package com.nocturna.browser;

import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

/**
 * Modo oscuro forzado.
 * - FORCED: el motor de Chrome oscurece toda página que no tenga modo oscuro propio (respeta
 *   las fotos y los videos). Si una página sigue clara (porque el motor no la oscureció),
 *   un script de respaldo la invierte con cuidado, devolviendo imágenes y videos a su color.
 * - MAX: además reescribe los colores de todo el contenido: fondo casi negro y texto claro,
 *   en cualquier página, sin excepción.
 */
final class DarkMode {
    static final int OFF = 0, FORCED = 1, MAX = 2;
    static final String[] NAMES = {"Apagado", "Forzado (recomendado)", "Máximo (todo oscuro sí o sí)"};

    private DarkMode() {}

    static void apply(WebView w, int mode) {
        WebSettings s = w.getSettings();
        boolean on = mode != OFF;
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, on && mode == FORCED);
        }
        w.setBackgroundColor(on ? Ui.BG : 0xFFFFFFFF);
    }

    /** Script que se ejecuta al cargar la página. */
    static String script(int mode) {
        if (mode == MAX) return MAX_JS;
        if (mode == FORCED) return FALLBACK_JS;
        return "";
    }

    /** Hoja de estilo del modo máximo: todo con fondo oscuro y letras claras. */
    private static final String MAX_CSS =
            "html,body{background:#0b0f17!important;color:#dfe5ee!important}"
                    + "*:not(img):not(video):not(picture):not(canvas):not(svg):not(svg *):not(iframe){"
                    + "background-color:transparent!important;color:#dfe5ee!important;border-color:#2a3242!important;"
                    + "box-shadow:none!important;text-shadow:none!important}"
                    + "header,nav,footer,aside,main,section,article,div,table,td,th,ul,li,form,dialog,[role=dialog],[role=menu]{"
                    + "background-color:#0e131d!important}"
                    + "input,textarea,select,button,[contenteditable]{background-color:#182030!important;color:#eef2f8!important;"
                    + "border:1px solid #33405a!important}"
                    + "a,a *{color:#7cb4ff!important}a:visited,a:visited *{color:#b49cff!important}"
                    + "::placeholder{color:#8b97ab!important}"
                    + "img,video,picture{filter:brightness(.88) contrast(1.05)!important}"
                    + "::-webkit-scrollbar{background:#0b0f17}::-webkit-scrollbar-thumb{background:#2a3242}";

    private static final String MAX_JS =
            "(function(){if(window.__noctMax)return;window.__noctMax=1;"
                    + "function add(){if(document.getElementById('__noct_max'))return;var s=document.createElement('style');"
                    + "s.id='__noct_max';s.textContent=" + jsString(MAX_CSS) + ";"
                    + "(document.head||document.documentElement).appendChild(s);}"
                    + "add();document.addEventListener('DOMContentLoaded',add);"
                    + "new MutationObserver(add).observe(document.documentElement,{childList:true,subtree:false});"
                    + "var m=document.createElement('meta');m.name='color-scheme';m.content='dark';"
                    + "(document.head||document.documentElement).appendChild(m);})();";

    /**
     * Respaldo del modo forzado: mira el color real del fondo en varios puntos de la pantalla;
     * si la página sigue clara, la invierte (y vuelve a invertir imágenes y videos para que se
     * vean normales). Se revisa varias veces porque muchas páginas cargan su contenido después.
     */
    private static final String FALLBACK_JS =
            "(function(){if(window.__noctFb)return;window.__noctFb=1;"
                    + "var CSS='html{filter:invert(.93) hue-rotate(180deg)!important;background:#fff!important}"
                    + "img,video,picture,canvas,iframe,embed,object,svg image,[style*=\"background-image\"]{"
                    + "filter:invert(1) hue-rotate(180deg)!important}';"
                    + "function lum(c){var m=(c||'').match(/[\\d.]+/g);if(!m||m.length<3)return null;"
                    + "if(m.length>3&&parseFloat(m[3])<0.15)return null;return(0.2126*m[0]+0.7152*m[1]+0.0722*m[2])/255;}"
                    + "function bgAt(x,y){var e=document.elementFromPoint(x,y);while(e){var l=lum(getComputedStyle(e).backgroundColor);"
                    + "if(l!==null)return l;e=e.parentElement;}return 1;}"
                    // si la página dice que tiene modo oscuro propio, el motor no la oscurece: si aun así
                    // se ve clara, hay que oscurecerla nosotros
                    + "function declares(){var cs=getComputedStyle(document.documentElement).colorScheme||'';"
                    + "var m=document.querySelector('meta[name=\"color-scheme\"]');return /dark/.test(cs)||(m&&/dark/.test(m.content));}"
                    + "function check(){if(!document.body||document.getElementById('__noct_inv'))return;"
                    + "var w=innerWidth,h=innerHeight,pts=[[w*.5,h*.5],[w*.1,h*.15],[w*.9,h*.15],[w*.2,h*.8],[w*.8,h*.8]],s=0;"
                    + "for(var i=0;i<pts.length;i++)s+=bgAt(pts[i][0],pts[i][1]);s/=pts.length;"
                    + "if(s>0.62&&(window.__noctNeedsFallback||declares())){var st=document.createElement('style');st.id='__noct_inv';"
                    + "st.textContent=CSS;(document.head||document.documentElement).appendChild(st);}}"
                    + "[300,1200,3000,6000].forEach(function(t){setTimeout(check,t);});})();";

    /**
     * El motor oscurece la página al pintarla, pero el color que lee el script sigue siendo el
     * claro. Por eso el respaldo solo se activa cuando el motor no puede oscurecer
     * (WebView muy viejo).
     */
    static String fallbackFlag(boolean engineCanDarken) {
        return "window.__noctNeedsFallback=" + (!engineCanDarken) + ";";
    }

    static boolean engineCanDarken() {
        return WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING);
    }

    static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
