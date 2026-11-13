#!/usr/bin/env python3
"""Comprueba SOLO la V6.16.6 (arranque en la parada)."""
import re,subprocess,sys,tempfile,os
from pathlib import Path
R=Path(__file__).resolve().parent.parent
rd=lambda p:(R/p).read_text(encoding="utf-8")
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '6.16.6'" in rd("build.gradle") and "mod_version=6.16.6" in rd("gradle.properties"),"versión")
need("V6.16.6" in rd("README.md") and rd("NUEVOS_BLOQUES.txt").startswith("PARCHE V6.16.6"),"docs")
t=rd("src/main/java/com/example/beautifuldecor/entity/TrainRoute.java")
need("this.traveled >= MIN_TRAVEL || this.from < 0" in t,"llegada inicial")
need("!haveTarget && !this.hasDist" in t,"regla sin vía")
need(t.count("{")==t.count("}"),"llaves")
if bad: print("FALLÓ:",bad); sys.exit(1)
print("OK V6.16.6")
