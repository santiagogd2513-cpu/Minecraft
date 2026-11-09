import json, urllib.request, urllib.error, base64, hashlib, struct, time
def b64d(s): s = s.replace('-', '+').replace('_', '/'); return base64.b64decode(s + '=' * (-len(s) % 4))
def b64e(b): return base64.urlsafe_b64encode(b).decode().rstrip('=')
SEQ = [int(time.time())]
def call(cmd, hc=None, same=False):
    if not same: SEQ[0] += 1
    req = urllib.request.Request("https://g.api.mega.co.nz/cs?id=%d" % SEQ[0], data=json.dumps([cmd]).encode(), method="POST")
    req.add_header("Content-Type", "application/json"); req.add_header("User-Agent", "Boveda")
    if hc: req.add_header("X-Hashcash", hc)
    try:
        with urllib.request.urlopen(req, timeout=30) as r: return r.status, None, r.read()[:200]
    except urllib.error.HTTPError as e: return e.code, e.headers.get("X-Hashcash"), e.read()[:200]
def solve(h, raw=False, le_hash=False, inc_first=False):
    p = h.split(':'); e = int(p[1]); token = p[3].encode() if raw else b64d(p[3])
    th = (((e & 63) << 1) + 1) << ((e >> 6) * 7 + 3)
    buf = bytearray(4 + 262144 * len(token)); buf[4:] = token * 262144
    n = 1 if inc_first else 0
    while True:
        buf[0:4] = struct.pack('<I', n)
        hsh = hashlib.sha256(buf).digest()
        v = struct.unpack('<I' if le_hash else '>I', hsh[:4])[0]
        if v <= th: return "1:" + p[3] + ":" + b64e(bytes(buf[0:4]))
        n += 1
cmd = {"a": "us", "user": "prueba.boveda.test@example.com", "uh": "AAAAAAAAAAAAAAAAAAAAAA"}
for name, kw, same in [("misma id", {}, True), ("token en texto", {"raw": True}, False), ("hash little-endian", {"le_hash": True}, False)]:
    st, hc, body = call(cmd)
    if not hc:
        print(name, "sin desafío:", st, body); continue
    ans = solve(hc, **kw)
    print(name, "->", call(cmd, ans, same=same))
print("us0 de una cuenta que seguro existe (v2 trae sal):", call({"a": "us0", "user": "support@mega.nz"}))
