import json, urllib.request, urllib.error
def call(cmd, ua="Boveda", extra=None):
    req = urllib.request.Request("https://g.api.mega.co.nz/cs?id=12345", data=json.dumps([cmd]).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    if ua: req.add_header("User-Agent", ua)
    for k, v in (extra or {}).items(): req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            print("  ", r.status, dict(r.headers).get("X-Hashcash"), r.read()[:300])
    except urllib.error.HTTPError as e:
        print("  HTTP", e.code, "X-Hashcash:", e.headers.get("X-Hashcash"), e.read()[:300])
for ua in ["Boveda", None, "Mozilla/5.0"]:
    print("us0 con UA", ua); call({"a": "us0", "user": "prueba.boveda.test@example.com"}, ua)
print("us con datos falsos"); call({"a": "us", "user": "prueba.boveda.test@example.com", "uh": "AAAAAAAAAAAAAAAAAAAAAA"})
