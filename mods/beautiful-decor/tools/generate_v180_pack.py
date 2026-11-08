#!/usr/bin/env python3
"""Genera los assets de la versión 1.8.0 de Beautiful Decor y conecta el código Java:

  * printer                 Impresora HS rediseñada (solo modelos/texturas; el mecanismo no cambia)
  * freezer_chest           Congelador horizontal (tapa que se abre)
  * cold_room               Cámara frigorífica 3x3x3 con puerta, bombillo interior y termostato
  * crate_empty / crate_fries / crate_ribs / crate_balls   Cajas
  * bugerati_*              Marca Bugerati: 6 lámparas, 3 candelabros colgantes (dorado, plateado, negro),
                            24 letreros de neón (6 formas x 4 colores) y el logo neón dorado

Requiere Pillow y numpy. Es seguro ejecutarlo varias veces (los parches de Java solo se aplican una vez).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from v180 import (bugerati_chandelier, bugerati_lamps, bugerati_mat, bugerati_neon, coldroom, crates, freezer,
                  printer)
from v180.common import *

MARK = "V1.8.0"


def patch(path, old, new, once_marker=None):
    text = path.read_text(encoding="utf-8")
    marker = once_marker or new.strip().splitlines()[0]
    if marker in text:
        return False
    if old not in text:
        raise SystemExit(f"No encuentro el punto de inserción en {path}: {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    return True


def write_loot_tables():
    simple = (["freezer_chest", "crate_empty", "crate_fries", "crate_ribs", "crate_balls"]
              + [i for i in bugerati_lamps.LAMPS]
              + [f"bugerati_chandelier_{f}" for f in bugerati_chandelier.FINISHES]
              + bugerati_neon.all_ids())
    for bid in simple:
        write_loot(bid)
    write_loot_part0("cold_room")


def merge_lang():
    for code, new in (("es_es", LANG_ES), ("en_us", LANG_EN)):
        path = LANG / f"{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        data.update(new)
        write_json(path, data)


JAVA_BLOCKS = '''
    // ===== V1.8.0: congelador, cámara frigorífica, cajas y la marca Bugerati =====
    public static final Map<String, RegistryObject<Block>> CRATES = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> BUGERATI = new LinkedHashMap<>();
    public static final String[] CRATE_IDS = {"crate_empty", "crate_fries", "crate_ribs", "crate_balls"};
    public static final String[] BUGERATI_NEON_SHAPES = {"ring", "heart", "star", "bolt", "moon", "arrow"};
    public static final String[] BUGERATI_NEON_COLORS = {"pink", "cyan", "yellow", "red"};
    public static final String[] BUGERATI_CHANDELIER_FINISHES = {"gold", "silver", "black"};

    /** Cámara frigorífica 3x3x3 (27 piezas); la luz 14 solo la da la pieza del bombillo del techo. */
    public static final RegistryObject<Block> COLD_ROOM = BLOCKS.register("cold_room",
            () -> new ColdRoomBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(3.0f, 4.0f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(ColdRoomBlock.PART) == ColdRoomBlock.LIGHT_PART ? 14 : 0)));

    private static BlockBehaviour.Properties bugeratiLampProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                .strength(1.0f).sound(SoundType.METAL)
                .lightLevel(state -> state.getValue(ToggleBlock.OPEN) ? 14 : 0).noOcclusion();
    }

    private static void registerBugeratiLamp(String name, VoxelShape northShape) {
        BUGERATI.put(name, BLOCKS.register(name,
                () -> new BugeratiLampBlock(bugeratiLampProps(), northShape)));
    }

    private static void registerBugeratiNeon(String name) {
        BUGERATI.put(name, BLOCKS.register(name,
                () -> new NeonPanelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                        .strength(0.5f).sound(SoundType.GLASS).lightLevel(state -> 13).noOcclusion())));
    }

    static {
        // Congelador horizontal: la tapa se levanta con clic derecho (igual que el horno o el microondas)
        registerOpenable("freezer_chest", ModBlock.box(0, 0, 1, 16, 14, 15), 0.7f);

        for (String id : CRATE_IDS) {
            CRATES.put(id, BLOCKS.register(id,
                    () -> new ShapedFacingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                            .strength(0.8f).sound(SoundType.WOOD).noOcclusion(),
                            ModBlock.box(1, 0, 1, 15, 12, 15))));
        }

        // Lámparas Bugerati (clic derecho = encender/apagar; también obedecen a los interruptores de pared)
        registerBugeratiLamp("bugerati_floor_lamp", ModBlock.box(4, 0, 4, 12, 15.6, 12));
        registerBugeratiLamp("bugerati_arc_lamp", ModBlock.box(3, 0, 3, 13, 13.5, 13));
        registerBugeratiLamp("bugerati_table_lamp", ModBlock.box(4, 0, 4, 12, 15.6, 12));
        registerBugeratiLamp("bugerati_orb_lamp", ModBlock.box(4, 0, 4, 12, 13.3, 12));
        registerBugeratiLamp("bugerati_pendant_lamp", ModBlock.box(4, 1, 4, 12, 16, 12));
        registerBugeratiLamp("bugerati_wall_sconce", ModBlock.box(5.6, 3.2, 9.4, 10.4, 12.8, 16));
        for (String finish : BUGERATI_CHANDELIER_FINISHES) {
            registerBugeratiLamp("bugerati_chandelier_" + finish, ModBlock.box(1, 1.4, 1, 15, 16, 15));
        }

        // Neones Bugerati de pared: 6 formas x 4 colores + logo dorado
        for (String shape : BUGERATI_NEON_SHAPES) {
            for (String color : BUGERATI_NEON_COLORS) {
                registerBugeratiNeon("bugerati_neon_" + shape + "_" + color);
            }
        }
        registerBugeratiNeon("bugerati_neon_logo");
    }
'''


def patch_java():
    blocks = JAVA / "init/ModBlocks.java"
    items = JAVA / "init/ModItems.java"
    cat = JAVA / "init/ModCategory.java"
    patch(blocks, "import com.example.beautifuldecor.block.BigTelevisionBlock;\n",
          "import com.example.beautifuldecor.block.BigTelevisionBlock;\n"
          "import com.example.beautifuldecor.block.BugeratiLampBlock;\n"
          "import com.example.beautifuldecor.block.ColdRoomBlock;\n"
          "import com.example.beautifuldecor.block.NeonPanelBlock;\n",
          "import com.example.beautifuldecor.block.ColdRoomBlock;")
    patch(blocks, "    public static void register(IEventBus eventBus) {\n        BLOCKS.register(eventBus);",
          JAVA_BLOCKS + "\n    public static void register(IEventBus eventBus) {\n        BLOCKS.register(eventBus);",
          "// ===== V1.8.0: congelador")
    patch(items, "        registerGroup(ModBlocks.WASHBASINS);\n    }\n",
          "        registerGroup(ModBlocks.WASHBASINS);\n"
          "        registerGroup(ModBlocks.CRATES);\n"
          "        registerGroup(ModBlocks.BUGERATI);\n    }\n\n"
          "    /** V1.8.0: cámara frigorífica (estructura 3x3x3 de varias piezas). */\n"
          "    public static final RegistryObject<Item> COLD_ROOM = ITEMS.register(\"cold_room\",\n"
          "            () -> new BlockItem(ModBlocks.COLD_ROOM.get(), new Item.Properties()));\n",
          "registerGroup(ModBlocks.CRATES);")
    patch(cat, '            || p.startsWith("cutlery_")),',
          '            || p.startsWith("cutlery_")\n'
          '            || p.equals("freezer_chest") || p.equals("cold_room") || p.startsWith("crate_")),',
          'p.equals("freezer_chest")')
    patch(cat, '            || p.startsWith("neon_bar_")),',
          '            || p.startsWith("neon_bar_") || p.startsWith("bugerati_")),',
          'p.startsWith("bugerati_")')


def bump_version():
    for path, old, new in [(ROOT / "build.gradle", "version = '1.7.0'", "version = '1.8.0'"),
                           (ROOT / "gradle.properties", "mod_version=1.7.0", "mod_version=1.8.0")]:
        text = path.read_text(encoding="utf-8")
        if old in text:
            path.write_text(text.replace(old, new), encoding="utf-8")


def main():
    printer.generate()
    freezer.generate()
    crates.generate()
    coldroom.generate()
    bugerati_mat.generate_materials()
    bugerati_lamps.generate()
    bugerati_chandelier.generate()
    bugerati_neon.generate()
    write_loot_tables()
    merge_lang()
    patch_java()
    bump_version()
    print("v1.8.0: assets generados y Java conectado")


if __name__ == "__main__":
    main()
