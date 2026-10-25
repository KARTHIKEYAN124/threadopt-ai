"""Train only from observed engine measurements; hold out complete scenarios."""
import argparse
import sklearn
import platform
import csv
import json
from pathlib import Path
import joblib
import numpy as np
from sklearn.ensemble import RandomForestRegressor
from sklearn.model_selection import GroupShuffleSplit
from sklearn.metrics import mean_absolute_error
from features import vector, FEATURES

def train(csv_path, output):
    with open(csv_path, newline="") as f:
        rows = list(csv.DictReader(f))
    groups = [r["scenario_id"] for r in rows]
    if len(set(groups)) < 5:
        raise ValueError("At least five independent workload scenarios required")
    x = np.array([vector(int(r["tasks"]),int(r["durationMs"]),float(r["io_ratio"]),int(r["workers"]),r["policy"]) for r in rows])
    y = np.array([float(r["p95LatencyMs"]) for r in rows])
    a,b = next(GroupShuffleSplit(n_splits=1,test_size=.25,random_state=42).split(x,y,groups))
    model = RandomForestRegressor(n_estimators=150,min_samples_leaf=2,random_state=42,n_jobs=1)
    model.fit(x[a],y[a])
    baseline_mae = float(mean_absolute_error(y[b], np.full(len(b), y[a].mean())))
    metadata = {"python_version":platform.python_version(),"sklearn_version":sklearn.__version__,"baseline_mean_predictor_mae_ms":baseline_mae,"heldout_mean_p95_ms":float(y[b].mean()),"version":"measured-v1","features":FEATURES,"rows":len(rows),"scenarios":len(set(groups)),"validation_mae_ms":float(mean_absolute_error(y[b],model.predict(x[b]))),"training_scenarios":sorted(set(np.array(groups)[a])),"validation_scenarios":sorted(set(np.array(groups)[b])),"objective":"P95 latency times (1 + 0.02 * workers)","limitation":"Local measurements; evaluate on target hardware before drawing conclusions"}
    model.fit(x,y)
    Path(output).parent.mkdir(parents=True,exist_ok=True)
    joblib.dump({"model":model,"metadata":metadata},output)
    Path(output).with_suffix('.json').write_text(json.dumps(metadata,indent=2))
    print(json.dumps(metadata,indent=2))
if __name__ == "__main__":
    p=argparse.ArgumentParser();p.add_argument("csv");p.add_argument("--output",default="models/model.joblib");a=p.parse_args();train(a.csv,a.output)
