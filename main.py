"""Start the Python scheduling advisor. Run the Java backend separately."""
import subprocess
import sys
from pathlib import Path
if __name__ == "__main__":
    raise SystemExit(subprocess.call([sys.executable,"-m","uvicorn","main:app","--host","127.0.0.1","--port","8000"],cwd=Path(__file__).parent/"ai-service"))
