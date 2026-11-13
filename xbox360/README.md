# Xbox 360: empaquetador casero de plugins (.xex)

Proyecto de Forja Estudios para hacer plugins de la Xbox 360 con RGH sin el kit oficial de Microsoft.

## Fases
1. **Lector de XEX** (`tools/xexinfo.py`): probado con un `default.xex` real. ✔
2. **Compilador y librería mínima** (`sdk/`, `build.sh`): GCC PowerPC (32 bits, big-endian) + importaciones del kernel (`sdk/imports.txt`). ✔
3. **Empaquetador** (`tools/xexpack.py`) y **comprobador** (`tools/xexcheck.py`): XEX2 sin cifrar ni comprimir con imagen PE. ✔ (validado en PC)
4. **Plugin de prueba** (`plugin/hola.c`): START + clic del stick derecho muestra un aviso. Falta probarlo en la consola.
5. Menú dibujado encima del tablero.
6. Audio.
7. Radio por internet.

## Lo que NO está comprobado (necesita la consola)
- Que Dashlaunch cargue el XEX (hay variante DLL y variante «título»).
- Las firmas y los hashes: la firma RSA va en ceros y varios digests son aproximados; una consola con RGH normalmente no los comprueba.
- Las firmas de las funciones del kernel (`sdk/xbox.h`) vienen de documentación comunitaria.

## Uso
```
./build.sh plugin/hola.c build/hola.xex     # necesita gcc-powerpc-linux-gnu
python3 tools/xexcheck.py build/hola.xex
```
