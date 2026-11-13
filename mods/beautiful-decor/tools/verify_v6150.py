#!/usr/bin/env python3
"""Comprueba SOLO la V6.15.0 (lotería)."""
import json, os, re, shutil, subprocess, sys, tempfile
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
J = ROOT / "src/main/java/com/example/beautifuldecor"
A = ROOT / "src/main/resources/assets/beautifuldecor"
errors = []
def need(c, m):
    if not c:
        errors.append(m)
def read(p): return Path(p).read_text(encoding="utf-8")
def run(cmd, **kw):
    env = {k: v for k, v in os.environ.items() if k != "JAVA_TOOL_OPTIONS"}
    return subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", env=env, **kw)

need("version = '6.15.0'" in read(ROOT / "build.gradle") and "mod_version=6.15.0" in read(ROOT / "gradle.properties"), "versión 6.15.0")
need("V6.15.0" in read(ROOT / "README.md") and read(ROOT / "NUEVOS_BLOQUES.txt").startswith("PARCHE V6.15.0"), "docs")
need('PROTOCOL = "31"' in read(J / "network/ModMessages.java") and "LotteryPacket.class" in read(J / "network/ModMessages.java"), "protocolo 31 y paquete")
need('"loteria"' in read(J / "ad/AdBrands.java"), "marca loteria en AdBrands")
need((A / "ads/loteria.mp4").stat().st_size > 50000 and (A / "textures/ads/poster_loteria.png").stat().st_size > 1000, "anuncio y póster")
for n in ("lottery_booth", "cash_register", "cash_register_open", "painting_loteria"):
    json.loads(read(A / f"models/block/{n}.json"))
for n in ("lottery_booth", "cash_register", "painting_loteria"):
    json.loads(read(A / f"blockstates/{n}.json")); json.loads(read(ROOT / f"src/main/resources/data/beautifuldecor/loot_tables/blocks/{n}.json"))
    need((A / f"models/item/{n}.json").exists(), f"modelo de ítem {n}")
need((A / "textures/item/lottery_ticket.png").exists() and (A / "models/item/lottery_ticket.json").exists(), "boleto")
for t in ("lottery_booth_front", "lottery_booth_side", "lottery_booth_top", "cash_register_front", "cash_register_side", "cash_register_top", "cash_register_cash", "painting_loteria"):
    need((A / f"textures/block/{t}.png").exists(), f"textura {t}")
# toda textura que citan los modelos existe
for n in ("lottery_booth", "cash_register", "cash_register_open"):
    for v in json.loads(read(A / f"models/block/{n}.json"))["textures"].values():
        need((A / "textures" / (v.split(":")[1] + ".png")).exists(), f"{n}: falta {v}")
# todas las claves de idioma que usa el código de la lotería existen
keys = set()
for f in ("block/LotteryBoothBlock.java", "block/CashRegisterBlock.java", "lottery/Lottery.java", "lottery/PrizeTruckDriver.java", "client/LotteryScreenRenderer.java", "item/LotteryTicketItem.java"):
    keys |= set(re.findall(r'translatable\("([a-z_.0-9]+)"', read(J / f)))
for lang in ("es_es.json", "en_us.json"):
    d = json.loads(read(A / "lang" / lang))
    for k in keys:
        need(k in d or any(x.startswith(k) for x in d), f"{lang}: falta {k}")
    for k in ("news.lottery_tier.1", "news.lottery_tier.3", "message.beautifuldecor.lottery_won.2", "message.beautifuldecor.lottery_ticket_won.3"):
        need(k in d, f"{lang}: falta {k}")
need("lottery_ticket" in read(J / "init/ModCreativeTabs.java"), "boleto fuera de las pestañas")
dt = read(J / "entity/DeliveryTruckEntity.java")
need("PrizeTruckDriver.drive(this)" in dt and "canAddPassenger" in dt and "PrizeTier" in dt, "camión de premios")
ap = read(J / "client/AdPlayback.java")
need("ADS_PER_LOTTERY = 5" in ap and "s.lottery = true" in ap and "LotteryClient.reset()" in ap, "AdPlayback con la lotería")
need("LotteryScreenRenderer.draw" in read(J / "client/AdScreenRenderer.java"), "AdScreenRenderer")
ex = read(J / "entity/VehiclePaintExtras.java")
need(ex.count('case "') >= 29 and ex.count("freight_loco") >= 2, "VehiclePaintExtras perdió entradas")
javac = shutil.which("javac")
work = Path(tempfile.mkdtemp(prefix="v6150_"))
try:
    if javac:
        names = ("lottery/LotteryData.java", "lottery/Lottery.java", "lottery/PrizeTruckDriver.java", "network/LotteryPacket.java", "client/LotteryClient.java", "client/LotteryScreenRenderer.java",
                 "block/LotteryBoothBlock.java", "block/CashRegisterBlock.java", "item/LotteryTicketItem.java", "entity/DeliveryTruckEntity.java", "client/AdPlayback.java",
                 "client/AdScreenRenderer.java", "client/NewsScreenRenderer.java", "init/ModBlocks.java", "init/ModItems.java", "init/ModCreativeTabs.java", "network/ModMessages.java")
        files = [J / f for f in names]
        r = run([javac, "-proc:none", "-encoding", "UTF-8", "-Xmaxerrs", "5000", "-d", str(work / "syn")] + [str(f) for f in files], cwd=work)
        ignore = ("cannot find symbol", "does not exist", "does not override or implement a method from a supertype", "package com.", "package net.", "package org.", "cannot access",
                  "incompatible types", "cannot be applied", "no suitable", "cannot infer", "cannot be dereferenced", "constructor", "bad operand", "invalid method reference",
                  "has private access", "cannot be referenced", "static import only", "unreported exception", "not a functional interface", "lambda body", "incomparable types",
                  "cannot return", "is not abstract and does not override", "cannot be inherited", "method does not override", "unexpected type", "cannot be converted")
        bad = [l for l in r.stderr.splitlines() if ": error:" in l and not any(k in l for k in ignore)]
        need(not bad, "errores de sintaxis:\n" + "\n".join(bad[:10]))
finally:
    shutil.rmtree(work, ignore_errors=True)
if errors:
    print("FALLÓ la verificación V6.15.0:")
    [print(" -", x) for x in errors]
    sys.exit(1)
print("OK V6.15.0: lotería; solo se verificó esta versión.")
