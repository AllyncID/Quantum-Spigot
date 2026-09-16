import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import net from 'node:net';
import readline from 'node:readline';
import {spawn} from 'node:child_process';
import {createHash, randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {parseMetric, classify, summarizeStage, stableStage, validWorkload} from './metrics.mjs';

const repository = fileURLToPath(new URL('../../', import.meta.url));
const bench = path.join(repository, 'quantum-testbench');
const runsRoot = path.join(os.homedir(), '.cache', 'quantumspigot', 'runs');
const sha = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const json = (file, value) => fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const [mode, argument, scenarioFile] = process.argv.slice(2);

if (mode === 'prepare') {
  if (!argument) throw Error('Usage: node quantum-testbench/scripts/local-smoke.mjs prepare <JDK25-directory>');
  const java = path.resolve(argument, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  const installedLibraries = path.join(bench, 'build/install/quantum-testbench/lib');
  const scenario = scenarioFile ? JSON.parse(fs.readFileSync(scenarioFile, 'utf8')) : null;
  if (scenario?.server && !['quantum', 'purpur'].includes(scenario.server)) throw Error('Unknown server');
  if (scenario?.allocator && !['adaptive', 'pooled'].includes(scenario.allocator)) throw Error('Unknown allocator');
  const viewDistance = scenario?.viewDistance ?? 6, simulationDistance = scenario?.simulationDistance ?? 4;
  if (![viewDistance, simulationDistance].every(n => Number.isInteger(n) && n >= 2 && n <= 16)) throw Error('Distances must be 2..16');
  const jar = path.join(repository, scenario?.server === 'purpur' ? 'artifacts/purpur-26.2-baseline.jar' : 'artifacts/quantumspigot-26.2-build.DEV.jar');
  if (scenario && (!Array.isArray(scenario.stages) || !scenario.stages.length || scenario.stages.some(s =>
    !Number.isInteger(s.players) || s.players < 1 || s.players > 300 || !Number.isInteger(s.seconds)
    || s.seconds < 60 || s.seconds > 1800 || !['mixed', 'explore'].includes(s.profile)))) throw Error('Invalid local scenario');
  if (!fs.existsSync(java) || !fs.existsSync(jar) || !fs.existsSync(installedLibraries)) throw Error('Build the server and bot driver and supply JDK 25 first');
  const directory = path.join(runsRoot, 'smoke-' + new Date().toISOString().replace(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8));
  fs.mkdirSync(path.join(directory, 'cache'), {recursive: true});
  fs.mkdirSync(path.join(directory, 'plugins'));
  const libraries = path.join(directory, 'driver');
  fs.cpSync(installedLibraries, libraries, {recursive: true});
  fs.cpSync(path.join(bench, 'scripts'), path.join(directory, 'sources/scripts'), {recursive: true});
  fs.copyFileSync(path.join(bench, 'src/main/java/dev/quantumspigot/testbench/BotDriver.java'), path.join(directory, 'sources/BotDriver.java'));
  fs.copyFileSync(jar, path.join(directory, 'server.jar'));
  if (scenario?.sparkEnabled === false) {
    fs.mkdirSync(path.join(directory, 'config'));
    fs.writeFileSync(path.join(directory, 'config/paper-global.yml'), '_version: 31\nspark:\n  enabled: false\n  enable-immediately: false\n');
  }
  const vanilla = path.join(repository, 'run/cli-smoke/cache/mojang_26.2.jar');
  if (fs.existsSync(vanilla) && sha(vanilla) === 'cdacdfb25898de5e4b4b0e5ddcc2722f77067e46605709c2d886c000ebb63ec5') {
    fs.copyFileSync(vanilla, path.join(directory, 'cache/mojang_26.2.jar'));
  }
  fs.writeFileSync(path.join(directory, 'eula.txt'), '# Operator must explicitly accept https://www.minecraft.net/en-us/eula\neula=false\n');
  fs.writeFileSync(path.join(directory, 'server.properties'), [
    'server-ip=127.0.0.1', 'server-port=25570', 'online-mode=false', 'enforce-secure-profile=false',
    'enable-rcon=false', 'enable-query=false', 'max-players=300', `view-distance=${viewDistance}`, `simulation-distance=${simulationDistance}`,
    'pause-when-empty-seconds=-1', 'level-seed=728194', 'level-name=world', 'gamemode=survival',
    'difficulty=normal', 'motd=Quantum local testbench', 'player-idle-timeout=0', 'spawn-protection=0', ''
  ].join('\n'));
  const manifest = {schema: 1, status: 'PREPARED_NOT_RUN', created: new Date().toISOString(), directory, java,
    serverSha256: sha(jar), minecraft: '26.2', protocol: 776, host: '127.0.0.1', port: 25570,
    heap: {initial: '1G', maximum: '6G'}, plugins: [], worldSeed: 728194, viewDistance, simulationDistance,
    network: 'loopback; server and bots share one host; offline-mode authentication is not measured',
    stages: scenario?.stages ?? [{players: 1, seconds: 60}, {players: 20, seconds: 120}], idleSeconds: 60,
    performance: !!scenario, warmupSeconds: scenario ? 60 : 0,
    server: scenario?.server ?? 'quantum', nativeMetrics: !!scenario?.nativeMetrics,
    allocator: scenario?.allocator ?? 'adaptive',
    sparkEnabled: scenario?.sparkEnabled !== false,
    locatorBar: scenario?.locatorBar !== false,
    thresholds: {minimumTps: 19.9, minimumObservedTps: 19.9, maxAverageMspt: 40, maxP95Mspt: 50, maxP99Mspt: 50, maxTickMs: 250},
    driver: scenario ? 'mixed flat-arena survival / creative exploration, native protocol; see profile counters' : 'idle-look smoke only', driverLibraries: libraries,
    driverHashes: fs.readdirSync(libraries).filter(f => f.endsWith('.jar')).sort().map(f => ({file: f, sha256: sha(path.join(libraries, f))})),
    hostCpu: os.cpus()[0]?.model, logicalProcessors: os.cpus().length, totalMemoryBytes: os.totalmem(),
    platform: os.platform(), release: os.release(), propertiesSha256: sha(path.join(directory, 'server.properties'))};
  json(path.join(directory, 'manifest.json'), manifest);
  console.log(JSON.stringify({prepared: directory, freeGiB: os.freemem() / 2**30, eulaAccepted: false}, null, 2));
} else if (mode === 'run') {
  await run(path.resolve(argument ?? ''));
} else {
  throw Error('Usage: local-smoke.mjs prepare <JDK25-directory> | run <prepared-directory>');
}

async function run(directory) {
  if (!directory.startsWith(path.resolve(runsRoot) + path.sep)) throw Error('Only a prepared test directory inside runsRoot is allowed');
  const config = JSON.parse(fs.readFileSync(path.join(directory, 'manifest.json'), 'utf8'));
  if (config.status !== 'PREPARED_NOT_RUN' || config.directory !== directory || fs.existsSync(path.join(directory, 'result.json'))) throw Error('Prepare a fresh run');
  if (config.host !== '127.0.0.1' || config.plugins.length || config.stages.some(s => s.players < 1 || s.players > 300)) throw Error('Local scope: loopback, no plugins, at most 300 bots');
  if (!/^eula\s*=\s*true\s*$/mi.test(fs.readFileSync(path.join(directory, 'eula.txt'), 'utf8'))) throw Error('Minecraft EULA has not been accepted by the operator; no server was started');
  if (os.freemem() < 12 * 2**30) throw Error('Initial local smoke needs at least 12 GiB available before startup');
  if (sha(path.join(directory, 'server.jar')) !== config.serverSha256 || sha(path.join(directory, 'server.properties')) !== config.propertiesSha256) throw Error('Prepared server/config hash changed');
  if (fs.readdirSync(path.join(directory, 'plugins')).length) throw Error('The no-plugin fixture must remain empty');
  for (const library of config.driverHashes) if (sha(path.join(config.driverLibraries, library.file)) !== library.sha256) throw Error('Bot driver changed; prepare a new run');
  await new Promise((resolve, reject) => {
    const probe = net.createServer(); probe.once('error', reject);
    probe.listen(config.port, config.host, () => probe.close(resolve));
  });
  config.status = 'RUNNING'; config.started = new Date().toISOString();
  json(path.join(directory, 'manifest.json'), config);
  const log = fs.createWriteStream(path.join(directory, 'console.log'));
  const telemetry = fs.createWriteStream(path.join(directory, 'telemetry.jsonl'));
  const fatalErrors = [], stages = [], measurements = [];
  let phase = 'startup', serverReady = false, serverExit, stopping = false, bot, monitor, stageStarted = Date.now(), badSamples = 0;
  let activeStarted, activePhase;
  let previousCpu = os.cpus(), latestTps, latestMspt;
  const args = ['-Xms1G', '-Xmx6G', '-XX:StartFlightRecording=filename=server.jfr,settings=profile,dumponexit=true,maxsize=256m',
    `-Dio.netty.allocator.type=${config.allocator ?? 'adaptive'}`,
    '-Xlog:safepoint=info:file=safepoints.log:time,uptime,level',
    '-Dterminal.ansi=false', '-jar', 'server.jar', '--nogui', '--nojline'];
  config.jvmArguments = args;
  json(path.join(directory, 'manifest.json'), config);
  const server = spawn(config.java, args, {cwd: directory, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe']});
  const serverDone = new Promise(resolve => server.once('exit', (code, signal) => { serverExit = {code, signal}; resolve(serverExit); }));
  server.on('error', error => fatalErrors.push(error.message));
  server.stdin.on('error', error => { if (!stopping) fatalErrors.push(error.message); });
  const command = text => { if (!serverExit && !server.stdin.destroyed) server.stdin.write(text + '\n'); };
  for (const stream of [server.stdout, server.stderr]) readline.createInterface({input: stream}).on('line', line => {
    log.write(line + '\n');
    if (/Done \([^)]+\)!/.test(line)) { serverReady = true; console.log('SERVER_READY'); }
    if (/Exception in server tick loop|Encountered an unexpected exception|OutOf(?:Direct)?MemoryError|has not responded for|A single server tick took/.test(line)) fatalErrors.push(line);
    const metric = parseMetric(line);
    if (metric) {
      const record = {timestamp: new Date().toISOString(), phase, ...metric};
      measurements.push(record); telemetry.write(JSON.stringify(record) + '\n');
      if (metric.kind === (config.nativeMetrics ? 'native-tps' : 'tps')) latestTps = metric;
      if (metric.kind === (config.nativeMetrics ? 'native-mspt' : 'mspt')) latestMspt = metric;
    }
  });
  const health = () => {
    if (activeStarted && Date.now() - activeStarted >= config.warmupSeconds * 1000) phase = activePhase;
    const cpus = os.cpus();
    const perCoreBusy = cpus.map((cpu, i) => {
      const previous = previousCpu[i]?.times ?? cpu.times;
      const total = Object.keys(cpu.times).reduce((sum, key) => sum + cpu.times[key] - previous[key], 0);
      return total > 0 ? 1 - (cpu.times.idle - previous.idle) / total : 0;
    });
    previousCpu = cpus;
    const record = {timestamp: new Date().toISOString(), phase, kind: 'host', freeBytes: os.freemem(), perCoreBusy,
      averageBusy: perCoreBusy.reduce((a,b) => a+b, 0) / perCoreBusy.length};
    telemetry.write(JSON.stringify(record) + '\n');
    if (record.freeBytes < 3 * 2**30) fatalErrors.push('Host available memory fell below 3 GiB');
    if (Date.now() - stageStarted > 60_000 && (latestTps?.oneMinute < 18 || latestMspt?.p99 > 100)) badSamples++;
    else badSamples = 0;
    if (badSamples >= 4) fatalErrors.push('Sustained TPS below 18 or rolling p99 above 100 ms');
    command(config.nativeMetrics ? 'tps\nmspt\nlist' : 'quantum tps\nquantum chunks\nquantum entities\nquantum gc\nlist');
  };
  const interrupted = () => fatalErrors.push('Operator interrupted test');
  process.on('SIGINT', interrupted); process.on('SIGTERM', interrupted);
  const healthyWait = async ms => {
    const until = Date.now() + ms;
    while (Date.now() < until) {
      if (fatalErrors.length || serverExit) throw Error(fatalErrors.at(-1) ?? 'Server exited unexpectedly');
      await delay(Math.min(500, until - Date.now()));
    }
  };
  try {
    const readyDeadline = Date.now() + 240_000;
    while (!serverReady) {
      if (Date.now() > readyDeadline) throw Error('Startup exceeded 240 seconds');
      await healthyWait(500);
    }
    command('version\nplugins');
    if (config.locatorBar === false) command('gamerule minecraft:locator_bar false');
    if (config.performance) {
      phase = 'arena-setup';
      command('forceload add -16 -16 176 144');
      await healthyWait(15_000);
      for (let x = -4; x <= 164; x += 20) {
        command(`fill ${x} 159 -4 ${Math.min(164, x + 19)} 159 124 minecraft:stone`);
        command(`fill ${x} 160 -4 ${Math.min(164, x + 19)} 166 124 minecraft:air`);
        await healthyWait(100);
      }
      command('setworldspawn 4 160 4\ntime set day');
      await healthyWait(5000);
    }
    phase = 'idle'; stageStarted = Date.now();
    monitor = setInterval(health, 15_000); health();
    console.log('BASELINE_IDLE_60_SECONDS');
    await healthyWait(config.idleSeconds * 1000);
    for (const stage of config.stages) {
      activePhase = `bots-${stage.players}-${stage.profile ?? 'idle'}-${stages.length + 1}`;
      phase = activePhase + '-join'; activeStarted = undefined; stageStarted = Date.now(); badSamples = 0;
      latestTps = latestMspt = undefined;
      const file = path.join(directory, activePhase + '.json');
      console.log('START_STAGE ' + phase);
      const botLog = fs.createWriteStream(path.join(directory, activePhase + '.jsonl'));
      bot = spawn(config.java, ['-Xms128M', '-Xmx2G', '-Dio.netty.eventLoopThreads=4', '-cp', path.join(config.driverLibraries, '*'),
        'dev.quantumspigot.testbench.BotDriver', String(config.port), String(stage.players), String(stage.seconds + (config.warmupSeconds ?? 0)), file, stage.profile ?? 'idle', String(config.warmupSeconds ?? 0)],
        {cwd: directory, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']});
      let botExit;
      bot.once('exit', (code, signal) => { botExit = {code, signal}; });
      bot.once('error', error => { fatalErrors.push(error.message); });
      for (const stream of [bot.stdout, bot.stderr]) readline.createInterface({input: stream}).on('line', line => {
        botLog.write(line + '\n');
        try {
          const event = JSON.parse(line);
          if (event.event === 'all_spawned' || event.event === 'actions_started' || event.result || (event.event === 'disconnected' && !event.expected)) console.log(line);
          if (event.event === 'actions_started') { activeStarted = stageStarted = Date.now(); phase = activePhase + '-warmup'; }
          if (event.event === 'all_spawned' && config.performance) setupPlayers(stage, file).catch(error => fatalErrors.push(error.message));
          if (event.event === 'all_spawned' && !config.performance) { activeStarted = Date.now(); phase = activePhase; }
        } catch {}
      });
      const stageDeadline = Date.now() + (stage.seconds + (config.warmupSeconds ?? 0) + stage.players * .2 + 240) * 1000;
      while (!botExit) {
        if (Date.now() > stageDeadline) throw Error('Bot stage timed out');
        await healthyWait(500);
      }
      botLog.end(); bot = undefined;
      const driver = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : null;
      const observed = summarizeStage(measurements, activePhase, config.nativeMetrics);
      const stageResult = {...stage, phase: activePhase, exitCode: botExit.code, driver, ...observed};
      stages.push(stageResult);
      console.log('STAGE_RESULT ' + JSON.stringify(stageResult));
      if (botExit.code !== 0 || driver?.result !== 'PASS') throw Error('Bot stage failed; see captured log');
      if (config.performance && !validWorkload(stageResult)) throw Error('Workload validation failed; some expected server-confirmed actions are absent or bot movement is invalid');
      if (config.performance && !stableStage(stageResult)) throw Error('Performance gate failed; retain JFR and stop ramp');
      activeStarted = undefined;
      phase = 'cooldown'; await healthyWait(15_000);
    }
    command('save-all flush'); await healthyWait(5_000);
  } catch (error) {
    fatalErrors.push(error.message);
    console.error('SMOKE_STOPPED: ' + error.message);
  } finally {
    clearInterval(monitor); stopping = true;
    if (bot && bot.exitCode === null) bot.kill();
    command('stop');
    await Promise.race([serverDone, delay(60_000)]);
    if (!serverExit) { fatalErrors.push('Graceful server shutdown timed out'); server.kill(); await Promise.race([serverDone, delay(5000)]); }
    log.end(); telemetry.end();
    process.removeListener('SIGINT', interrupted); process.removeListener('SIGTERM', interrupted);
    const result = {serverReady, stoppedCleanly: serverExit?.code === 0, fatalErrors, stages, expectedStages: config.stages.length, performance: !!config.performance,
      scope: `${config.driver}. No added plugins, same-host loopback. Rolling 60-second MSPT samples are not pooled full-run percentiles.`,
      serverSha256: config.serverSha256, started: config.started, ended: new Date().toISOString(), directory};
    result.result = classify(result);
    json(path.join(directory, 'result.json'), result);
    config.status = result.result; config.ended = result.ended; json(path.join(directory, 'manifest.json'), config);
    fs.writeFileSync(path.join(directory, 'REPORT.md'), '# Quantum local smoke\n\nResult: **' + result.result + '**\n\n'
      + result.scope + '\n\n```json\n' + JSON.stringify(result, null, 2) + '\n```\n');
    console.log('RESULT ' + result.result + ' ' + path.join(directory, 'result.json'));
    if (result.result !== 'PASS') process.exitCode = 1;
  }

  async function setupPlayers(stage, file) {
    command('kill @e[type=minecraft:zombie]\nclear @a');
    for (let i = 0; i < stage.players; i++) {
      const name = 'QTest' + String(i + 1).padStart(3, '0'), x = i % 20 * 8 + 4, z = Math.floor(i / 20) * 8 + 4;
      const role = i % 10;
      command(`gamemode ${stage.profile === 'explore' ? 'creative' : 'survival'} ${name}`);
      command(`tp ${name} ${x + .5} ${stage.profile === 'explore' ? 240 : 160} ${z + .5}`);
      command(`effect give ${name} minecraft:saturation infinite 0 true`);
      if (stage.profile === 'mixed') {
        command(`setblock ${x + 2} 160 ${z} ${role === 9 ? 'minecraft:chest' : 'minecraft:air'}`);
        if (role === 6 || role === 7) {
          command(`item replace entity ${name} hotbar.0 with minecraft:dirt 64\nitem replace entity ${name} hotbar.1 with minecraft:diamond_shovel`);
        }
        if (role === 8) command(`summon minecraft:zombie ${x + 2.5} 160 ${z + .5} {NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:1000f,attributes:[{id:"minecraft:max_health",base:1000.0},{id:"minecraft:knockback_resistance",base:1.0}],ArmorItems:[{},{},{},{id:"minecraft:iron_helmet",count:1}]}`);
      }
      await healthyWait(10);
    }
    command('effect give @e[type=minecraft:zombie] minecraft:fire_resistance infinite 0 true');
    await healthyWait(5000);
    fs.writeFileSync(file + '.go', new Date().toISOString());
  }
}
