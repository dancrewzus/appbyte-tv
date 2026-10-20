#!/usr/bin/env python3
"""A tiny stand-in for the optional home hub (docs/HUB_API.md): serves made-up data so you can try every home feature without any smart-home setup.

    python3 tools/mock-hub/server.py            # listens on 0.0.0.0:8190
    # then build the launcher with appbyte.hubUrl=http://<your-pc-ip>:8190

Standard library only. Lights toggle in memory when the TV sends actions."""
import json, struct, sys, time, zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

STATE = {"lights": {"lampara": "on", "comedor": "off", "bar": "off", "habitacion": "off"}, "movie": False, "away": False, "robot": "docked"}
LIBRARY = [{"id": "%032x" % (i + 1), "title": t, "sub": s, "progress": p, "image": "%032x" % (i + 1)} for i, (t, s, p) in enumerate(
    [("Dune: Part Two", "2024", 0.62), ("The Bear", "T2E5 · Pilot", 0.3), ("Planet Earth III", "T1E2", 0.8), ("Live concert", "2023", 0.15)])]


def png(w, h, a, b):
    """A w x h gradient PNG with no dependencies (the TV decodes it like any picture)."""
    rows = b"".join(b"\x00" + b"".join(bytes([int(a[k] + (b[k] - a[k]) * (x / w + y / h) / 2) for k in range(3)]) for x in range(w)) for y in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


class H(BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json"):
        data = body if isinstance(body, bytes) else json.dumps(body).encode()
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)

    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query)
        if u.path == "/api/tv/home":
            self._send(200, {"weather": {"state": "partlycloudy", "temp": 19.0}, "air": {"temp": 21.4, "hum": 48.0, "co2": 612.0, "pm25": 6.0}, "people": [{"name": "Ana", "present": True}],
                             "door": {"open": False}, "cams": {"up": 4, "total": 4}, "office": {"effective": "off", "live": False}, "attention": [],
                             "mimir": {"away": STATE["away"], "vacation": False, "movie": STATE["movie"], "lights": STATE["lights"], "tv": {}, "robot": {"state": STATE["robot"], "battery": 100}}})
        elif u.path == "/api/tv/watch": self._send(200, {"title": "Continuar viendo", "items": LIBRARY})
        elif u.path.startswith("/api/tv/watch/img/"):
            i = int(u.path.rsplit("/", 1)[1], 16) % 4; cols = [((61, 139, 255), (91, 63, 224)), ((255, 158, 107), (212, 46, 139)), ((33, 199, 181), (30, 91, 196)), ((176, 63, 168), (91, 63, 224))]
            self._send(200, png(224, 126, *cols[i]), "image/png")
        elif u.path == "/api/tv/search":
            term = (q.get("q") or [""])[0].lower(); self._send(200, {"items": [{"id": m["id"], "title": m["title"], "sub": m["sub"], "type": "Movie"} for m in LIBRARY if term and term in m["title"].lower()]})
        elif u.path == "/api/tv/avatar": self._send(404, {"detail": "no picture chosen"})
        elif u.path == "/": self._send(200, b"<html><body style='font-family:sans-serif;background:#111;color:#eee'><h1>Mock hub</h1><pre>" + json.dumps(STATE, indent=1).encode() + b"</pre></body></html>", "text/html")
        else: self._send(404, {"detail": "not found"})

    def do_POST(self):
        if urlparse(self.path).path != "/api/entrada/action": return self._send(404, {"detail": "not found"})
        a = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}").get("a", "")
        print(time.strftime("%H:%M:%S"), "action:", a, flush=True)
        if a.startswith("toggle:") and a[7:] in STATE["lights"]: STATE["lights"][a[7:]] = "off" if STATE["lights"][a[7:]] == "on" else "on"
        elif a == "toggle:robot": STATE["robot"] = "cleaning" if STATE["robot"] == "docked" else "docked"
        elif a in ("cine_on", "cine_off"): STATE["movie"] = a == "cine_on"
        elif a in ("dormir", "apagar_todo"): STATE["lights"] = {k: "off" for k in STATE["lights"]}
        self._send(200, {"ok": True})

    def log_message(self, *a): pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8190
    print("mock hub on :%d (Ctrl+C to stop)" % port); ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
