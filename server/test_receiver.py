#!/usr/bin/env python3
"""Tiny test endpoint for [database] type = http. Stores responses in survey.db (SQLite).

Run:  python3 test_receiver.py 8080 [api_key]
INI:  url = http://<this-pc-ip>:8080/api/survey
Standard library only. For testing - put a real API in front of your production database.
"""
import json, sqlite3, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
API_KEY = sys.argv[2] if len(sys.argv) > 2 else ""
db = sqlite3.connect("survey.db", check_same_thread=False)
db.execute("""CREATE TABLE IF NOT EXISTS responses (response_id TEXT PRIMARY KEY, rating INT,
              created_utc TEXT, created_local TEXT, site_id TEXT, device_id TEXT)""")

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        if API_KEY and self.headers.get("X-Api-Key") != API_KEY:
            self.send_response(401); self.end_headers(); return
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        rows = [(r["response_id"], int(r["rating"]), r["created_utc"], r["created_local"],
                 r["site_id"], r["device_id"]) for r in body.get("responses", [])
                if 1 <= int(r["rating"]) <= 5]
        db.executemany("INSERT OR IGNORE INTO responses VALUES (?,?,?,?,?,?)", rows); db.commit()
        print(f"stored {len(rows)} response(s)")
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.end_headers()
        self.wfile.write(json.dumps({"stored": len(rows)}).encode())

print(f"Listening on :{PORT}")
ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
