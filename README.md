# ThreadOpt AI

Source: https://github.com/KARTHIKEYAN124/threadopt-ai

[![CI](https://github.com/KARTHIKEYAN124/threadopt-ai/actions/workflows/ci.yml/badge.svg)](https://github.com/KARTHIKEYAN124/threadopt-ai/actions/workflows/ci.yml)

A working scheduling laboratory: a custom Java platform-thread pool, Spring Boot experiment API, Python/scikit-learn advisor, and React/TypeScript live dashboard.

## Run locally

Requires Java 21, Maven, Python 3.12+ and Node 22. Run these commands in separate terminals from the project root:

```powershell
mvn -f backend/pom.xml spring-boot:run
```

```powershell
python -m pip install -r ai-service/requirements.txt
python main.py
```

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open **http://127.0.0.1:5173**. The dashboard proxies `/api` and `/ws` to port 8080. Python listens on 8000. Local history persists in `data/` (or `backend/data/` when started from that directory). No external AI provider or API key is required: this project uses a local random forest.

## What works

- Custom bounded task queue with real platform threads and FIFO, priority and shortest-job-first selection.
- Stable tie ordering, two-second queue aging, controlled resizing API, graceful drain, cancellation and exception isolation.
- Seeded CPU, I/O and mixed workloads; asynchronous experiments with a single-run admission limit.
- Measured throughput, queue wait, P95/P99 latency, failures, completion counts, process CPU utilization, worker utilization, and queue-selection overhead.
- WebSocket dashboard, worker activity, experiment details, JSON exports, configuration advisor.
- Durable H2 storage locally; PostgreSQL in Docker Compose.
- Random forest trained on measured Java results, scenario-level holdout validation, explicit heuristic fallback.
- Unit/concurrency/API tests, Python validation tests, CI and three container definitions.

The advisor configures the next experiment. Automatic live resizing, per-user authentication/budgets, work stealing, JMH, and drift monitoring are advanced extensions rather than implemented claims. Pool resizing is supported in the Java core and tested independently.

## Reproduce benchmarks and train

Start the Java backend first. Do not launch other experiments while collecting data.

```powershell
python experiments/collect.py --repeats 3
cd ai-service
python train.py ../datasets/benchmark_results.csv
python -m uvicorn main:app --port 8000
```

Run `python experiments/summarize.py` from the root to regenerate the academic report after collecting the default 540-run matrix. Restart the advisor after training. Never load untrusted joblib model files. Dataset rows contain real measured engine outcomes; no artificial optimum labels are generated. Scenarios vary workload mix and duration/task count. The small calibration dataset is a demonstration, not evidence of broad generalization. Scheduling policies are non-preemptive. See `docs/report.md` for methodology, results and limitations.

## Validate

```powershell
mvn -f backend/pom.xml verify
python -m pytest -c ai-service/pytest.ini ai-service/tests
npm --prefix frontend run build
```

Browser test instructions and observed results are in `docs/validation.md`. Docker and VPS instructions are in `docs/deployment.md`. Academic demonstration notes are in `docs/presentation.md`.

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | /api/health | Health and available processors |
| GET | /api/schedulers | Supported policies |
| POST | /api/experiments | Start a bounded seeded experiment (202) |
| GET | /api/experiments | Saved history |
| GET | /api/experiments/{id} | Status, config and worker snapshot |
| GET | /api/experiments/{id}/metrics | Measurements |
| POST | /api/experiments/{id}/cancel | Cancel a running experiment |
| GET | /api/metrics/live | Current experiment |
| POST | /api/optimizer/recommend | Local ML or labeled fallback |
| WS | /ws/metrics | 500ms live snapshots |
| GET | :8000/health | Advisor readiness |
| GET | :8000/model/info | Training and validation metadata |
| POST | :8000/predict | Advisor candidate selection |

Example request to POST `/api/experiments`:

```json
{"tasks":200,"workers":4,"durationMs":20,"policy":"FIFO","workload":"MIXED","seed":42}
```

Only predefined work runs. Tasks are capped at 1,000, workers at 32, and nominal duration at 100ms (generated durations range from 1 to twice that value). The server allows one running benchmark globally. Experiment timeout is 220 seconds. Resizing takes effect between tasks. On restart, unfinished persisted experiments are marked INTERRUPTED.
