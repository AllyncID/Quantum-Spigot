import {test} from 'node:test';
import assert from 'node:assert/strict';
import {classify, parseMetric, summarizeStage, stableStage, validWorkload} from './metrics.mjs';

test('real diagnostic lines preserve the rolling window and coverage', () => {
  const sample = parseMetric('[13:00:00 INFO]: MSPT 60s: avg 2.10 | p50 1.20 | p75 3.00 | p95 4.00 | p99 7.00 | max 10.00 | last 2.00 (1200 samples, 60.0s coverage)');
  assert.equal(sample.windowSeconds, 60);
  assert.equal(sample.samples, 1200);
  assert.equal(sample.p99, 7);
  assert.equal(parseMetric('MSPT 60s: no samples yet'), null);
  assert.equal(parseMetric('ERROR is a word in plugin help'), null);
});

test('performance gates exclude warmup and never pass missing or overloaded telemetry', () => {
  const samples = ['warmup', 'load', 'load'].flatMap((phase, i) => [
    {phase, kind: 'tps', oneMinute: i ? 20 : 10},
    {phase, kind: 'mspt', samples: 1200, coverageSeconds: 60, average: 10, p95: 20, p99: 30, max: 45}]);
  const summary = summarizeStage(samples, 'load');
  assert.equal(summary.minimumTps, 20);
  assert.equal(stableStage(summary), true);
  assert.equal(stableStage({...summary, worstRollingP99: 51}), false);
  assert.equal(stableStage({...summary, minimumTps: 19.8}), false);
  assert.equal(stableStage({...summary, maximumTickMs: 1000}), false);
  assert.equal(stableStage({...summary, minimumObservedTps: 17}), false);
  assert.equal(stableStage(summarizeStage([], 'load')), false);
  assert.equal(validWorkload({profile: 'mixed', players: 200, seconds: 60, driver: {actions: {}}}), false);
  assert.equal(validWorkload({profile: 'explore', players: 20, seconds: 60,
    driver: {actions: {movementPackets: 24000, explorationSteps: 24000, chunksReceivedDuringActions: 30}}}), true);
  assert.equal(validWorkload({profile: 'explore', players: 20, seconds: 60,
    driver: {actions: {movementPackets: 24000, explorationSteps: 18000, chunkWaitTicks: 6000, chunksReceivedDuringActions: 30}}}), false);
});

test('native upstream comparison uses the same one-minute window without invented percentiles', () => {
  const tps = parseMetric('TPS from last 5s, 1m, 5m, 15m: 20.0, 19.8, 19.9, 20.0');
  const mspt = parseMetric('[INFO]: ◴ 12.1/8.2/43.1, 13.5/8.1/45.1, 14.2/8.0/52.3');
  assert.equal(tps.oneMinute, 19.8);
  assert.equal(mspt.average, 14.2);
  assert.equal(mspt.max, 52.3);
  const summary = summarizeStage([tps, mspt, tps, mspt].map(m => ({...m, phase: 'load'})), 'load', true);
  assert.equal(summary.worstRollingP99, undefined);
  assert.equal(stableStage(summary), false);
  assert.equal(stableStage({...summary, minimumTps: 20}), true);
  assert.equal(stableStage({...summary, minimumTps: 20, maximumTickMs: 300}), false);
});

test('26.2 TPS windows cannot mistake a recovered 5-second value for 1 minute', () => {
  const legacy = parseMetric('TPS (upstream) 1m 20.00 | 5m 17.10 | 15m 18.38');
  assert.equal(legacy.fiveSeconds, 20);
  assert.equal(legacy.oneMinute, 17.1);
  assert.equal(legacy.fifteenMinutes, null);
  const fixed = parseMetric('TPS (upstream) 5s 20.00 | 1m 17.10 | 5m 18.38 | 15m 19.10');
  assert.equal(fixed.oneMinute, 17.1);
  assert.equal(fixed.fifteenMinutes, 19.1);
});

test('absent bots, missing metrics and abnormal shutdown cannot pass', () => {
  const stage = players => ({players, exitCode: 0, msptSamples: 4, driver: {result: 'PASS', allSpawned: true, minimumReady: players}});
  const input = {serverReady: true, stoppedCleanly: true, fatalErrors: [], stages: [stage(1), stage(20)]};
  assert.equal(classify(input), 'PASS');
  assert.equal(classify({...input, stoppedCleanly: false}), 'FAIL');
  assert.equal(classify({...input, stages: []}), 'FAIL');
  input.stages[1].driver.minimumReady = 19;
  assert.equal(classify(input), 'INVALID_TEST');
  input.stages[1].driver.minimumReady = 20;
  input.stages[1].msptSamples = 0;
  assert.equal(classify(input), 'INVALID_TEST');
});
