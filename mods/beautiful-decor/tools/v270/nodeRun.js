// Ejecuta un script con node usando el mismo preludio que JsRun.java, para comparar salidas.
const fs = require('fs');
globalThis.__print = (s) => process.stdout.write(s + '\n');
const prelude = "function out(){ var p=[]; for (var i=0;i<arguments.length;i++){ var x=arguments[i];"
  + " p.push(typeof x==='string'?x:(x===undefined?'undefined':(typeof x==='function'?'[fn]':(typeof x==='symbol'?'[sym]':JSON.stringify(x))))); }"
  + " __print(p.join(' ')); }\n";
const vm = require('vm');
{
  const { Console } = require('console');
  const { Writable } = require('stream');
  let lvl = 'log';
  const w = new Writable({ write(chunk, enc, cb) { process.stdout.write('[' + lvl + '] ' + chunk.toString()); cb(); } });
  const c = new Console({ stdout: w, stderr: w, groupIndentation: 2, colorMode: false });
  const map = { log: 'log', info: 'info', debug: 'debug', warn: 'warn', error: 'error', dir: 'log', table: 'log', count: 'log', group: 'log', groupCollapsed: 'log', assert: 'error' };
  for (const k of Object.keys(map)) { console[k] = (...a) => { lvl = map[k]; c[k](...a); }; }
  console.groupEnd = () => c.groupEnd();
  console.countReset = (l) => c.countReset(l);
}
const src = fs.readFileSync(process.argv[2], 'utf8');
try {
  vm.runInThisContext(prelude);
  vm.runInThisContext(src);
} catch (e) {
  process.stdout.write('UNCAUGHT ' + (e && e.name ? e.name + ': ' + e.message : String(e)) + '\n');
}
