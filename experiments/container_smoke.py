"""Verify the full Compose stack through its Nginx entry point, including WS."""
import base64,json,os,socket,time,urllib.request
BASE='http://127.0.0.1:3000'
def request(path,body=None):
    req=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=10) as response:return json.load(response)
deadline=time.monotonic()+120
while True:
    try:
        assert request('/api/health')['status']=='ok'
        break
    except Exception:
        if time.monotonic()>deadline:raise
        time.sleep(1)
with urllib.request.urlopen(BASE,timeout=10) as response:
    assert 'ThreadOpt AI' in response.read().decode()
r=request('/api/experiments',{'tasks':20,'workers':2,'durationMs':5,'policy':'SJF','workload':'IO','seed':42})
deadline=time.monotonic()+30
while r['status']=='RUNNING':
    if time.monotonic()>deadline:raise TimeoutError(r['id'])
    time.sleep(.1);r=request('/api/experiments/'+r['id'])
assert r['status']=='COMPLETED',r
assert r['metrics']['completed']==20 and r['metrics']['throughput']>0
assert any(row['id']==r['id'] for row in request('/api/experiments'))
advice=request('/api/optimizer/recommend',{'tasks':200,'durationMs':20,'workload':'MIXED','max_workers':8})
assert advice['source']=='random-forest',advice
# Both direct HTTP and TLS-terminated proxy origins must survive the second proxy.
for origin,extra in [('http://127.0.0.1:3000',''),('https://127.0.0.1:3000','X-Forwarded-Proto: https\r\n')]:
    with socket.create_connection(('127.0.0.1',3000),timeout=10) as connection:
        key=base64.b64encode(os.urandom(16)).decode()
        headers=f'GET /ws/metrics HTTP/1.1\r\nHost: 127.0.0.1:3000\r\nOrigin: {origin}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n{extra}\r\n'
        connection.sendall(headers.encode());response=connection.recv(4096)
        assert b'101' in response.split(b'\r\n',1)[0],(origin,response)
print('PASS: frontend, API, PostgreSQL persistence, real execution, trained ML, HTTP/HTTPS-proxy WebSocket upgrades')
