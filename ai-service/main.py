from pathlib import Path
import json
import os
import joblib
from fastapi import FastAPI
from pydantic import BaseModel, Field
from features import POLICIES, vector

app = FastAPI(title="ThreadOpt scheduling advisor", version="1.0.0")
MODEL_PATH = Path(os.getenv("MODEL_PATH", str(Path(__file__).parent / "models/model.joblib")))
bundle = joblib.load(MODEL_PATH) if MODEL_PATH.exists() else None

class Features(BaseModel):
    tasks: int = Field(default=200, ge=1, le=1000)
    durationMs: int = Field(default=20, ge=1, le=100)
    workload: str = Field(default="MIXED", pattern="^(CPU|IO|MIXED)$")
    max_workers: int = Field(default=8, ge=1, le=32)

@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": bundle is not None}

@app.get("/model/info")
def info():
    return bundle["metadata"] if bundle else {"source": "rule-based", "reason": "No measured training artifact available"}

@app.post("/predict")
def predict(f: Features):
    io = {"CPU": 0., "IO": 1., "MIXED": .5}[f.workload]
    in_domain = 40 <= f.tasks <= 200 and 2 <= f.durationMs <= 20
    if bundle is None or not in_domain:
        return {"recommended_workers": min(f.max_workers, os.cpu_count() or 1, 4) if io == 0 else min(f.max_workers, 8), "recommended_policy": "SJF", "source": "rule-based", "reason": "Outside calibration domain; using a heuristic" if bundle and not in_domain else "Train on measured benchmarks to enable ML predictions"}
    candidates = [(w,p) for w in (1,2,4,8) if w <= f.max_workers for p in POLICIES]
    rows = [vector(f.tasks,f.durationMs,io,w,p) for w,p in candidates]
    predictions = bundle["model"].predict(rows)
    # Objective is measured P95 plus a small explicit resource penalty.
    costs = [max(0,float(y))*(1+.02*w) for y,(w,p) in zip(predictions,candidates)]
    index = min(range(len(costs)),key=costs.__getitem__)
    workers,policy = candidates[index]
    return {"recommended_workers": workers,"recommended_policy": policy,"predicted_p95_latency_ms": max(0,float(predictions[index])),"source": "random-forest","model_version": bundle["metadata"]["version"],"validation_mae_ms": bundle["metadata"]["validation_mae_ms"]}
