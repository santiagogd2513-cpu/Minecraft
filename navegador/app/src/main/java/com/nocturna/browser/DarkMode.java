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
 * - MAX: oscurece todo, elemento por elemento, invirtiendo solo la claridad de cada color (nunca
 *   el tono ni la fuente): la página conserva su estilo, pero en oscuro.
 */
final class DarkMode {
    static final int OFF = 0, FORCED = 1, MAX = 2;
    static final String[] NAMES = {"Apagado", "Forzado (recomendado)", "Máximo (oscurece todo y conserva los colores)"};

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
        if (mode == MAX) return maxJs;
        if (mode == FORCED) return FALLBACK_JS;
        return "";
    }

    /** El script del modo máximo vive en assets/oscuro_max.js (se carga una vez). */
    private static String maxJs = "";

    static void init(android.content.Context c) {
        if (!maxJs.isEmpty()) return;
        try (java.io.InputStream in = c.getAssets().open("oscuro_max.js")) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            maxJs = bo.toString("UTF-8");
        } catch (java.io.IOException ignored) {
        }
    }

    /**
     * Respaldo del modo forzado: mira el color real del fondo en varios puntos de la pantalla;
     * si la página sigue clara, la invierte (y vuelve a invertir imágenes y videos para que se
     * vean normales). Se revisa varias veces porque muchas páginas cargan su contenido después.
     */
    private static final String FALLBACK_JS =
            "(function(){if(window.__noctFb)return;window.__noctFb=1;"
                    + "var CSS='html{filter:invert(.92) hue-rotate(180deg)!important;background:#fff!important}"
                    + "img,video,picture,canvas,iframe,embed,object,svg image,[style*=\"background-image\"]{"
                    + "filter:invert(1) hue-rotate(180deg)!important}';"
                    + "function lum(c){var m=(c||'').match(/[\\d.]+/g);if(!m||m.length<3)return null;"
                    + "if(m.length>3&&parseFloat(m[3])<0.15)return null;return(0.2126*m[0]+0.7152*m[1]+0.0722*m[2])/255;}"
                    + "function bgAt(x,y){var e=document.elementFromPoint(x,y);while(e){var l=lum(getComputedStyle(e).backgroundColor);"
                    + "if(l!==null)return l;e=e.parentElement;}return 1;}"
                    // si la página dice que tiene modo oscuro propio, el motor no la oscurece
                    + "function declares(){var cs=getComputedStyle(document.documentElement).colorScheme||'';"
                    + "var m=document.querySelector('meta[name=\"color-scheme\"]');return /dark/.test(cs)||(m&&/dark/.test(m.content));}"
                    // si el motor no está en modo oscuro (le dice a las páginas 'claro'), no oscureció nada
                    + "function engineDark(){return !window.__noctNeedsFallback&&window.matchMedia&&"
                    + "matchMedia('(prefers-color-scheme: dark)').matches;}"
                    + "function put(){window.__noctWant=1;if(document.getElementById('__noct_inv'))return;"
                    + "var r=document.head||document.documentElement;if(!r)return;var st=document.createElement('style');"
                    + "st.id='__noct_inv';st.textContent=CSS;r.appendChild(st);}"
                    + "function unput(){window.__noctWant=0;var st=document.getElementById('__noct_inv');if(st)st.remove();}"
                    // los colores que lee el script son los originales (sin el filtro), así que se puede
                    // medir si la página es clara u oscura aunque ya esté invertida
                    + "function check(){if(!document.body)return;"
                    + "var w=innerWidth,h=innerHeight,pts=[[w*.5,h*.5],[w*.1,h*.15],[w*.9,h*.15],[w*.2,h*.8],[w*.8,h*.8],[w*.5,h*.3]],s=0;"
                    + "for(var i=0;i<pts.length;i++)s+=bgAt(pts[i][0],pts[i][1]);s/=pts.length;"
                    + "if(s>0.6){if(!engineDark()||declares()){window.__noctInv=1;put();}}"
                    + "else if(s<0.45&&!window.__noctInv&&document.body.children.length>0){unput();}}"
                    // desde el primer instante: si el motor no está oscureciendo, se oscurece ya, antes de pintar
                    + "if(!engineDark())put();"
                    + "var n=0,t=setInterval(function(){check();if(++n>60)clearInterval(t);},250);"
                    + "document.addEventListener('DOMContentLoaded',check);addEventListener('load',check);"
                    // la página todavía no existe al primer instante: se pone el estilo apenas aparezca
                    + "var mo=new MutationObserver(function(){if(window.__noctWant)put();});"
                    + "mo.observe(document,{childList:true,subtree:true});"
                    + "addEventListener('load',function(){setTimeout(function(){mo.disconnect();"
                    + "new MutationObserver(function(){if(window.__noctWant)put();}).observe(document.documentElement,{childList:true});},15000);});"
                    + "})();";

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
