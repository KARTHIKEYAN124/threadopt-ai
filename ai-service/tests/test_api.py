from fastapi.testclient import TestClient
from main import app
client=TestClient(app)
def test_health():
    assert client.get('/health').json()['status']=='ok'
def test_bounded_recommendations():
    for workload in ['CPU','IO','MIXED']:
        r=client.post('/predict',json={'workload':workload,'max_workers':2})
        assert r.status_code==200
        assert 1 <= r.json()['recommended_workers'] <= 2
        assert r.json()['recommended_policy'] in ['FIFO','PRIORITY','SJF']
def test_validation():
    assert client.post('/predict',json={'tasks':1001}).status_code==422
    assert client.post('/predict',json={'workload':'arbitrary code'}).status_code==422
