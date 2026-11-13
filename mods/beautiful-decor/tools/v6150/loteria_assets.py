"""V6.15.0: puesto de lotería, caja registradora, boleto, cartel Lotto Gordo y textos (lang). Uso: python3 -m tools.v6150.loteria_assets"""
import json
from pathlib import Path
from PIL import Image, ImageDraw
from ..v210.vcommon import FONT
from ..v410.adkit import Cv
from .loteria import logo_loteria

ROOT = Path(__file__).resolve().parents[2]
A = ROOT / "src/main/resources/assets/beautifuldecor"
D = ROOT / "src/main/resources/data/beautifuldecor"
GREEN, DGREEN, GOLD, RED, WHITE = (10, 120, 62, 255), (6, 80, 40, 255), (250, 200, 30, 255), (225, 40, 50, 255), (245, 245, 248, 255)
GREY, DGREY, LGREY = (120, 124, 134, 255), (70, 74, 84, 255), (190, 194, 204, 255)


def txt(d, s, x, y, col, sc=1):
    for ch in s.upper():
        for ry, row in enumerate(FONT.get(ch, FONT[" "]).split()):
            for rx, bit in enumerate(row):
                if bit == "1":
                    d.rectangle([x + rx * sc, y + ry * sc, x + rx * sc + sc - 1, y + ry * sc + sc - 1], fill=col)
        x += 4 * sc


def save(im, name):
    im.save(A / "textures/block" / (name + ".png"))


def booth_textures():
    f = Image.new("RGBA", (32, 32), DGREEN)
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 31, 7], fill=GOLD)
    txt(d, "LOTTO", 4, 1, (60, 40, 0, 255), 1)
    txt(d, "GORDO", 18 - 12 + 12, 1, (60, 40, 0, 255), 1) if False else None
    d.rectangle([3, 10, 28, 21], fill=(30, 40, 50, 255), outline=(200, 205, 215, 255))        # ventanilla
    d.rectangle([4, 11, 27, 20], fill=(70, 120, 150, 255))
    d.rectangle([4, 17, 27, 20], fill=(60, 100, 130, 255))
    d.rectangle([2, 22, 29, 24], fill=(150, 110, 70, 255))                                       # mostrador
    d.rectangle([2, 22, 29, 22], fill=(190, 150, 100, 255))
    for i in range(4):
        d.ellipse([5 + i * 6, 26, 9 + i * 6, 30], fill=WHITE, outline=GREY)                      # bolas pintadas
        d.point((7 + i * 6, 28), fill=RED)
    save(f, "lottery_booth_front")
    s = Image.new("RGBA", (32, 32), GREEN)
    d = ImageDraw.Draw(s)
    for y in range(0, 32, 8):
        d.rectangle([0, y, 31, y], fill=DGREEN)
    d.rectangle([0, 0, 31, 3], fill=GOLD)
    d.ellipse([10, 10, 21, 21], fill=WHITE, outline=GOLD)
    txt(d, "7", 14, 13, RED, 1)
    save(s, "lottery_booth_side")
    t = Image.new("RGBA", (32, 32), GOLD)
    d = ImageDraw.Draw(t)
    for x in range(0, 32, 8):
        d.rectangle([x, 0, x + 3, 31], fill=RED)
    d.rectangle([0, 0, 31, 31], outline=(120, 80, 0, 255))
    save(t, "lottery_booth_top")


def register_textures():
    f = Image.new("RGBA", (32, 32), LGREY)
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 31, 31], outline=DGREY)
    d.rectangle([3, 3, 28, 9], fill=(40, 60, 40, 255), outline=DGREY)                              # pantallita
    txt(d, "0 00", 9, 4, (120, 255, 140, 255), 1)
    for r in range(2):
        for c in range(5):
            d.rectangle([4 + c * 5, 12 + r * 5, 7 + c * 5, 15 + r * 5], fill=WHITE if (r + c) % 3 else GOLD, outline=GREY)
    d.rectangle([3, 24, 28, 29], fill=DGREY, outline=(30, 30, 36, 255))                           # cajón
    d.rectangle([13, 26, 18, 27], fill=LGREY)
    save(f, "cash_register_front")
    s = Image.new("RGBA", (32, 32), GREY)
    d = ImageDraw.Draw(s)
    d.rectangle([0, 0, 31, 31], outline=DGREY)
    d.rectangle([0, 0, 31, 4], fill=LGREY)
    save(s, "cash_register_side")
    t = Image.new("RGBA", (32, 32), LGREY)
    ImageDraw.Draw(t).rectangle([0, 0, 31, 31], outline=DGREY)
    save(t, "cash_register_top")
    dr = Image.new("RGBA", (32, 32), (60, 140, 70, 255))                                           # billetes dentro del cajón abierto
    d = ImageDraw.Draw(dr)
    for y in range(2, 30, 6):
        d.rectangle([2, y, 29, y + 3], fill=(90, 190, 100, 255), outline=(30, 90, 40, 255))
    save(dr, "cash_register_cash")


def ticket_icon():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 4, 14, 12], fill=GOLD, outline=(120, 80, 0, 255))
    d.rectangle([2, 5, 13, 6], fill=RED)
    for i in range(3):
        d.rectangle([3 + i * 4, 8, 5 + i * 4, 10], fill=WHITE, outline=(120, 80, 0, 255))
    d.point((0, 8), fill=(0, 0, 0, 0)); d.point((15, 8), fill=(0, 0, 0, 0))
    im.save(A / "textures/item/lottery_ticket.png")
    (A / "models/item/lottery_ticket.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "beautifuldecor:item/lottery_ticket"}}, indent=2), encoding="utf-8")


def faces(front, side, top, uv=(0, 0, 16, 16)):
    f = lambda t: {"uv": list(uv), "texture": "#" + t}
    return {"north": f(front), "south": f(side), "east": f(side), "west": f(side), "up": f(top), "down": f(top)}


def el(a, b, front, side, top):
    return {"from": a, "to": b, "faces": faces(front, side, top)}


def models():
    booth = {"textures": {"front": "beautifuldecor:block/lottery_booth_front", "side": "beautifuldecor:block/lottery_booth_side",
                          "top": "beautifuldecor:block/lottery_booth_top", "particle": "beautifuldecor:block/lottery_booth_side"},
             "elements": [el([0, 0, 1], [16, 14, 15], "front", "side", "side"), el([-1, 14, 0], [17, 16, 16], "top", "top", "top")]}
    reg = {"textures": {"front": "beautifuldecor:block/cash_register_front", "side": "beautifuldecor:block/cash_register_side",
                        "top": "beautifuldecor:block/cash_register_top", "cash": "beautifuldecor:block/cash_register_cash", "particle": "beautifuldecor:block/cash_register_side"},
           "elements": [el([1, 0, 2], [15, 7, 14], "front", "side", "top"), el([3, 7, 7], [13, 11, 13], "side", "side", "top")]}
    regopen = json.loads(json.dumps(reg))
    regopen["elements"].append({"from": [2, 3, -2], "to": [14, 6, 2], "faces": faces("cash", "side", "cash")})
    for name, m in (("lottery_booth", booth), ("cash_register", reg), ("cash_register_open", regopen)):
        (A / f"models/block/{name}.json").write_text(json.dumps(m, indent=2), encoding="utf-8")
    for name in ("lottery_booth", "cash_register"):
        (A / f"models/item/{name}.json").write_text(json.dumps({"parent": f"beautifuldecor:block/{name}"}, indent=2), encoding="utf-8")
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    bs = {"variants": {f"facing={k}": ({"model": "beautifuldecor:block/lottery_booth", "y": v} if v else {"model": "beautifuldecor:block/lottery_booth"}) for k, v in rot.items()}}
    (A / "blockstates/lottery_booth.json").write_text(json.dumps(bs, indent=2), encoding="utf-8")
    bs = {"variants": {}}
    for k, v in rot.items():
        for o in ("false", "true"):
            e = {"model": "beautifuldecor:block/cash_register" + ("_open" if o == "true" else "")}
            if v:
                e["y"] = v
            bs["variants"][f"facing={k},open={o}"] = e
    (A / "blockstates/cash_register.json").write_text(json.dumps(bs, indent=2), encoding="utf-8")
    for n in ("lottery_booth", "cash_register", "painting_loteria"):
        src = (D / "loot_tables/blocks/rojotoro_minibar.json").read_text(encoding="utf-8").replace("rojotoro_minibar", n)
        (D / f"loot_tables/blocks/{n}.json").write_text(src, encoding="utf-8")


def painting():
    cv = Cv()
    cv.gradient((10, 140, 70), (4, 60, 34), 0, 100)
    cv.rays(50, 50, 16, (250, 210, 60, 90), (10, 140, 70, 0), 0.3, 150)
    logo_loteria(cv, 50, 40, 26)
    fr = cv.done().convert("RGB")
    crop = fr.crop((0, 48, 384, 336)).resize((128, 96), Image.LANCZOS)
    ImageDraw.Draw(crop).rectangle([0, 0, 127, 95], outline=(20, 20, 30))
    crop.save(A / "textures/block/painting_loteria.png")
    crop.resize((32, 32), Image.LANCZOS).save(A / "textures/item/painting_loteria.png")
    src = "painting_valley_dawn"
    for rel in (f"models/block/{src}.json", f"blockstates/{src}.json", f"models/item/{src}.json"):
        (A / rel.replace(src, "painting_loteria")).write_text((A / rel).read_text(encoding="utf-8").replace(src, "painting_loteria"), encoding="utf-8")


def tags():
    p = ROOT / "src/main/resources/data/minecraft/tags/blocks/mineable/axe.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    for n in ("painting_loteria",):
        if "beautifuldecor:" + n not in d["values"]:
            d["values"].append("beautifuldecor:" + n)
    p.write_text(json.dumps(d, indent=2), encoding="utf-8")
    p = ROOT / "src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json"
    if p.exists():
        d = json.loads(p.read_text(encoding="utf-8"))
        for n in ("lottery_booth", "cash_register"):
            if "beautifuldecor:" + n not in d["values"]:
                d["values"].append("beautifuldecor:" + n)
        p.write_text(json.dumps(d, indent=2), encoding="utf-8")


ES = {
    "block.beautifuldecor.painting_loteria": "Cartel Lotto Gordo", "block.beautifuldecor.lottery_booth": "Puesto de lotería",
    "block.beautifuldecor.cash_register": "Caja registradora", "item.beautifuldecor.lottery_ticket": "Boleto de lotería",
    "item.beautifuldecor.lottery_ticket.info": "Número %s · Clase %s · Sorteo #%s",
    "message.beautifuldecor.lottery_booth_price": "Un boleto cuesta %s lingotes de hierro. Próximo sorteo en %s min",
    "message.beautifuldecor.lottery_booth_max": "Ya tienes %s boletos para este sorteo",
    "message.beautifuldecor.lottery_bought": "Boleto %s · Clase %s para el sorteo #%s",
    "message.beautifuldecor.cash_register": "¡Cling! Paga aquí. Con un boleto en la mano te digo si ganó",
    "message.beautifuldecor.lottery_ticket_waiting": "Este boleto es del sorteo #%s, que aún no sale",
    "message.beautifuldecor.lottery_ticket_old": "Este boleto es de un sorteo anterior",
    "message.beautifuldecor.lottery_ticket_lost": "Este boleto no ganó nada. ¡Suerte la próxima!",
    "message.beautifuldecor.lottery_ticket_won.1": "¡PREMIO GORDO! Tu camión viene en camino",
    "message.beautifuldecor.lottery_ticket_won.2": "¡Premio mediano! Tu camión viene en camino",
    "message.beautifuldecor.lottery_ticket_won.3": "¡Premio pequeño! Tu camión viene en camino",
    "message.beautifuldecor.lottery_won.1": "¡¡PREMIO GORDO!! Un camión AMAZOOM va hacia ti con 3 stacks de diamantes",
    "message.beautifuldecor.lottery_won.2": "¡Premio mediano! Un camión AMAZOOM va hacia ti con oro",
    "message.beautifuldecor.lottery_won.3": "¡Premio pequeño! Un camión AMAZOOM va hacia ti con hierro",
    "message.beautifuldecor.lottery_delivered": "¡Entrega lista! Tu premio está detrás del camión",
    "news.lottery_result": "Lotto Gordo, sorteo #%s: número %s, clase %s. %s",
    "news.lottery_nobody": "Nadie ganó esta vez",
    "news.lottery_tier.1": "¡Hay ganador del premio gordo!", "news.lottery_tier.2": "Hubo premio mediano", "news.lottery_tier.3": "Hubo premio pequeño",
    "lottery.title": "LOTTO GORDO", "lottery.draw": "Sorteo #%s", "lottery.last": "Último número:",
    "lottery.prize.1": "GORDO (número y clase): 3 stacks de diamantes", "lottery.prize.2": "MEDIANO (número): 96 lingotes de oro",
    "lottery.prize.3": "PEQUEÑO (2 últimas cifras): 32 de hierro", "lottery.next": "Próximo sorteo en %s min", "lottery.soon": "Próximo sorteo muy pronto",
    "lottery.buy": "Boletos en el puesto: 4 lingotes de hierro",
}
EN = {
    "block.beautifuldecor.painting_loteria": "Lotto Gordo Poster", "block.beautifuldecor.lottery_booth": "Lottery Booth",
    "block.beautifuldecor.cash_register": "Cash Register", "item.beautifuldecor.lottery_ticket": "Lottery Ticket",
    "item.beautifuldecor.lottery_ticket.info": "Number %s · Class %s · Draw #%s",
    "message.beautifuldecor.lottery_booth_price": "A ticket costs %s iron ingots. Next draw in %s min",
    "message.beautifuldecor.lottery_booth_max": "You already have %s tickets for this draw",
    "message.beautifuldecor.lottery_bought": "Ticket %s · Class %s for draw #%s",
    "message.beautifuldecor.cash_register": "Ka-ching! Pay here. Hold a ticket and I'll tell you if it won",
    "message.beautifuldecor.lottery_ticket_waiting": "This ticket is for draw #%s, which hasn't happened yet",
    "message.beautifuldecor.lottery_ticket_old": "This ticket is from an earlier draw",
    "message.beautifuldecor.lottery_ticket_lost": "This ticket won nothing. Better luck next time!",
    "message.beautifuldecor.lottery_ticket_won.1": "JACKPOT! Your truck is on its way",
    "message.beautifuldecor.lottery_ticket_won.2": "Medium prize! Your truck is on its way",
    "message.beautifuldecor.lottery_ticket_won.3": "Small prize! Your truck is on its way",
    "message.beautifuldecor.lottery_won.1": "JACKPOT!! An AMAZOOM truck is heading to you with 3 stacks of diamonds",
    "message.beautifuldecor.lottery_won.2": "Medium prize! An AMAZOOM truck is heading to you with gold",
    "message.beautifuldecor.lottery_won.3": "Small prize! An AMAZOOM truck is heading to you with iron",
    "message.beautifuldecor.lottery_delivered": "Delivery done! Your prize is behind the truck",
    "news.lottery_result": "Lotto Gordo, draw #%s: number %s, class %s. %s",
    "news.lottery_nobody": "Nobody won this time",
    "news.lottery_tier.1": "There is a jackpot winner!", "news.lottery_tier.2": "There was a medium prize", "news.lottery_tier.3": "There was a small prize",
    "lottery.title": "LOTTO GORDO", "lottery.draw": "Draw #%s", "lottery.last": "Last number:",
    "lottery.prize.1": "JACKPOT (number + class): 3 stacks of diamonds", "lottery.prize.2": "MEDIUM (number): 96 gold ingots",
    "lottery.prize.3": "SMALL (last 2 digits): 32 iron", "lottery.next": "Next draw in %s min", "lottery.soon": "Next draw very soon",
    "lottery.buy": "Tickets at the booth: 4 iron ingots",
}


def lang():
    for f, d in (("es_es.json", ES), ("en_us.json", EN)):
        p = A / "lang" / f
        s = p.read_text(encoding="utf-8")
        if '"lottery.buy"' in s:
            continue
        k = '  "itemGroup.beautifuldecor_parts"'
        assert k in s
        s = s.replace(k, "".join(f'  {json.dumps(a)}: {json.dumps(b, ensure_ascii=False)},\n' for a, b in d.items()) + k, 1)
        json.loads(s)
        p.write_text(s, encoding="utf-8")


if __name__ == "__main__":
    booth_textures(); register_textures(); ticket_icon(); models(); painting(); tags(); lang()
    print("escrito: puesto, caja registradora, boleto, cartel y textos de la lotería")
