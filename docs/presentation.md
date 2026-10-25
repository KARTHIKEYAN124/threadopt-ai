# ThreadOpt AI - demonstration deck

## 1. Research question
Can measured workload features guide thread-pool configuration to improve latency without uncontrolled resource growth?

## 2. Architecture
React dashboard -> Spring Boot REST/WebSocket API -> custom Java platform-thread pool. Python/scikit-learn provides configuration advice; H2/PostgreSQL stores experiments. Java owns thread execution.

## 3. Concurrency core
A monitor protects a bounded queue, submission, selection and shutdown. Workers release the lock before executing tasks. Stable sequence numbers resolve policy ties. Counters are atomic and timing collections are synchronized.

## 4. Scheduling policies
FIFO, descending priority, and shortest estimated job first. Aged queues select FIFO after two seconds to reduce starvation. Policies are non-preemptive. Show the policy-ordering unit test.

## 5. Live demonstration
Open Overview. Choose Mixed, 200 tasks, 4 workers, 20ms duration, seed 42. Run FIFO, then Priority, then SJF with the same settings. Observe live workers, queue length and measured results. Repeat rather than assuming one run is representative.

## 6. Performance definitions
Throughput: completed tasks / elapsed seconds. Queue wait: execution start - submission. End-to-end latency: completion - submission. P95/P99: nearest-rank percentiles. One worker supplies a sequential baseline.

## 7. ML experiment
Train a random forest from measured outcomes, with worker count and queue policy as candidate features. Hold out entire workload scenarios. Score predicted P95 times (1 + 0.02 * workers). Show model metadata, validation MAE and CSV provenance.

## 8. Test the recommendation
Click Recommend configuration. Explain the source label and predicted P95. Run the selected configuration and compare observed latency against the prediction and fixed baselines. Predictions are not guarantees.

## 9. Reliability and safety
Show invalid-input rejection, single-experiment admission, cancellation, bounded predefined work, exception isolation, graceful draining and persisted history. Demonstrate resize tests; runtime adaptive control is future work.

## 10. Conclusions and limitations
Read the measured results in report.md. This is a working scheduling laboratory. The calibration dataset is small, task count and duration are correlated, measurements share a machine, and CPU tasks are wall-clock bounded. Do not claim fixed-operation CPU speedup, universal policy superiority or production multi-tenant isolation. Future work: fixed-operation CPU workloads, randomized experiments, larger held-out datasets, cooldown-based adaptive control and authentication.
