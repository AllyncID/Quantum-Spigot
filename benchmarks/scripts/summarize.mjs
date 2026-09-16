import { readFileSync } from 'node:fs';

const path = process.argv[2];
if (!path) {
  console.error('Usage: node benchmarks/scripts/summarize.mjs <ticks.csv>');
  process.exit(2);
}
try {
  const lines = readFileSync(path, 'utf8').trim().split(/\r?\n/);
  if (lines.shift() !== 'sample,mspt') throw new Error('Expected sample,mspt CSV header');
  const values = lines.map((line, index) => {
    const columns = line.split(',');
    const value = Number(columns[1]);
    if (columns.length !== 2 || Number(columns[0]) !== index || columns[1].trim() === '' || !Number.isFinite(value) || value < 0) {
      throw new Error(`Invalid sample on line ${index + 2}`);
    }
    return value;
  });
  if (values.length === 0) throw new Error('No samples');
  values.sort((a, b) => a - b);
  const percentile = (p) => values[Math.ceil(values.length * p) - 1];
  console.log(JSON.stringify({
    metric: 'ServerTickEndEvent.tickDuration_ms',
    samples: values.length,
    mean: values.reduce((a, b) => a + b, 0) / values.length,
    p50: percentile(0.50), p95: percentile(0.95), p99: percentile(0.99),
    max: values.at(-1),
    note: 'Observed tick-end event durations; not a throughput or compatibility guarantee.'
  }, null, 2));
} catch (error) {
  console.error(error.message);
  process.exit(1);
}
