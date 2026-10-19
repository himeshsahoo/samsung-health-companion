import os
import json
from datetime import datetime
from fastapi import FastAPI, Request, Header, HTTPException
import uvicorn
import threading
import asyncio
from mcp.server.mcpserver import MCPServer
import mcp.server.stdio

# 1. MCP Server Setup (mcp 2.x API)
app_mcp = MCPServer("SamsungHealth")
DATA_FILE = "latest_health_data.json"

def get_stored_data():
    if os.path.exists(DATA_FILE):
        try:
            with open(DATA_FILE, "r") as f:
                return json.load(f)
        except:
            return {}
    return {}

@app_mcp.tool()
async def get_live_vitals() -> str:
    """Returns the most recent health metrics synced from the phone."""
    data = get_stored_data()
    if not data:
        return "No data synced yet. Please sync from the Android app."
    return json.dumps(data, indent=2)

@app_mcp.tool()
async def get_health_summary() -> str:
    """Provides a human-readable summary of current fitness status."""
    data = get_stored_data()
    if not data: return "No data."
    
    summary = data.get("summary", {})
    text = f"Latest Heart Rate: {summary.get('hr_latest', 'N/A')} BPM\n" \
           f"Energy Score: {summary.get('energy_score', 'N/A')}\n" \
           f"Sleep Score: {summary.get('sleep_score', 'N/A')}\n" \
           f"Last Sync: {data.get('timestamp', 'N/A')}"
    return text

# Configuration
AUTH_TOKEN = os.environ.get("SYNC_AUTH_TOKEN", "your_secret_token_here")
RECEIVER_PORT = int(os.environ.get("RECEIVER_PORT", "8080"))
RECEIVER_HOST = os.environ.get("RECEIVER_HOST", "0.0.0.0")

# 2. Data Receiver (FastAPI)
app_api = FastAPI(title="Samsung Health MCP Receiver")

@app_api.post("/sync")
async def receive_sync(request: Request, authorization: str = Header(None)):
    expected_header = f"Bearer {AUTH_TOKEN}"
    if not authorization or authorization != expected_header:
        raise HTTPException(status_code=401, detail="Unauthorized: Invalid or missing authorization token")
    
    payload = await request.json()
    with open(DATA_FILE, "w") as f:
        json.dump(payload, f)
    
    print(f"[{datetime.now()}] Received live sync from phone!")
    return {"status": "success"}

def run_receiver():
    # Run the HTTP server to receive data from phone
    uvicorn.run(app_api, host=RECEIVER_HOST, port=RECEIVER_PORT, log_level="warning")

async def main():
    # Start the HTTP receiver in a background thread
    threading.Thread(target=run_receiver, daemon=True).start()
    
    # Run the MCP server over stdio
    await app_mcp.run_stdio_async()

if __name__ == "__main__":
    asyncio.run(main())
