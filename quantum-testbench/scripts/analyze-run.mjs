import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import {spawnSync} from 'node:child_process';
import {summarizeStage, stableStage, validWorkload} from './metrics.mjs';

const directory = path.resolve(process.argv[2]);
const read = name => JSON.parse(fs.readFileSync(path.join(directory, name), 'utf8').replace(/^\uFEFF/, ''));
const result = read('result.json'), manifest = read('manifest.json');
const telemetry = fs.readFileSync(path.join(directory, 'telemetry.jsonl'), 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse).map(m =>
  m.kind === 'tps' && m.fiveSeconds === undefined ? {...m, fiveSeconds: m.oneMinute, oneMinute: m.fiveMinutes, fiveMinutes: m.fifteenMinutes, fifteenMinutes: null, correctedLegacyLabels: true} : m);
for (const stage of result.stages) {
  if (stage.phase) Object.assign(stage, summarizeStage(telemetry, stage.phase, manifest.nativeMetrics));
}
const eventFile = path.join(directory, 'profile-events.json');
if (!fs.existsSync(eventFile)) {
  const fd = fs.openSync(eventFile, 'w');
  const command = spawnSync(path.join(path.dirname(manifest.java), process.platform === 'win32' ? 'jfr.exe' : 'jfr'),
    ['print', '--json', '--stack-depth', '12', '--events', 'jdk.ExecutionSample,jdk.GarbageCollection,jdk.GCPhasePause,jdk.SafepointBegin', path.join(directory, 'server.jfr')],
    {windowsHide: true, stdio: ['ignore', fd, 'pipe']});
  fs.closeSync(fd);
  if (command.status !== 0) throw Error(String(command.stderr));
}
// JFR repeats class-loader metadata per frame; stream and retain only fields used below.
const events = [];
let buffer = null, complete = false;
function retainEvent() {
  const event = JSON.parse(buffer + '}'), value = event.values;
  const frames = value.stackTrace?.frames?.map(f => ({method: {type: {name: f.method.type.name}, name: f.method.name}}));
  events.push({type: event.type, values: {startTime: value.startTime, duration: value.duration,
    name: value.name, cause: value.cause, longestPause: value.longestPause, sumOfPauses: value.sumOfPauses,
    safepointId: value.safepointId, sampledThread: {javaName: value.sampledThread?.javaName}, stackTrace: {frames}}});
}
for await (const line of readline.createInterface({input: fs.createReadStream(eventFile), crlfDelay: Infinity})) {
  if (line.includes('"events": [{')) buffer = '{';
  else if (line === '    }, {') { retainEvent(); buffer = '{'; }
  else if (line === '    }]') { retainEvent(); complete = true; break; }
  else if (buffer !== null) buffer += line + '\n';
}
if (!complete || !events.length) throw Error('Incomplete or empty JFR event export');
fs.writeFileSync(path.join(directory, 'profile-compact.json'), JSON.stringify(events));
const rank = values => {
  const counts = new Map();
  for (const value of values) counts.set(value, (counts.get(value) ?? 0) + 1);
  return [...counts].sort((a, b) => b[1] - a[1]).slice(0, 8);
};
const lines = ['# Local Quantum active load result', '', `Result: **${result.result}**. ${result.started} to ${result.ended}.`, '',
  `${manifest.hostCpu}; ${manifest.logicalProcessors} logical CPUs; ${(manifest.totalMemoryBytes / 2**30).toFixed(1)} GiB RAM; ${manifest.platform} ${manifest.release}.`,
  `Server SHA-256: \`${manifest.serverSha256}\`. Java: \`${manifest.java}\`. JVM: \`${manifest.jvmArguments.join(' ')}\`.`,
  `No added plugins. View ${manifest.viewDistance}, simulation ${manifest.simulationDistance}, seed ${manifest.worldSeed}. ${manifest.network}.`, '',
  'The table reports the worst **rolling 60-second** window sampled after active warmup. Early windows still overlap active warmup; join/setup are excluded. Percentiles are not pooled across windows. TPS is the sampled upstream 1-minute value.', '',
  '| Players/profile | Planned measured seconds | Minimum 1m TPS | Observed tick rate min | Worst mean MSPT | Worst p95 | Worst p99 | Maximum tick | Ticks/workload/clients |',
  '|---|---:|---:|---:|---:|---:|---:|---:|---|'];
for (const stage of result.stages) lines.push(`| ${stage.players}/${stage.profile ?? 'idle'} | ${stage.seconds} | ${stage.minimumTps ?? 'n/a'} | ${stage.minimumObservedTps?.toFixed(2) ?? 'n/a'} | ${stage.worstRollingAverage ?? 'n/a'} | ${stage.worstRollingP95 ?? 'n/a'} | ${stage.worstRollingP99 ?? 'n/a'} | ${stage.maximumTickMs ?? 'n/a'} | ${stableStage(stage)}/${validWorkload(stage)}/${stage.driver?.result ?? 'missing'} |`);
if (manifest.nativeMetrics) lines.push('', 'Native /tps and /mspt comparison: identical upstream collector on both servers, rounded to 0.1. No p95/p99 or observed tick-count gate is available in this mode.');
const profiles = [];
for (const stage of result.stages) {
  const observations = telemetry.filter(m => m.phase === stage.phase);
  const start = Date.parse(observations[0]?.timestamp), end = Date.parse(observations.at(-1)?.timestamp);
  const selected = events.filter(e => Date.parse(e.values.startTime) >= start && Date.parse(e.values.startTime) <= end);
  const samples = selected.filter(e => e.type === 'jdk.ExecutionSample');
  const main = samples.filter(e => e.values.sampledThread?.javaName === 'Server thread');
  const methods = main.map(e => e.values.stackTrace?.frames?.[0]?.method).filter(Boolean).map(m => m.type.name.replaceAll('/', '.') + '.' + m.name);
  const metrics = kind => observations.filter(m => m.kind === kind);
  const maximum = values => values.length ? Math.max(...values) : null;
  const profile = {players: stage.players, phase: stage.phase, samplingStart: new Date(start).toISOString(), samplingEnd: new Date(end).toISOString(),
    executionSamples: samples.length, mainThreadSamples: main.length,
    sampledThreads: rank(samples.map(e => e.values.sampledThread?.javaName ?? 'unknown')),
    mainThreadHotLeafMethods: rank(methods), peakLoadedChunks: maximum(metrics('chunks').map(m => m.loaded)),
    peakLoadedEntities: maximum(metrics('entities').map(m => m.loaded)), peakHeapBytes: maximum(metrics('heap').map(m => m.usedBytes)),
    maxHostBusyFraction: maximum(metrics('host').map(m => m.averageBusy)),
    garbageCollections: selected.filter(e => e.type === 'jdk.GarbageCollection').map(e => e.values),
    gcPauses: selected.filter(e => e.type === 'jdk.GCPhasePause').map(e => ({start: e.values.startTime, duration: e.values.duration}))};
  profile.safepointSynchronization = selected.filter(e => e.type === 'jdk.SafepointBegin').map(e => ({start: e.values.startTime, duration: e.values.duration}));
  profiles.push(profile);
  lines.push('', `## ${stage.players} ${stage.profile ?? 'idle'} clients`, '',
    `Peak sampled loaded chunks: ${profile.peakLoadedChunks ?? 'unavailable'}; entities: ${profile.peakLoadedEntities ?? 'unavailable'}; heap: ${profile.peakHeapBytes == null ? 'unavailable' : (profile.peakHeapBytes / 2**30).toFixed(2) + ' GiB'}.`, '',
    'Client counters (include active warmup; attempts and server confirmations are separate):', '', '```json', JSON.stringify(stage.driver?.actions ?? {}, null, 2), '```', '',
    `JFR: ${samples.length} execution samples in the observation interval; ${main.length} on the server thread. Sample counts indicate hotspots, not precise CPU time.`, '',
    '| Main-thread leaf method | Samples |', '|---|---:|', ...profile.mainThreadHotLeafMethods.map(([method, count]) => `| \`${method}\` | ${count} |`));
}
lines.push('', '## Limits', '',
  'Scripted arena survival uses fixed flat-floor movement, stationary combat targets, building, chest open/close and hotbar/offhand swaps. It does not simulate full inventories, terrain pathfinding, real PvP, farms or redstone. Exploration uses creative flight through normal generated terrain. Results apply only to these fixtures and this shared host.', '',
  'Errors: ' + JSON.stringify(result.fatalErrors), '', 'Raw manifest, console log, telemetry, bot logs and JFR remain in this directory.', '');
lines.push('Older Quantum logs mislabeled the 5-second TPS as 1 minute; this analysis corrects the known legacy layout. Original artifacts are retained unchanged. Stable additionally requires >=19.9 ticks/second observed from tick count/coverage and maximum tick <250 ms.', '');
fs.writeFileSync(path.join(directory, 'profile-summary.json'), JSON.stringify(profiles, null, 2) + '\n');
fs.writeFileSync(path.join(directory, 'ANALYSIS.md'), lines.join('\n'));
console.log(path.join(directory, 'ANALYSIS.md'));
