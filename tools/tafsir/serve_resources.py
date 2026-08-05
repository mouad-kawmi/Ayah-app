#!/usr/bin/env python3
"""Phase 2 test server: local HTTP server with Range support for downloads.

Serves the tools/tafsir/site directory (catalog + resource databases) on port
8080. The debug app reaches it via http://10.0.2.2:8080/ (BuildConfig
RESOURCE_BASE_URL).

Range support is required for the resume tests: the app downloads with
`Range: bytes=<start>-` headers and expects 206 responses. Optional throttling
(--throttle-kb) makes progress observable in the UI; optional start-slow
(--slow-start) delays the first response to exercise cancellation.

Usage:
    python tools/tafsir/serve_resources.py [--port 8080] [--throttle-kb 256] [--slow-start]
    python tools/tafsir/serve_resources.py --site tools/tafsir/production/site [--port 8080]
"""

import argparse
import os
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.abspath(__file__))
SITE_DIR = os.path.join(ROOT, "site")

THROTTLE_BYTES_PER_SECOND = None
SLOW_START_SECONDS = 0.0
LOG_LOCK = threading.Lock()


class RangeHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        with LOG_LOCK:
            sys.stderr.write(f"[{time.strftime('%H:%M:%S')}] {self.address_string()} {fmt % args}\n")
            sys.stderr.flush()

    def do_GET(self):
        path = self.path.split("?")[0]
        target = os.path.normpath(os.path.join(SITE_DIR, path.lstrip("/")))
        if not target.startswith(os.path.normpath(SITE_DIR)):
            self.send_error(403, "Forbidden")
            return
        if not os.path.isfile(target):
            self.send_error(404, "Not found")
            return

        file_size = os.path.getsize(target)
        range_header = self.headers.get("Range")

        if SLOW_START_SECONDS > 0:
            time.sleep(SLOW_START_SECONDS)

        start = 0
        end = file_size - 1
        status = 200

        if range_header:
            try:
                spec = range_header.strip().split("=")[1]
                if spec.endswith("-"):
                    start = int(spec[:-1])
                else:
                    start_text, end_text = spec.split("-", 1)
                    start = int(start_text) if start_text else 0
                    end = int(end_text) if end_text else file_size - 1
                if start >= file_size:
                    self.send_response(416)
                    self.send_header("Content-Range", f"bytes */{file_size}")
                    self.end_headers()
                    return
                end = min(end, file_size - 1)
                status = 206
            except (ValueError, IndexError):
                start = 0
                end = file_size - 1
                status = 200

        length = end - start + 1
        self.send_response(status)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if status == 206:
            self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
        self.end_headers()

        with open(target, "rb") as handle:
            handle.seek(start)
            remaining = length
            while remaining > 0:
                chunk = handle.read(min(65536, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)
                if THROTTLE_BYTES_PER_SECOND:
                    time.sleep(len(chunk) / THROTTLE_BYTES_PER_SECOND)
        self.wfile.flush()

    def do_HEAD(self):
        target = os.path.normpath(os.path.join(SITE_DIR, self.path.lstrip("/")))
        if not os.path.isfile(target):
            self.send_error(404, "Not found")
            return
        self.send_response(200)
        self.send_header("Content-Length", str(os.path.getsize(target)))
        self.end_headers()


def main() -> int:
    global THROTTLE_BYTES_PER_SECOND, SLOW_START_SECONDS, SITE_DIR
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--throttle-kb", type=int, default=0, help="bytes per second cap (KB/s)")
    parser.add_argument("--slow-start", type=float, default=0.0, help="delay before responding (s)")
    parser.add_argument("--site", default=SITE_DIR, help="directory to serve (default: tools/tafsir/site)")
    args = parser.parse_args()

    THROTTLE_BYTES_PER_SECOND = args.throttle_kb * 1024 if args.throttle_kb > 0 else None
    SLOW_START_SECONDS = args.slow_start
    SITE_DIR = os.path.abspath(args.site)

    if not os.path.isdir(SITE_DIR):
        print(f"ERROR: site directory missing: {SITE_DIR} (run build_test_tafsir_db.py first)", file=sys.stderr)
        return 1

    server = ThreadingHTTPServer(("0.0.0.0", args.port), RangeHandler)
    host_ip = socket.gethostbyname(socket.gethostname())
    print(f"Serving {SITE_DIR} on port {args.port} (LAN {host_ip}:{args.port}, emulator 10.0.2.2:{args.port})")
    print("Range: enabled  Throttle:", THROTTLE_BYTES_PER_SECOND, " Slow-start:", SLOW_START_SECONDS)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
