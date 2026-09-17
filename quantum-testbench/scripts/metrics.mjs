export function parseMetric(line) {
  const text = line.replace(/\u001b\[[0-9;]*m/g, '');
  const nativeTps = text.match(/TPS from last 5s, 1m, 5m, 15m: ([\d.]+), ([\d.]+), ([\d.]+), ([\d.]+)/);
  if (nativeTps) return {kind: 'native-tps', fiveSeconds: Number(nativeTps[1]), oneMinute: Number(nativeTps[2]), fiveMinutes: Number(nativeTps[3]), fifteenMinutes: Number(nativeTps[4])};
  const nativeMspt = text.match(/([\d.]+)\/([\d.]+)\/([\d.]+), ([\d.]+)\/([\d.]+)\/([\d.]+), ([\d.]+)\/([\d.]+)\/([\d.]+)/);
  if (nativeMspt) return {kind: 'native-mspt', windowSeconds: 60, average: Number(nativeMspt[7]), min: Number(nativeMspt[8]), max: Number(nativeMspt[9])};
  const tps = text.match(/TPS \(upstream\) 5s ([\d.]+) \| 1m ([\d.]+) \| 5m ([\d.]+) \| 15m ([\d.]+)/);
  if (tps) return { kind: 'tps', fiveSeconds: Number(tps[1]), oneMinute: Number(tps[2]), fiveMinutes: Number(tps[3]), fifteenMinutes: Number(tps[4]) };
  const legacy = text.match(/TPS \(upstream\) 1m ([\d.]+) \| 5m ([\d.]+) \| 15m ([\d.]+)/);
  // The initial Quantum 26.2 artifact mislabeled upstream's [5s, 1m, 5m, 15m] array.
  if (legacy) return {kind: 'tps', fiveSeconds: Number(legacy[1]), oneMinute: Number(legacy[2]), fiveMinutes: Number(legacy[3]), fifteenMinutes: null, correctedLegacyLabels: true};
  const mspt = text.match(/MSPT (\d+)s: avg ([\d.]+) \| p50 ([\d.]+) \| p75 ([\d.]+) \| p95 ([\d.]+) \| p99 ([\d.]+) \| max ([\d.]+) \| last ([\d.]+) \((\d+) samples, ([\d.]+)s coverage\)/);
  if (mspt) return { kind: 'mspt', windowSeconds: Number(mspt[1]), average: Number(mspt[2]), p50: Number(mspt[3]),
    p75: Number(mspt[4]), p95: Number(mspt[5]), p99: Number(mspt[6]), max: Number(mspt[7]), last: Number(mspt[8]), samples: Number(mspt[9]), coverageSeconds: Number(mspt[10]) };
  const chunks = text.match(/Loaded chunks: (\d+)/);
  if (chunks) return {kind: 'chunks', loaded: Number(chunks[1])};
  const queues = text.match(/Waiting (load|generation|send): (\d+)\s*\|\s*(Loading|Generating|Waiting ticking): (\d+)/);
  if (queues) return {kind: 'chunk-queue', queue: queues[1], waiting: Number(queues[2]), companion: queues[3], companionCount: Number(queues[4])};
  const position = text.match(/(QTest\d+) has the following entity data: \[([-\d.Ee+]+)d, ([-\d.Ee+]+)d, ([-\d.Ee+]+)d\]/);
  if (position) return {kind: 'server-position', player: position[1], x: Number(position[2]), y: Number(position[3]), z: Number(position[4])};
  const entities = text.match(/Loaded entities: (\d+)/);
  if (entities) return {kind: 'entities', loaded: Number(entities[1])};
  const heap = text.match(/Heap:.*used = (\d+).*committed = (\d+).*max = (\d+)/);
  if (heap) return {kind: 'heap', usedBytes: Number(heap[1]), committedBytes: Number(heap[2]), maxBytes: Number(heap[3])};
  const gc = text.match(/(G1 [^:]+): collections=(\d+) cumulative time=(\d+) ms/);
  if (gc) return {kind: 'gc', collector: gc[1], collections: Number(gc[2]), cumulativeMs: Number(gc[3])};
  return null;
}

export function summarizeStage(measurements, phase, nativeMetrics = false) {
  if (nativeMetrics) {
    const ticks = measurements.filter(m => m.phase === phase && m.kind === 'native-mspt');
    const tps = measurements.filter(m => m.phase === phase && m.kind === 'native-tps');
    return {nativeMetrics: true, msptSamples: ticks.length, tpsSamples: tps.length,
      minimumTps: tps.length ? Math.min(...tps.map(m => m.oneMinute)) : null,
      worstRollingAverage: ticks.length ? Math.max(...ticks.map(m => m.average)) : null,
      maximumTickMs: ticks.length ? Math.max(...ticks.map(m => m.max)) : null,
      lastRolling60Seconds: ticks.at(-1) ?? null};
  }
  const ticks = measurements.filter(m => m.phase === phase && m.kind === 'mspt' && m.coverageSeconds >= 59);
  const tps = measurements.filter(m => m.phase === phase && m.kind === 'tps');
  return {msptSamples: ticks.length, tpsSamples: tps.length,
    minimumTps: tps.length ? Math.min(...tps.map(m => m.oneMinute)) : null,
    minimumObservedTps: ticks.length ? Math.min(...ticks.map(m => m.samples / m.coverageSeconds)) : null,
    worstRollingAverage: ticks.length ? Math.max(...ticks.map(m => m.average)) : null,
    worstRollingP95: ticks.length ? Math.max(...ticks.map(m => m.p95)) : null,
    worstRollingP99: ticks.length ? Math.max(...ticks.map(m => m.p99)) : null,
    maximumTickMs: ticks.length ? Math.max(...ticks.map(m => m.max)) : null,
    lastRolling60Seconds: ticks.at(-1) ?? null};
}

export function stableStage(stage) {
  // Native upstream commands lack percentiles and tick counts; this is a narrower comparison gate.
  if (stage.nativeMetrics) return stage.msptSamples >= 2 && stage.tpsSamples >= 2
    && stage.minimumTps >= 19.9 && stage.worstRollingAverage < 40 && stage.maximumTickMs < 250;
  return stage.msptSamples >= 2 && stage.tpsSamples >= 2 && stage.minimumTps >= 19.9
    && stage.minimumObservedTps >= 19.9 && stage.maximumTickMs < 250
    && stage.worstRollingAverage < 40 && stage.worstRollingP95 < 50 && stage.worstRollingP99 < 50;
}

export function validWorkload(stage) {
  const actions = stage.driver?.actions ?? {};
  if (!stage.profile) return true;
  if (stage.profile === 'explore') return actions.movementPackets > stage.players * stage.seconds * 15
    && actions.chunksReceivedDuringActions > stage.players && actions.explorationSteps > stage.players * stage.seconds * 15
    && (actions.chunkWaitTicks ?? 0) / (actions.explorationSteps + (actions.chunkWaitTicks ?? 0)) < .05
    && (actions.positionCorrections ?? 0) / actions.movementPackets < .005;
  return actions.movementPackets > stage.players * .5 * stage.seconds * 15
    && ['jumps', 'confirmedBlockPlaced', 'confirmedBlockBroken', 'confirmedContainerOpen', 'confirmedTargetDamage', 'chatMessages', 'commands'].every(key => actions[key] > 0)
    && (actions.positionCorrections ?? 0) / actions.movementPackets < .005;
}

export function classify({serverReady, stoppedCleanly, fatalErrors, stages, expectedStages = 2, performance = false}) {
  if (!serverReady || !stoppedCleanly || fatalErrors.length) return 'FAIL';
  if (stages.length !== expectedStages || stages.some(stage => stage.exitCode !== 0 || stage.driver?.result !== 'PASS')) return 'FAIL';
  if (stages.some(stage => stage.msptSamples < 2 || !stage.driver.allSpawned || stage.driver.minimumReady !== stage.players)) return 'INVALID_TEST';
  if (performance && stages.some(stage => !validWorkload(stage))) return 'INVALID_TEST';
  if (performance && stages.some(stage => !stableStage(stage))) return 'FAIL';
  return 'PASS';
}
