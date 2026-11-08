import com.example.beautifuldecor.browser.js.JsEngine;
import com.example.beautifuldecor.browser.js.JsException;
import com.example.beautifuldecor.browser.js.Js;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ejecuta un script de JavaScript con el motor y escribe lo que imprima out(...). Uso: JsRun archivo.js */
public class JsRun {
    static final String PRELUDE = "function out(){ var p=[]; for (var i=0;i<arguments.length;i++){ var x=arguments[i];"
            + " p.push(typeof x==='string'?x:(x===undefined?'undefined':(typeof x==='function'?'[fn]':(typeof x==='symbol'?'[sym]':JSON.stringify(x))))); }"
            + " __print(p.join(' ')); }\n";

    public static void main(String[] args) throws Exception {
        String src = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
        int[] code = {0};
        PrintStream ps = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        Thread t = new Thread(null, () -> code[0] = run(src, ps), "js", 512L << 20);
        t.start();
        t.join();
        ps.flush();
        System.exit(code[0]);
    }

    static int run(String src, PrintStream ps) {
        JsEngine e = new JsEngine();
        e.defineFunction("__print", 1, (th, a) -> {
            ps.println(Js.toStr(Js.arg(a, 0)));
            return Js.UNDEFINED;
        });
        e.setConsole((level, text) -> ps.println("[" + level + "] " + text));
        try {
            e.eval(PRELUDE);
            e.eval(src);
            e.runJobs();
            return 0;
        } catch (JsException ex) {
            ps.println("UNCAUGHT " + ex.getMessage());
            return 1;
        } catch (com.example.beautifuldecor.browser.js.JsSyntaxError ex) {
            ps.println("SYNTAX " + ex.getMessage());
            return 2;
        } finally {
            e.close();
        }
    }
}
