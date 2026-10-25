import { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  FlaskConical,
  FileText,
  Plus,
  Play,
  Cpu,
  Download,
  Sparkles,
  X,
} from "lucide-react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
} from "recharts";
import "./style.css";
type Config = {
  tasks: number;
  workers: number;
  durationMs: number;
  policy: string;
  workload: string;
  seed: number;
};
type Metrics = {
  throughput: number;
  averageWaitMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  completed: number;
  failed: number;
  elapsedMs: number;
};
type Experiment = {
  id: string;
  createdAt: number;
  status: string;
  config: Config;
  metrics: Metrics;
  pool: {
    queued: number;
    completed: number;
    workers: { name: string; busy: boolean; retiring: boolean }[];
  };
};
const initial: Config = {
  tasks: 200,
  workers: 4,
  durationMs: 20,
  policy: "FIFO",
  workload: "MIXED",
  seed: 42,
};
async function api(path: string, body?: unknown) {
  const r = await fetch(
    "/api" + path,
    body === undefined
      ? undefined
      : {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
  );
  const data = await r.json();
  if (!r.ok) throw new Error(data.message || data.detail || "Request failed");
  return data;
}
const number = (v?: number) =>
  Number(v || 0).toLocaleString(undefined, { maximumFractionDigits: 1 });
function App() {
  const [page, setPage] = useState("Overview"),
    [config, setConfig] = useState(initial),
    [live, setLive] = useState<Experiment | null>(null),
    [rows, setRows] = useState<Experiment[]>([]),
    [points, setPoints] = useState<{ time: number; throughput: number }[]>([]),
    [connected, setConnected] = useState(false),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [advice, setAdvice] = useState<any>(null),
    [selected, setSelected] = useState<Experiment | null>(null),
    [creating, setCreating] = useState(false);
  const lastId = useRef("");
  const createDialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (creating) createDialog.current?.showModal();
    else createDialog.current?.close();
  }, [creating]);
  const refresh = () =>
    api("/experiments")
      .then((history: Experiment[]) => {
        setRows(history);
        const latest = history
          .slice()
          .sort((a, b) => b.createdAt - a.createdAt)[0];
        if (latest) setLive((current) => current ?? latest);
      })
      .catch((e) => setError(e.message));
  useEffect(() => {
    refresh();
    let stopped = false;
    let socket: WebSocket;
    let retry: ReturnType<typeof setTimeout>;
    const connect = () => {
      socket = new WebSocket(
        `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws/metrics`,
      );
      socket.onopen = () => setConnected(true);
      socket.onclose = () => {
        setConnected(false);
        if (!stopped) retry = setTimeout(connect, 2000);
      };
      socket.onmessage = (e) => {
        const r = JSON.parse(e.data);
        if (!r.id) return;
        setLive(r);
        if (lastId.current !== r.id) {
          lastId.current = r.id;
          setPoints([]);
        }
        setPoints((p) => {
          const next = {
            time: Number((r.metrics.elapsedMs / 1000).toFixed(1)),
            throughput: r.metrics.throughput,
          };
          return p.length && p[p.length - 1].time === next.time
            ? p
            : [...p.slice(-119), next];
        });
        if (r.status !== "RUNNING")
          setRows((old) => [r, ...old.filter((x) => x.id !== r.id)]);
      };
    };
    connect();
    // Keep the dashboard current even if the WebSocket connection is unavailable.
    const poll = setInterval(() => {
      api("/metrics/live")
        .then((r) => {
          if (stopped || !r.id) return;
          setLive(r);
          setRows((old) => [r, ...old.filter((x) => x.id !== r.id)]);
        })
        .catch(() => {});
    }, 2000);
    return () => {
      clearInterval(poll);
      stopped = true;
      clearTimeout(retry);
      socket.close();
    };
  }, []);
  const running = live?.status === "RUNNING";
  async function run() {
    setBusy(true);
    setError("");
    try {
      const r = await api("/experiments", config);
      setLive(r);
      setCreating(false);
      setPage("Overview");
      setPoints([]);
      lastId.current = r.id;
      setAdvice(null);
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function recommend() {
    setBusy(true);
    setError("");
    try {
      const r = await api("/optimizer/recommend", {
        tasks: config.tasks,
        durationMs: config.durationMs,
        workload: config.workload,
        max_workers: 8,
      });
      setAdvice(r);
      setConfig((c) => ({
        ...c,
        workers: r.recommended_workers,
        policy: r.recommended_policy,
      }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function download() {
    const content = JSON.stringify(rows, null, 2),
      url = URL.createObjectURL(
        new Blob([content], { type: "application/json" }),
      );
    const a = document.createElement("a");
    a.href = url;
    a.download = "threadopt-experiments.json";
    a.click();
    URL.revokeObjectURL(url);
  }
  const configuration = (
    <section className="panel config">
      <h2>Workload configuration</h2>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          run();
        }}
      >
        <label>
          Workload
          <select
            value={config.workload}
            onChange={(e) => setConfig({ ...config, workload: e.target.value })}
          >
            <option value="MIXED">Mixed</option>
            <option value="CPU">CPU-bound</option>
            <option value="IO">I/O-bound</option>
          </select>
        </label>
        <label>
          Policy
          <select
            value={config.policy}
            onChange={(e) => setConfig({ ...config, policy: e.target.value })}
          >
            <option>FIFO</option>
            <option value="PRIORITY">Priority</option>
            <option>SJF</option>
          </select>
        </label>
        <div className="form-grid">
          {(
            [
              { key: "tasks", label: "Tasks", min: 1, max: 1000 },
              {
                key: "workers",
                label: "Workers",
                min: 1,
                max: 32,
              },
              {
                key: "durationMs",
                label: "Duration (ms)",
                min: 1,
                max: 100,
              },
              {
                key: "seed",
                label: "Seed",
                min: 0,
                max: 2147483647,
              },
            ] as const
          ).map((f) => (
            <label key={f.key}>
              {f.label}
              <input
                type="number"
                required
                min={f.min}
                max={f.max}
                value={config[f.key]}
                onChange={(e) =>
                  setConfig({
                    ...config,
                    [f.key]: e.target.valueAsNumber,
                  })
                }
              />
            </label>
          ))}
        </div>
        <button className="full" type="submit" disabled={busy || running}>
          <Play size={16} />
          {running ? "Experiment runningâ€¦" : "Run experiment"}
        </button>
        <button
          type="button"
          className="secondary full"
          disabled={busy || running}
          onClick={recommend}
        >
          <Sparkles size={16} />
          Recommend configuration
        </button>
        {running && (
          <button
            type="button"
            className="cancel full"
            onClick={() =>
              api("/experiments/" + live.id + "/cancel", {})
                .then(setLive)
                .catch((e) => setError(e.message))
            }
          >
            Cancel experiment
          </button>
        )}
      </form>
      {advice && (
        <div className="advice">
          <strong>
            {advice.recommended_workers} workers Â· {advice.recommended_policy}
          </strong>
          <span>
            {advice.source}
            {advice.predicted_p95_latency_ms !== undefined
              ? " Â· predicted P95 " +
                number(advice.predicted_p95_latency_ms) +
                " ms"
              : ""}
          </span>
          <small>
            Applied to the next run. Validate against measured results.
          </small>
        </div>
      )}
      <p className="config-note">
        Bounded to 1,000 tasks and 32 workers.
        <br />
        Only one experiment runs at a time.
      </p>
    </section>
  );
  return (
    <div className="app">
      <aside>
        <a className="brand" href="#" onClick={() => setPage("Overview")}>
          ThreadOpt <span>AI</span>
        </a>
        <nav>
          {[
            { name: "Overview", icon: Activity },
            { name: "Experiments", icon: FlaskConical },
            { name: "Methodology", icon: FileText },
          ].map(({ name, icon: Icon }) => (
            <button
              className={page === name ? "active" : ""}
              onClick={() => setPage(name)}
              key={name}
            >
              <Icon size={20} />
              {name}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <Cpu size={18} />
          <div>
            Custom Java engine<small>Platform threads Â· Java 21</small>
          </div>
        </div>
      </aside>
      <main>
        <header>
          <div>
            <h1>{page === "Overview" ? "Scheduling laboratory" : page}</h1>
            <p>
              {page === "Overview"
                ? "Configure, execute, and compare real workloads."
                : page === "Experiments"
                  ? "Measured results from your scheduling experiments."
                  : "How the engine works and how to interpret results."}
            </p>
          </div>
          <button
            disabled={running || busy}
            aria-label="New experiment"
            onClick={() => {
              setConfig({ ...initial });
              setAdvice(null);
              setError("");
              setCreating(true);
            }}
          >
            <Plus size={19} />
            New experiment
          </button>
        </header>
        {error && (
          <div role="alert" className="error">
            {error}
            <button
              className="icon"
              aria-label="Dismiss error"
              onClick={() => setError("")}
            >
              <X size={16} />
            </button>
          </div>
        )}
        {page === "Methodology" ? (
          <article className="panel methodology">
            <h2>A scheduling engine you can inspect.</h2>
            <p>
              Every experiment runs bounded, predefined work on a custom Java
              platform-thread pool. No browser simulation contributes to the
              measurements.
            </p>
            <h3>Three queue policies</h3>
            <p>
              FIFO selects submission order. Priority selects higher values
              first. SJF selects the shortest estimated task first. Ties use
              submission order. After two seconds of waiting, the queue switches
              to FIFO selection until aged tasks drain.
            </p>
            <h3>Reproducible workloads</h3>
            <p>
              The seed controls duration, priority, and CPU/I/O assignment. Use
              the same seed, task count, workload, and duration across policies.
              CPU tasks perform arithmetic for a bounded wall-clock duration;
              I/O tasks sleep. This is a scheduler demonstration, not a
              fixed-operation CPU speedup benchmark.
            </p>
            <h3>Measured metrics</h3>
            <p>
              Throughput is completed tasks divided by elapsed seconds. Queue
              wait ends when execution starts. P95 and P99 use nearest-rank
              percentiles of submission-to-completion latency. Values are
              cumulative during a run and final after completion.
            </p>
            <h3>Machine learning with a baseline</h3>
            <p>
              A random forest predicts P95 for candidate worker counts and queue
              policies. Training uses measured Java results and holds out
              complete workload scenarios. A 2% per-worker penalty balances
              latency against worker cost. The advisor changes the next
              experiment configuration; it does not silently resize a running
              benchmark. If the model is unavailable, an explicitly labeled
              rule-based recommendation is used.
            </p>
            <h3>Fair comparisons</h3>
            <p>
              Use one worker for a sequential baseline. Repeat each
              configuration, warm up the JVM, and report distributions. Shared
              hardware, CPU quotas, GC, and clock-based tasks affect results. AI
              recommendations are hypotheses to test; improvement is never
              assumed.
            </p>
          </article>
        ) : (
          <>
            {page === "Overview" && (
              <>
                <section className="stats">
                  {[
                    [
                      running ? "Workers" : "Configured workers",
                      running
                        ? live?.pool.workers.length || live?.config.workers
                        : config.workers,
                    ],
                    ["Queued", running ? (live?.pool.queued ?? 0) : "Empty"],
                    [
                      running ? "Completed" : "Last run completed",
                      live ? live.metrics.completed : "\u2014",
                    ],
                    [
                      "Average wait",
                      live
                        ? `${number(live.metrics.averageWaitMs)} ms`
                        : "\u2014",
                    ],
                  ].map(([label, value]) => (
                    <div className="stat" key={label}>
                      <span>{label}</span>
                      <strong>{value}</strong>
                    </div>
                  ))}
                </section>
                <p className="run-context" role="status">
                  {running
                    ? `Running ${live.config.tasks} tasks with ${live.config.workers} workers.`
                    : live
                      ? "No experiment running. Showing the latest saved results; workers launch when you run a workload."
                      : "Ready to run. Choose a workload or create a new experiment to launch workers."}
                </p>
                <div className="workspace">
                  <div className="monitor">
                    <section className="panel chart">
                      <div className="panel-heading">
                        <h2>Throughput over time</h2>
                        <span className="connection">
                          <i className={connected ? "online" : ""} />
                          {connected ? "Live connection" : "Reconnecting"}
                        </span>
                      </div>
                      <div className="chart-area">
                        {points.length ? (
                          <ResponsiveContainer width="100%" height="100%">
                            <LineChart
                              data={points}
                              margin={{
                                top: 15,
                                right: 15,
                                bottom: 15,
                                left: 0,
                              }}
                            >
                              <CartesianGrid
                                stroke="#e9edf0"
                                vertical={false}
                              />
                              <XAxis
                                dataKey="time"
                                tick={{ fontSize: 11 }}
                                label={{
                                  value: "Time (seconds)",
                                  position: "insideBottom",
                                  offset: -10,
                                }}
                              />
                              <YAxis tick={{ fontSize: 11 }} width={48} />
                              <Tooltip
                                formatter={(v: number) => [
                                  number(v) + " tasks/s",
                                  "Throughput",
                                ]}
                              />
                              <Line
                                type="monotone"
                                dataKey="throughput"
                                stroke="#00a85a"
                                dot={false}
                                strokeWidth={2}
                              />
                            </LineChart>
                          </ResponsiveContainer>
                        ) : (
                          <div className="empty-chart">
                            <Activity size={30} />
                            <strong>Your next experiment starts here</strong>
                            <span>
                              Run a workload to see measured throughput.
                            </span>
                          </div>
                        )}
                      </div>
                    </section>
                    <section className="panel worker-panel">
                      <h2>Worker activity</h2>
                      {live?.pool.workers.length ? (
                        <div className="workers">
                          {live.pool.workers.map((w, i) => (
                            <div className="worker" key={w.name}>
                              <small>WORKER {i + 1}</small>
                              <span className={w.busy ? "working" : ""}>
                                <i />
                                {w.retiring
                                  ? "Retiring"
                                  : w.busy
                                    ? "Running"
                                    : "Idle"}
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="muted">
                          No active workers. Start an experiment to launch the
                          pool.
                        </p>
                      )}
                    </section>
                  </div>
                  {!creating && configuration}
                </div>
              </>
            )}
            <section className="panel results">
              <div className="panel-heading">
                <h2>
                  {page === "Overview"
                    ? "Recent experiments"
                    : "Experiment history"}
                </h2>
                <button
                  className="text-button"
                  onClick={download}
                  disabled={!rows.length}
                >
                  <Download size={15} />
                  Export JSON
                </button>
              </div>
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      {[
                        "Experiment",
                        "Policy",
                        "Workers",
                        "Throughput",
                        "P95 latency",
                        "Status",
                      ].map((h) => (
                        <th key={h}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rows
                      .slice()
                      .sort((a, b) => b.createdAt - a.createdAt)
                      .slice(0, page === "Overview" ? 8 : 500)
                      .map((r) => (
                        <tr key={r.id}>
                          <td>
                            <button
                              className="row-link"
                              onClick={() => {
                                api("/experiments/" + r.id)
                                  .then(setSelected)
                                  .catch((e) => setError(e.message));
                              }}
                            >
                              {r.id.slice(0, 8)}
                              <small>
                                {r.config.workload} Â· {r.config.tasks} tasks
                              </small>
                            </button>
                          </td>
                          <td>{r.config.policy}</td>
                          <td>{r.config.workers}</td>
                          <td>{number(r.metrics.throughput)} tasks/s</td>
                          <td>{number(r.metrics.p95LatencyMs)} ms</td>
                          <td>
                            <span
                              className={"status " + r.status.toLowerCase()}
                            >
                              <i />
                              {r.status.toLowerCase().replace("_", " ")}
                            </span>
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
                {!rows.length && (
                  <div className="empty-table">
                    No experiments yet. Run your first workload to build a
                    comparison.
                  </div>
                )}
              </div>
            </section>
          </>
        )}
        <footer>
          ThreadOpt AI{" "}
          <span>Measured execution. Reproducible comparisons.</span>
        </footer>
      </main>
      <dialog
        ref={createDialog}
        className="panel create-dialog"
        aria-labelledby="create-title"
        onCancel={() => setCreating(false)}
        onClose={() => setCreating(false)}
      >
        <div className="panel-heading">
          <h2 id="create-title">New experiment</h2>
          <button
            type="button"
            className="icon"
            aria-label="Close new experiment"
            onClick={() => setCreating(false)}
          >
            <X size={20} />
          </button>
        </div>
        <p className="muted">
          Configure your workload, then run it to start the worker pool.
        </p>
        {creating && configuration}
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
      </dialog>
      {selected && (
        <div className="modal-backdrop" onClick={() => setSelected(null)}>
          <section
            className="panel modal"
            role="dialog"
            aria-modal="true"
            aria-label="Experiment details"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="panel-heading">
              <h2>Experiment {selected.id.slice(0, 8)}</h2>
              <button
                className="icon"
                autoFocus
                aria-label="Close details"
                onClick={() => setSelected(null)}
              >
                <X />
              </button>
            </div>
            <pre>{JSON.stringify(selected, null, 2)}</pre>
          </section>
        </div>
      )}
    </div>
  );
}
createRoot(document.getElementById("root")!).render(<App />);
