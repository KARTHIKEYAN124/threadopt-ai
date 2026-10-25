# ThreadOpt AI - measured experiment report

## Research question

Can a model trained on observed scheduling outcomes choose useful bounded configurations for varied workloads?

## Method

Collected 540 measured runs across 15 workload scenarios: CPU, I/O and mixed. Each scenario uses FIFO, priority and SJF with 1, 2, 4 and 8 platform threads, three repetitions (seeds 42-44), and the same generated work per matched configuration. Three JVM warmup runs are excluded. Task count increases with nominal duration: 40/2ms, 80/5ms, 120/10ms, 160/15ms, 200/20ms. The host exposes four Java processors. Measurements were collected locally on Windows; background activity is uncontrolled.

Throughput is completed tasks per elapsed second. P95 uses submission-to-completion latency and nearest-rank selection. The Java coordinator measures experiment elapsed time, including submission and thread termination. The I/O workload sleeps; the CPU workload performs arithmetic for a wall-clock budget. Worker counts above core count are useful for sleeping tasks but do not prove CPU compute speedup.

## Results: 200 tasks, 20ms nominal duration

Mean and sample standard deviation across three repetitions. Values are generated from datasets/benchmark_results.csv.

| Workload | Policy | Workers | Throughput, tasks/s | P95 latency, ms |
| --- | --- | ---: | ---: | ---: |
| CPU | FIFO | 1 | 49.3 +/- 0.7 | 3853.8 +/- 88.2 |
| CPU | FIFO | 2 | 96.5 +/- 3.8 | 1964.9 +/- 70.9 |
| CPU | FIFO | 4 | 180.2 +/- 15.7 | 1034.3 +/- 89.3 |
| CPU | FIFO | 8 | 236.7 +/- 28.0 | 810.4 +/- 113.2 |
| CPU | PRIORITY | 1 | 49.4 +/- 0.8 | 3848.5 +/- 109.8 |
| CPU | PRIORITY | 2 | 95.4 +/- 3.2 | 1985.9 +/- 68.3 |
| CPU | PRIORITY | 4 | 171.6 +/- 21.2 | 1111.6 +/- 145.7 |
| CPU | PRIORITY | 8 | 232.4 +/- 21.1 | 821.5 +/- 71.2 |
| CPU | SJF | 1 | 49.1 +/- 0.6 | 3727.3 +/- 58.8 |
| CPU | SJF | 2 | 96.3 +/- 3.1 | 1874.4 +/- 68.7 |
| CPU | SJF | 4 | 191.9 +/- 3.0 | 936.1 +/- 12.5 |
| CPU | SJF | 8 | 240.0 +/- 20.2 | 774.6 +/- 53.9 |
| IO | FIFO | 1 | 47.9 +/- 1.4 | 3975.3 +/- 116.9 |
| IO | FIFO | 2 | 93.1 +/- 4.5 | 2039.9 +/- 100.8 |
| IO | FIFO | 4 | 184.3 +/- 3.4 | 1013.1 +/- 29.9 |
| IO | FIFO | 8 | 367.2 +/- 8.6 | 494.9 +/- 9.5 |
| IO | PRIORITY | 1 | 47.4 +/- 1.0 | 4015.7 +/- 108.3 |
| IO | PRIORITY | 2 | 93.6 +/- 3.7 | 2030.6 +/- 78.0 |
| IO | PRIORITY | 4 | 184.8 +/- 7.4 | 1019.9 +/- 37.3 |
| IO | PRIORITY | 8 | 368.8 +/- 14.2 | 498.9 +/- 8.8 |
| IO | SJF | 1 | 47.6 +/- 0.8 | 3849.6 +/- 65.5 |
| IO | SJF | 2 | 93.8 +/- 2.1 | 1923.9 +/- 44.0 |
| IO | SJF | 4 | 183.6 +/- 6.2 | 974.9 +/- 36.2 |
| IO | SJF | 8 | 367.7 +/- 7.4 | 489.9 +/- 11.1 |
| MIXED | FIFO | 1 | 46.1 +/- 3.1 | 4157.7 +/- 267.3 |
| MIXED | FIFO | 2 | 91.2 +/- 3.6 | 2085.1 +/- 78.6 |
| MIXED | FIFO | 4 | 173.9 +/- 2.0 | 1078.9 +/- 21.5 |
| MIXED | FIFO | 8 | 317.4 +/- 10.0 | 583.5 +/- 9.5 |
| MIXED | PRIORITY | 1 | 46.5 +/- 2.4 | 4102.9 +/- 186.6 |
| MIXED | PRIORITY | 2 | 90.6 +/- 5.6 | 2128.6 +/- 115.5 |
| MIXED | PRIORITY | 4 | 182.2 +/- 9.2 | 1051.9 +/- 55.2 |
| MIXED | PRIORITY | 8 | 317.4 +/- 9.1 | 594.3 +/- 22.5 |
| MIXED | SJF | 1 | 45.8 +/- 2.2 | 4011.4 +/- 215.2 |
| MIXED | SJF | 2 | 90.9 +/- 3.4 | 1996.3 +/- 86.3 |
| MIXED | SJF | 4 | 179.7 +/- 9.0 | 1000.0 +/- 56.8 |
| MIXED | SJF | 8 | 331.6 +/- 17.0 | 539.2 +/- 17.4 |

## ML validation

RandomForestRegressor, 150 trees, min_samples_leaf=2, seed 42. Entire scenario groups are held out before fitting; 4 of 15 scenarios are used for evaluation. Held-out groups: CPU-10, IO-5, MIXED-15, MIXED-20.

Held-out P95 mean absolute error: **86.0ms**. A constant training-set mean predictor has MAE **704.4ms**. Mean held-out P95 is 975.1ms. These values describe this small local dataset; the exported model is refit on all rows after evaluation.

Inference evaluates bounded candidate worker counts and policies using predicted P95 times (1 + 0.02 * workers). It limits learned candidates to eight workers and falls back outside the calibration ranges (40-200 tasks, 2-20ms). Candidate worker counts are restricted to the measured set (1, 2, 4, 8). Predictions and validation error are exposed, and fallback results are explicitly labeled. This evaluation measures prediction quality, not a causal improvement from online adaptive scheduling.

## Threats to validity

The matrix has only fifteen scenario groups; task count and duration are correlated. Configuration order is fixed and hardware is shared. There is no statistical significance claim, independent target-machine validation, process CPU, worker utilization or queue-selection overhead recorded in this calibration CSV, scheduling-overhead microbenchmark, or automatic online adaptation. Aging mitigates starvation but these finite batch experiments do not establish long-run fairness under sustained arrivals. Wall-clock-bounded CPU work cannot establish Amdahl-style speedup for a fixed amount of computation. Random forest extrapolation is unreliable; calibration limits and a conventional fallback are part of the implementation.

## Reproduction

Run the commands in README.md with the Java backend running. Do not run other experiments during collection. Preserve seeds, software versions, machine resources, and this CSV. Repeat on dedicated hardware with randomized configuration order and fixed-operation CPU tasks before drawing research conclusions.

## Conclusion

The system executes real multithreaded work, compares queue policies, persists inspectable measurements and produces locally trained scheduling advice. Any claim that AI consistently outperforms fixed scheduling requires additional held-out execution experiments.
