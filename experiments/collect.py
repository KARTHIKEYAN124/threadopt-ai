"""Collect identical seeded scenarios across candidate policies and worker counts."""
import argparse, csv, json, time, urllib.request
from pathlib import Path

def request(base,path,body=None):
    req=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers={"Content-Type":"application/json"})
    with urllib.request.urlopen(req,timeout=10) as r:return json.load(r)
def collect(base,output,repeats):
    Path(output).parent.mkdir(parents=True,exist_ok=True)
    # Exclude JVM/class-loading warmup runs from the training CSV.
    for _ in range(3):
        warm=request(base,'/api/experiments',dict(tasks=40,workers=4,durationMs=5,policy='FIFO',workload='MIXED',seed=0))
        while warm['status']=='RUNNING':
            time.sleep(.05);warm=request(base,'/api/experiments/'+warm['id'])

    with open(output,'w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=['scenario_id','tasks','durationMs','io_ratio','workers','policy','seed','repeat','p95LatencyMs','throughput','averageWaitMs','elapsedMs'])
        writer.writeheader()
        # Five durations x three workload mixes; group split prevents seed/config leakage.
        for workload in ['CPU','IO','MIXED']:
            for duration in [2,5,10,15,20]:
                tasks={2:40,5:80,10:120,15:160,20:200}[duration]
                for repeat in range(repeats):
                    for workers in [1,2,4,8]:
                        for policy in ['FIFO','PRIORITY','SJF']:
                            config=dict(tasks=tasks,workers=workers,policy=policy,workload=workload,durationMs=duration,seed=42+repeat)
                            r=request(base,'/api/experiments',config)
                            deadline=time.monotonic()+230
                            while r['status']=='RUNNING':
                                if time.monotonic()>deadline:raise TimeoutError(r['id'])
                                time.sleep(.05);r=request(base,'/api/experiments/'+r['id'])
                            if r['status']!='COMPLETED':raise RuntimeError(r)
                            metrics=r['metrics'];row=dict(scenario_id=f'{workload}-{duration}',tasks=tasks,durationMs=duration,io_ratio={'CPU':0,'IO':1,'MIXED':.5}[workload],workers=workers,policy=policy,seed=42+repeat,repeat=repeat,**{k:metrics[k] for k in ['p95LatencyMs','throughput','averageWaitMs','elapsedMs']})
                            writer.writerow(row);f.flush()
                print(f'Collected {workload} / {duration} ms',flush=True)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--base',default='http://localhost:8080');p.add_argument('--output',default='datasets/benchmark_results.csv');p.add_argument('--repeats',type=int,default=3);a=p.parse_args();collect(a.base,a.output,a.repeats)
