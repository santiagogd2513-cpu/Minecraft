#!/usr/bin/env python3
"""Comprueba SOLO la V7.2.0 (Spotifly)."""
import json,sys
from pathlib import Path
R=Path(__file__).resolve().parent.parent
A=R/"src/main/resources/assets/beautifuldecor"
J=R/"src/main/java/com/example/beautifuldecor"
bad=[]
def need(c,m):
    if not c: bad.append(m)
need("version = '7.2.0'" in (R/"build.gradle").read_text() and "mod_version=7.2.0" in (R/"gradle.properties").read_text(),"versión")
need("V7.2.0" in (R/"README.md").read_text() and (R/"NUEVOS_BLOQUES.txt").read_text().startswith("PARCHE V7.2.0"),"docs")
need('"spotifly"' in (J/"ad/AdBrands.java").read_text(),"AdBrands")
for f in ("ads/spotifly.mp4","textures/ads/poster_spotifly.png","sounds/spotifly_song_1.ogg","sounds/spotifly_song_2.ogg","sounds/spotifly_song_3.ogg",
          "textures/block/painting_spotifly.png","textures/block/radiola_front.png","textures/block/radiola_front_on.png","textures/item/spotifly_headphones.png",
          "models/block/radiola.json","models/block/radiola_on.json","models/item/radiola.json","models/item/spotifly_headphones.json","blockstates/radiola.json","blockstates/painting_spotifly.json"):
    need((A/f).exists() and (A/f).stat().st_size>0,f)
for f in ("loot_tables/blocks/radiola.json","loot_tables/blocks/painting_spotifly.json"):
    need((R/"src/main/resources/data/beautifuldecor"/f).exists(),f)
sj=json.loads((A/"sounds.json").read_text())
for i in (1,2,3): need(f"spotifly_song_{i}" in sj,f"sounds.json {i}")
bs=json.loads((A/"blockstates/radiola.json").read_text())
need(len(bs["variants"])==16,"estados de la radiola")
for l in ("es_es","en_us"):
    j=json.loads((A/f"lang/{l}.json").read_text())
    for k in ("block.beautifuldecor.radiola","block.beautifuldecor.painting_spotifly","item.beautifuldecor.spotifly_headphones"):
        need(k in j,l+k)
for f in ("block/RadiolaBlock.java","item/SpotiflyHeadphonesItem.java","init/ModBlocks.java","init/ModItems.java","init/ModSounds.java"):
    s=(J/f).read_text(); need(s.count("{")==s.count("}"),f+" llaves")
need("RADIOLA" in (J/"init/ModBlocks.java").read_text() and "SPOTIFLY_HEADPHONES" in (J/"init/ModItems.java").read_text(),"registro")
if bad: print("FALLÓ:",bad); sys.exit(1)
print("OK V7.2.0")
