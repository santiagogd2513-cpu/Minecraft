#!/usr/bin/env python3
"""Servidor local para probar el navegador: sirve la carpeta site/ y responde /echo a GET y POST."""
import http.server
import json
import os
import sys
import time
import urllib.parse

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "site")


class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=ROOT, **k)

    def log_message(self, *a):
        pass

    def _echo(self, body):
        u = urllib.parse.urlparse(self.path)
        html = ("<html><head><title>echo</title></head><body><h1>echo %s</h1><p id=q>query=%s</p><p id=b>body=%s</p>"
                "<p id=c>cookie=%s</p><p id=t>type=%s</p></body></html>") % (
            self.command, u.query, body, self.headers.get("Cookie", ""), self.headers.get("Content-Type", "").split(";")[0])
        data = html.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Set-Cookie", "seen=1; Path=/")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _json(self, obj, status=200, extra=None):
        data = json.dumps(obj).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("X-Test", "uno")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def _api(self):
        u = urllib.parse.urlparse(self.path)
        q = urllib.parse.parse_qs(u.query)
        n = int(self.headers.get("Content-Length", "0") or 0)
        body = self.rfile.read(n).decode("utf-8", "replace") if n else ""
        if u.path == "/api/json":
            return self._json({"msg": "hola", "n": [1, 2, 3]})
        if u.path == "/api/echo":
            return self._json({"method": self.command, "query": u.query, "body": body,
                               "type": self.headers.get("Content-Type", "").split(";")[0],
                               "accept": self.headers.get("Accept", ""), "xtest": self.headers.get("X-Test", ""),
                               "cookie": self.headers.get("Cookie", "")})
        if u.path == "/api/slow":
            time.sleep(int(q.get("ms", ["300"])[0]) / 1000.0)
            return self._json({"slow": True})
        if u.path == "/api/cookie":
            return self._json({"set": True}, extra={"Set-Cookie": "fx=1; Path=/"})
        if u.path == "/api/redirect":
            self.send_response(302)
            self.send_header("Location", "/api/json")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        if u.path == "/api/missing":
            return self._json({"error": "no"}, status=404)
        self._json({"error": "unknown"}, status=404)

    def do_GET(self):
        if self.path.startswith("/api/"):
            return self._api()
        if self.path.startswith("/echo"):
            return self._echo("")
        return super().do_GET()

    def do_HEAD(self):
        if self.path.startswith("/api/"):
            return self._api()
        return super().do_HEAD()

    def do_PUT(self):
        return self._api()

    def do_DELETE(self):
        return self._api()

    def do_PATCH(self):
        return self._api()

    def do_POST(self):
        if self.path.startswith("/api/"):
            return self._api()
        n = int(self.headers.get("Content-Length", "0") or 0)
        body = self.rfile.read(n).decode("utf-8", "replace")
        if self.path.startswith("/redirect"):
            self.send_response(303)
            self.send_header("Location", "/echo?from=redirect")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        return self._echo(body)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    http.server.ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
