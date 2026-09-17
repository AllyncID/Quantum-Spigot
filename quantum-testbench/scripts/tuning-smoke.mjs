// Isolated startup/restart test. Usage: node tuning-smoke.mjs <paperclip.jar> <java.exe> <accepted-eula.txt>
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';

const [jarArg, javaArg, eulaArg] = process.argv.slice(2);
assert(jarArg && javaArg && eulaArg, 'Expected jar, Java and an operator-accepted EULA file');
const jar = path.resolve(jarArg), java = path.resolve(javaArg);
const eula = fs.readFileSync(path.resolve(eulaArg), 'utf8');
assert(/^eula=true\s*$/m.test(eula), 'Explicitly accepted EULA file required');
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'quantum-tuning-'));
fs.mkdirSync(path.join(directory, 'config/quantum'), {recursive: true});
// Reuse the accepted server's bootstrap cache; Paperclip still checks the expected hash.
const cachedVanilla = path.join(path.dirname(path.resolve(eulaArg)), 'cache/mojang_26.2.jar');
if (fs.existsSync(cachedVanilla)) {
  fs.mkdirSync(path.join(directory, 'cache'));
  fs.copyFileSync(cachedVanilla, path.join(directory, 'cache/mojang_26.2.jar'));
}
fs.writeFileSync(path.join(directory, 'eula.txt'), eula);
fs.writeFileSync(path.join(directory, 'server.properties'), 'server-ip=127.0.0.1\nserver-port=25579\nonline-mode=false\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\ndifficulty=peaceful\nlevel-seed=728194\n');
fs.writeFileSync(path.join(directory, 'config/paper-global.yml'), '_version: 31\nspark:\n  enabled: false\n  enable-immediately: false\n');
fs.writeFileSync(path.join(directory, 'config/quantum/quantum-performance.yml'), `enabled: true
profiling:
  disable-bundled-spark: true
chunks:
  generate-per-second: 17.0
  load-per-second: 65.0
  send-per-second: 45.0
  concurrent-generates: 1
  concurrent-loads: 3
world-defaults:
  hopper:
    transfer-ticks: 12
    check-ticks: 3
    amount: 2
    cooldown-when-full: false
    disable-move-event: true
    ignore-occluding-blocks: true
  redstone:
    implementation: ALTERNATE_CURRENT
  armor-stands:
    gravity: false
    collision-lookups: false
    water-movement: false
  anti-xray:
    enabled: true
    engine-mode: 2
    max-block-height: 80
    update-radius: 1
    lava-obscures: true
    use-permission: true
  blocks:
    max-scheduled-block-ticks: 12000
    max-scheduled-fluid-ticks: 16000
  saving:
    max-chunks-per-tick: 7
worlds:
  minecraft:the_nether:
    hopper:
      amount: 4
    armor-stands:
      tick: false
      movement: false
    anti-xray:
      enabled: false
    blocks:
      block-entity-ticking: false
`);
console.log(`Evidence: ${directory}`);

async function run(safeMode) {
  const name = safeMode ? 'safe' : 'tuned';
  const log = fs.createWriteStream(path.join(directory, `${name}.log`));
  const child = spawn(java, ['-Xms512M', '-Xmx2G', '-Dio.netty.allocator.type=pooled', '-Dterminal.jline=false',
    '-jar', jar, '--nogui', ...(safeMode ? ['--quantum-safe-mode'] : [])], {cwd: directory, windowsHide: true});
  let output = '', exited = false;
  const exit = new Promise((resolve, reject) => { child.once('error', reject); child.once('exit', code => { exited = true; resolve(code); }); });
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => { output += chunk; log.write(chunk); });
  const send = command => child.stdin.write(command + '\n');
  const waitFor = async (match, timeout = 90000) => {
    const end = Date.now() + timeout;
    while (!match.test(output)) {
      assert(!exited, `${name}: server exited before ${match}`);
      assert(Date.now() < end, `${name}: timed out waiting for ${match}`);
      await delay(200);
    }
  };
  try {
    await waitFor(/Done \([\d.]+s\)!/);
    console.log(`${name}: startup ready`);
    for (const command of ['help', 'version', 'tps', 'mspt', 'mspt histogram', 'health', 'threads', 'chunks', 'entities', 'plugins', 'profile', 'gc', 'config']) send('quantum ' + command);
    await waitFor(/Effective startup values/);
    const plainOutput = output.replace(/\u001b\[[0-9;]*m/g, '');
    const worlds = plainOutput.slice(plainOutput.lastIndexOf('[Quantum] CONFIG'));
    if (!safeMode) {
      assert.match(worlds, /generate=17.0, load=65.0, send=45.0/);
      assert.match(worlds, /generate=1, load=3/);
      assert.match(worlds, /Hopper transfer\/check\/amount: 12\/3\/2/);
      assert.match(worlds, /Hopper transfer\/check\/amount: 12\/3\/4/);
      assert.match(worlds, /Hopper cooldown \/ skip event \/ ignore blocks: false\/true\/true/);
      assert.match(worlds, /Redstone: ALTERNATE_CURRENT/);
      assert.match(worlds, /Armor stand tick\/collision\/gravity: true\/false\/false/);
      assert.match(worlds, /Armor stand tick\/collision\/gravity: false\/false\/false/);
      assert.match(worlds, /Armor stand movement\/water: false\/false/);
      assert.match(worlds, /Anti-Xray enabled\/mode\/height\/radius: true\/2\/80\/1/);
      assert.match(worlds, /Anti-Xray enabled\/mode\/height\/radius: false\/2\/80\/1/);
      assert.match(worlds, /Anti-Xray lava \/ permission: true\/true/);
      assert.match(worlds, /Block entities: false/);
      assert.match(worlds, /Block\/fluid tick budget: 12000\/16000/);
      assert.match(worlds, /Autosave chunks\/tick: 7/);
      send('forceload add 0 0');
      // Decimal coordinates avoid the command parser's +0.5 block-center adjustment.
      send('summon minecraft:armor_stand 0.0 200.0 0.0 {Tags:["quantum_gravity"],NoGravity:0b}');
      send('summon minecraft:armor_stand 2.0 200.0 0.0 {Tags:["quantum_nogravity"],NoGravity:1b}');
      await waitFor(/Summoned new Armor Stand/);
      await delay(2500);
      send('data get entity @e[tag=quantum_gravity,limit=1]');
      send('data get entity @e[tag=quantum_nogravity,limit=1]');
      send('execute as @e[tag=quantum_gravity,limit=1] if data entity @s {Pos:[0.0d,200.0d,0.0d]} unless data entity @s {NoGravity:1b} run say QUANTUM_GRAVITY_OK');
      await waitFor(/QUANTUM_GRAVITY_OK/, 10000);
    } else {
      assert.match(worlds, /Performance overrides: disabled/);
      assert.match(worlds, /Hopper transfer\/check\/amount: 8\/1\/1/);
      assert.doesNotMatch(worlds, /12\/3\/|ALTERNATE_CURRENT/);
      assert.match(worlds, /Armor stand tick\/collision\/gravity: true\/true\/true/);
      assert.match(worlds, /Anti-Xray enabled\/mode\/height\/radius: false\/1\/64\/2/);
      assert.match(worlds, /Block entities: true/);
      assert.match(worlds, /Block\/fluid tick budget: 65536\/65536/);
      await delay(2500);
      send('execute as @e[tag=quantum_gravity,limit=1] unless data entity @s {Pos:[0.0d,200.0d,0.0d]} unless data entity @s {NoGravity:1b} run say QUANTUM_FALL_OK');
      await waitFor(/QUANTUM_FALL_OK/);
    }
    send('execute as @e[tag=quantum_nogravity,limit=1] if data entity @s {Pos:[2.0d,200.0d,0.0d],NoGravity:1b} run say QUANTUM_NBT_OK');
    await waitFor(/QUANTUM_NBT_OK/);
    assert.doesNotMatch(output, /Exception executing command|Unable to load Quantum|OutOfMemoryError|Encountered an unexpected exception/);
    console.log(`${name}: commands, world overrides and armor stand physics PASS`);
  } finally {
    if (!exited) send('stop');
    let timer;
    try {
      const code = await Promise.race([exit, new Promise((_, reject) => { timer = setTimeout(() => reject(Error('Shutdown timeout')), 60000); })]);
      assert.equal(code, 0, `${name}: clean exit`);
      assert.match(output, /All dimensions are saved/);
    } catch (error) {
      child.kill(); // Only this isolated test process; retain its world and logs for diagnosis.
      throw error;
    } finally { clearTimeout(timer); log.end(); }
  }
}
await run(false);
await run(true);
console.log('PASS tuning startup, effective per-world values, command dispatch, gravity/NBT and safe-mode restart');
