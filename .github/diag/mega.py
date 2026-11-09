import json, urllib.request, urllib.error, base64, hashlib, struct, time
def b64d(s): s = s.replace('-', '+').replace('_', '/'); return base64.b64decode(s + '=' * (-len(s) % 4))
def b64e(b): return base64.urlsafe_b64encode(b).decode().rstrip('=')
def call(cmd, hc=None):
    req = urllib.request.Request("https://g.api.mega.co.nz/cs?id=%d" % int(time.time()), data=json.dumps([cmd]).encode(), method="POST")
    req.add_header("Content-Type", "application/json"); req.add_header("User-Agent", "Boveda")
    if hc: req.add_header("X-Hashcash", hc)
    try:
        with urllib.request.urlopen(req, timeout=30) as r: return r.status, None, r.read()[:200]
    except urllib.error.HTTPError as e: return e.code, e.headers.get("X-Hashcash"), e.read()[:200]
def solve(h, order):
    p = h.split(':'); e = int(p[1]); token = b64d(p[3])
    th = (((e & 63) << 1) + 1) << ((e >> 6) * 7 + 3)
    buf = bytearray(4 + 262144 * len(token))
    buf[4:] = token * 262144
    n = 0; t0 = time.time()
    while True:
        buf[0:4] = struct.pack(order, n)
        hsh = hashlib.sha256(buf).digest()
        if struct.unpack('>I', hsh[:4])[0] <= th:
            print("    resuelto en", n + 1, "intentos,", round(time.time() - t0, 1), "s")
            return "1:" + p[3] + ":" + b64e(bytes(buf[0:4]))
        n += 1
cmd = {"a": "us", "user": "prueba.boveda.test@example.com", "uh": "AAAAAAAAAAAAAAAAAAAAAA"}
for order in ['>I', '<I']:
    st, hc, body = call(cmd)
    print("primer intento:", st, hc)
    if hc:
        ans = solve(hc, order)
        print("  con prefijo", order, "->", call(cmd, ans))
