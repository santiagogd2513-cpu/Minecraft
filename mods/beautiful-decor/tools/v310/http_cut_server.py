#!/usr/bin/env python3
"""Servidor HTTP de prueba que CORTA las descargas a medias, como el que hacía morir los videos de los televisores.

Uso: http_cut_server.py <carpeta> <puerto>   (sirve los .mp4 de la carpeta; imprime READY cuando está listo)
Rutas:  /full/<archivo>       entrega todo.
        /cut/<archivo>        con Content-Length, corta cada respuesta tras un tercio del total; entiende "Range" (206 + Content-Range).
        /cutnorange/<archivo> con Content-Length, siempre corta al 40 % y no entiende "Range" (siempre 200 desde el principio).
        /chunk/<archivo>      sin Content-Length (la respuesta termina al cerrar), corta cada respuesta tras un tercio; entiende "Range".
        /chunknorange/<archivo> sin Content-Length, siempre corta al 40 % y no entiende "Range".
        /trunc/<archivo>      el ARCHIVO está cortado en el servidor (solo hay el 30 %): entrega ese 30 % completo y con su Content-Length; entiende "Range".
        /trunc200/<archivo>   igual, pero ignora "Range" (siempre 200 desde el principio).
        /nocache/<archivo>    una "caché" con solo el 30 %: lo entrega a un pedido normal, pero con "Pragma: no-cache" o "Cache-Control: no-cache" entrega todo.
        /range0/<archivo>     a un pedido SIN Range le entrega solo el 30 % (con su Content-Length, como si fuera el archivo entero); con Range, todo.
"""
import os
import re
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = sys.argv[1]


class H(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def do_GET(self):
        mode, _, name = self.path.lstrip("/").partition("/")
        path = os.path.join(ROOT, os.path.basename(name))
        if not os.path.isfile(path):
            self.send_error(404)
            return
        data = open(path, "rb").read()
        if mode in ("trunc", "trunc200"):
            data = data[: int(len(data) * 0.3)]
        total = len(data)
        start = 0
        partial = False
        m = re.match(r"bytes=(\d+)-", self.headers.get("Range", ""))
        nocache = "no-cache" in (self.headers.get("Pragma", "") + self.headers.get("Cache-Control", ""))
        if mode == "nocache" and not nocache:
            data = data[: int(len(data) * 0.3)]
            total = len(data)
        if mode == "range0" and not m:
            data = data[: int(len(data) * 0.3)]
            total = len(data)
        if m and mode in ("cut", "chunk", "trunc", "range0", "nocache"):
            start = int(m.group(1))
            if start >= total:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{total}")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            partial = True
        body = data[start:]
        if mode in ("full", "trunc", "trunc200", "range0", "nocache"):
            send = body
        elif mode in ("cutnorange", "chunknorange"):
            send = data[: int(total * 0.4)]
            partial = False
            body = data
        else:
            send = body[: max(1, total // 3)]
        self.send_response(206 if partial else 200)
        self.send_header("Content-Type", "video/mp4")
        if partial:
            self.send_header("Content-Range", f"bytes {start}-{total - 1}/{total}")
        if mode not in ("chunk", "chunknorange"):
            self.send_header("Content-Length", str(len(body)))     # promete todo y entrega menos (si corta)
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(send)
        self.wfile.flush()
        self.close_connection = True


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("127.0.0.1", int(sys.argv[2])), H)
    print("READY", flush=True)
    srv.serve_forever()
