#!/usr/bin/env bash
# Limbo solo aceptaba archivos y carpetas que vinieran del selector de Android
# («com.android.externalstorage.documents»). Este parche cambia esa revisión
# por «content://», así acepta cualquier proveedor de documentos (Bóveda, etc.).
# Uso: parchar.sh <limbo-original.apk> <salida.apk>
set -euo pipefail
IN="$1"
OUT="$2"
WORK="$(mktemp -d)"
APKTOOL="${APKTOOL:-apktool.jar}"

java -jar "$APKTOOL" d -f -r -o "$WORK/src" "$IN"

FILES=$(grep -rl --include=FileUtils.smali '"com.android.externalstorage.documents"' "$WORK/src" || true)
if [ -z "$FILES" ]; then
  echo "No se encontró la revisión del selector en FileUtils.smali" >&2
  exit 1
fi
for f in $FILES; do
  echo "Parcheando $f"
  grep -n '"com.android.externalstorage.documents"' "$f"
  sed -i 's#"com.android.externalstorage.documents"#"content://"#g' "$f"
done

java -jar "$APKTOOL" b -o "$WORK/sin-firma.apk" "$WORK/src"
cp "$WORK/sin-firma.apk" "$OUT"
echo "Listo: $OUT"
