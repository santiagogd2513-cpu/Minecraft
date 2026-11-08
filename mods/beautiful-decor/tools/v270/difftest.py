#!/usr/bin/env python3
"""Compara la salida del motor JS en Java con la de node, script por script.

Uso: difftest.py [--update] [classes_dir] [archivos.js...]   (por defecto: todos los de tools/v270/js)

* Si hay node, compara contra node. Si no, contra las salidas guardadas en tools/v270/expected/ (hechas con node 22).
* --update guarda de nuevo las salidas de node en tools/v270/expected/.
* Los scripts de fechas (t12_*) se corren en 5 zonas horarias; todos los demás en America/Bogota.
* DIFF_MAX=n muestra hasta n líneas distintas por script (por defecto 4).
"""
import glob, os, shutil, subprocess, sys

here = os.path.dirname(os.path.abspath(__file__))
args = sys.argv[1:]
update = '--update' in args
args = [a for a in args if a != '--update']
classes = args[0] if args else '/tmp/jsout'
files = args[1:] or sorted(glob.glob(os.path.join(here, 'js', '*.js')))
expected_dir = os.path.join(here, 'expected')
os.makedirs(expected_dir, exist_ok=True)
ZONES = ['America/Bogota', 'America/New_York', 'Europe/Madrid', 'Asia/Kolkata', 'Australia/Sydney']
have_node = shutil.which('node') is not None
dmax = int(os.environ.get('DIFF_MAX', '4'))
bad = 0
total = 0
for f in files:
    base = os.path.basename(f)
    zones = ZONES if base.startswith('t12_') else ZONES[:1]
    for zone in zones:
        total += 1
        env = dict(os.environ)
        env.pop('JAVA_TOOL_OPTIONS', None)
        env['TZ'] = zone
        label = base + ('' if len(zones) == 1 else ' [' + zone + ']')
        exp_path = os.path.join(expected_dir, base + ('' if zone == ZONES[0] else '.' + zone.replace('/', '_')) + '.out')
        if have_node:
            node = subprocess.run(['node', os.path.join(here, 'nodeRun.js'), f], capture_output=True, text=True, timeout=60, env=env)
            a = node.stdout.rstrip('\n').split('\n') if node.stdout.strip() else []
            if update:
                with open(exp_path, 'w', encoding='utf-8') as fh:
                    fh.write(node.stdout)
        elif os.path.exists(exp_path):
            with open(exp_path, encoding='utf-8') as fh:
                txt = fh.read()
            a = txt.rstrip('\n').split('\n') if txt.strip() else []
        else:
            print('FALTA ', label, '(sin node ni salida guardada)')
            bad += 1
            continue
        jv = subprocess.run(['java', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-cp', classes, 'JsRun', f], capture_output=True, text=True,
                            timeout=180, env=env, encoding='utf-8')
        b = jv.stdout.rstrip('\n').split('\n') if jv.stdout.strip() else []
        if a == b:
            print('OK   ', label, len(a), 'líneas')
            continue
        bad += 1
        print('FALLA', label)
        n = 0
        for i in range(max(len(a), len(b))):
            x = a[i] if i < len(a) else '<nada>'
            y = b[i] if i < len(b) else '<nada>'
            if x != y:
                print('   línea', i + 1)
                print('   esperado:', x[:300])
                print('   java    :', y[:300])
                n += 1
                if n >= dmax:
                    break
        if jv.stderr.strip():
            print('   stderr:', jv.stderr.strip()[:300])
print('RESULTADO:', f'todo igual ({total} corridas)' if bad == 0 else f'{bad} corrida(s) distintas')
sys.exit(1 if bad else 0)
