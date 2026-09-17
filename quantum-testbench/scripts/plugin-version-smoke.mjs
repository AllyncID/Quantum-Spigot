// Run only against copies with the operator's accepted EULA. Never touches the live server.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';

const [jarArg, java, lobbyArg] = process.argv.slice(2);
assert(jarArg && java && lobbyArg, 'Usage: plugin-version-smoke.mjs <jar> <java> <lobby-directory>');
const jar = path.resolve(jarArg), lobby = path.resolve(lobbyArg);
const eula = fs.readFileSync(path.join(lobby, 'eula.txt'), 'utf8');
assert(/^eula=true\s*$/m.test(eula), 'Operator-accepted EULA required');
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'quantum-plugin-version-'));
fs.mkdirSync(path.join(directory, 'plugins'));
for (const name of ['Images.jar', 'ItemEdit.jar']) {
  const source = fs.readdirSync(path.join(lobby, 'plugins')).find(f => f.toLowerCase() === name.toLowerCase()
    || name === 'ItemEdit.jar' && /^ItemEdit.*\.jar$/i.test(f));
  assert(source, `${name} missing`);
  fs.copyFileSync(path.join(lobby, 'plugins', source), path.join(directory, 'plugins', source));
}
fs.writeFileSync(path.join(directory, 'eula.txt'), eula);
fs.writeFileSync(path.join(directory, 'server.properties'), 'server-ip=127.0.0.1\nserver-port=25579\nonline-mode=false\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\nlevel-seed=728194\n');
fs.mkdirSync(path.join(directory, 'config'));
fs.writeFileSync(path.join(directory, 'config/paper-global.yml'), '_version: 31\nspark:\n  enabled: false\n  enable-immediately: false\n');
if (fs.existsSync(path.join(lobby, 'cache'))) fs.cpSync(path.join(lobby, 'cache'), path.join(directory, 'cache'), {recursive: true});
console.log(`Evidence: ${directory}`);
const log = fs.createWriteStream(path.join(directory, 'console.log'));
const child = spawn(java, ['-Xms512M', '-Xmx2G', '-Dio.netty.allocator.type=pooled', '-Dterminal.jline=false', '-jar', jar, '--nogui'], {cwd: directory, windowsHide: true});
let output = '', exitCode;
const done = new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', code => { exitCode = code; resolve(code); }); });
for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => { output += chunk; log.write(chunk); });
try {
  const deadline = Date.now() + 150000;
  while (!/Done \([\d.]+s\)!/.test(output)) {
    assert(exitCode === undefined && Date.now() < deadline, 'Startup failed or timed out');
    await delay(250);
  }
  child.stdin.write('version\nplugins\n');
  await delay(2000);
  assert.match(output, /Implementing API version 26\.2\.build\.0-local/);
  assert.match(output, /\[Images\] Enabling Images/);
  assert.match(output, /\[ItemEdit\] Enabling ItemEdit/);
  assert.doesNotMatch(output, /unknown minor version|Invalid Bukkit version format|Could not load plugin|Error occurred while enabling/);
  console.log('PASS: Images and ItemEdit enabled; numeric local API version accepted');
} finally {
  if (exitCode === undefined) child.stdin.write('stop\n');
  await Promise.race([done, delay(60000)]);
  if (exitCode === undefined) { child.kill(); throw Error('Shutdown timed out'); }
  log.end();
  assert.equal(exitCode, 0);
}
