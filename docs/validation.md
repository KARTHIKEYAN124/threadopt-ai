# Validation record

Date: 2026-10-25. Native Windows execution, Java 21, Python 3.14. Node 24.14.0. CI and frontend container use Node 22. Local dashboard: http://127.0.0.1:5173. Backend: 8080. Advisor: 8000.

## Automated checks

Local results: seven Java tests passed (six concurrency/core tests and one API integration test). Three Python API tests passed before training; the final run also exercises the bundled model. The TypeScript production build passed and splits the chart library from the main bundle. Commands:

```powershell
mvn -f backend/pom.xml verify
python -m pytest -c ai-service/pytest.ini ai-service/tests
npm --prefix frontend run build
```

Java coverage includes execution without lost/duplicated tasks across 1,000 submissions, stable FIFO/priority/SJF ordering, shrink/grow behavior, task exceptions, invalid worker bounds, cancellation, queue aging, API validation, single-experiment admission, unknown IDs and persistence of experiment records. Python covers health, bounded recommendations and invalid workloads/limits.

## Browser verification

Browser plugin classification: absent. Fallback: installed Playwright Chromium, using experiments/browser_smoke.py. Optional reproduction:

```powershell
python -m pip install playwright
python -m playwright install chromium
python experiments/browser_smoke.py
```

Run all three services first, and keep the benchmark collector stopped while running this smoke test. The smoke test creates real experiments and saves screenshots under docs/.

| Check (all passed) | Observed evidence |
| --- | --- |
| Page identity | ThreadOpt AI title and Scheduling laboratory heading |
| Rendering | Populated dashboard, no blank page or framework error overlay |
| Console health | No uncaught browser page errors |
| Live execution | Real queued/completed counts, busy workers, nonzero measured throughput |
| End-to-end controls | Start -> live updates -> completion -> recommendation -> history -> details -> export -> methodology -> cancellation |
| Desktop | 1440x1050 full-page screenshot |
| Mobile | 390x844, readable stacked controls and no horizontal document overflow |

The final smoke run passed every interaction above with an empty browser console error list. An explicit random-forest response was verified through the Java API. Final resource/selection metrics were also confirmed.

Screenshot evidence: dashboard-desktop.png and dashboard-mobile.png. The first reference is design-concept.png; view_image inspection compared palette, typography, sidebar, composition, form controls and responsive behavior. Details and intentional differences are in design-review.md. The dashboard faithfully follows that design with measured states replacing the illustrative data.

## Final local outcomes

The 540-row dataset passed uniqueness, completeness and positive-outcome checks. The model trained successfully with scenario-level holdout MAE 86.0ms against a 704.4ms constant baseline. All three Python tests pass with the bundled model. Restart testing preserved the saved history and marked a deliberately interrupted run INTERRUPTED. Maven tests passed; Windows initially locked the running JAR during packaging, so the server was stopped and packaging then passed. Docker Compose syntax validation passed without an engine.

The final end-to-end ML check exposed an HTTP/2 upgrade mismatch between Java HttpClient and the Uvicorn HTTP server. The advisor client now explicitly uses HTTP/1.1; browser QA verifies a random-forest response through the Java API rather than accepting fallback as proof of ML integration.

## Deployment limits

Docker CLI is installed, but the Docker Desktop Linux engine socket is unavailable. Compose syntax passed locally. Remote GitHub Actions then built all three images and verified the running four-service stack, including PostgreSQL persistence, real task execution, trained-model inference and HTTP/HTTPS-proxy WebSocket upgrades. H2 is used for native local execution. No public hosting target or domain has been supplied. Dockerfiles, Nginx, Caddy and VPS instructions are prepared; public HTTPS deployment is not claimed.

Automatic online adaptation, JMH microbenchmarks and multi-user account isolation are not implemented. Chromium was tested; Firefox/Safari and sustained multi-user load remain untested.

## Remote CI evidence

All four jobs (Java, Python, frontend, containers) passed for code commit `97636fa`. Run: https://github.com/KARTHIKEYAN124/threadopt-ai/actions/runs/40214940017 . The container smoke test passed through Nginx on the Docker network with PostgreSQL and the bundled trained model. Public hosting and real certificate/domain issuance remain pending.

## Idle metrics and new-experiment regression

The dashboard shows configured workers while idle, an explicit Empty queue, and the latest saved result rather than treating missing/live-only observations as zero. HTTP polling backs up WebSocket updates. The header + button opens a native, keyboard-accessible configuration dialog instead of focusing Tasks. Targeted Playwright checks passed at 1440x1050 and 390x844: dialog creation/run/close, nonzero queue during real execution, completed-result persistence across reload, history without WebSockets, and no horizontal overflow or browser errors. The production build and full browser smoke flow also passed.
