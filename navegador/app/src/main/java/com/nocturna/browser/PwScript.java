package com.nocturna.browser;

/**
 * Lo que corre dentro de cada página para el gestor de contraseñas:
 * - avisa cuando hay un inicio de sesión (campo de contraseña, o el paso del usuario en Google/Microsoft),
 * - cuando la persona envía el formulario, pasa el usuario y la contraseña a la app para ofrecer guardarlos,
 * - rellena los campos cuando la persona lo pide (y pasó la huella).
 * Solo en la página principal, no en los marcos de otros sitios dentro de ella.
 */
final class PwScript {
    private PwScript() {}

    static final String JS = """
            (function(){
            if (window.__noctPw || window.top !== window) return;
            var B = window.NocturnaClaves; if (!B) return;
            var P = {}; window.__noctPw = P;
            var USER_RE = /user|email|e-mail|mail|login|correo|usuario|account|cuenta|identifier|phone|celular|telefono|documento|cedula/i;
            var STEP_RE = /identifier|loginfmt|username|user_?name|login_?field|login_?id|email|correo|usuario/i;
            var SUBMIT_RE = /iniciar|inicia|entrar|ingresar|acceder|sign ?in|log ?in|login|continu|siguiente|next|enviar|submit|confirm|verificar|aceptar|registr|sign ?up|crear/i;
            function vis(e){ if(!e || e.disabled) return false; var r = e.getBoundingClientRect();
              if (r.width < 2 || r.height < 2) return false; var s = getComputedStyle(e);
              return s.visibility !== 'hidden' && s.display !== 'none' && s.opacity !== '0'; }
            function arr(l){ return Array.prototype.slice.call(l); }
            function pwFields(onlyVisible){ return arr(document.querySelectorAll('input[type=password]')).filter(function(e){ return !onlyVisible || vis(e); }); }
            function textFields(scope){ return arr((scope || document).querySelectorAll('input[type=email],input[type=text],input[type=tel],input:not([type])')).filter(vis); }
            function desc(e){ return ((e.autocomplete||'')+' '+(e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')).toLowerCase(); }
            function before(a, b){ return !!(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING); }
            /** El campo del usuario que va con esta contraseña: el más cercano antes de ella. */
            function userFor(pw){
              var list = textFields(pw.form || document).filter(function(e){ return before(e, pw); });
              if (!list.length && pw.form) list = textFields(document).filter(function(e){ return before(e, pw); });
              for (var i = list.length - 1; i >= 0; i--) if (/username|email/.test(list[i].autocomplete || '')) return list[i];
              for (var j = list.length - 1; j >= 0; j--) if (USER_RE.test(desc(list[j])) || list[j].type === 'email') return list[j];
              return list.length ? list[list.length - 1] : null;
            }
            /** Paso de solo usuario (Google, Microsoft…): un campo de usuario sin contraseña a la vista. */
            function stepUser(){
              var l = textFields();
              for (var i = 0; i < l.length; i++) { var e = l[i];
                if (/username|webauthn/.test(e.autocomplete || '') || STEP_RE.test((e.name||'')+' '+(e.id||'')) || (e.type === 'email' && e.form)) return e; }
              return null;
            }
            /** La contraseña a guardar: en formularios de cambio de clave, la nueva. */
            function pickPw(){
              var all = pwFields(false).filter(function(e){ return e.value; });
              if (!all.length) return null;
              for (var i = 0; i < all.length; i++) if (/new-password/.test(all[i].autocomplete || '')) return all[i];
              if (all.length >= 3) return all[1];
              return all[all.length - 1];
            }
            var lastKey = '';
            function grab(submitted){
              try {
                var pw = pickPw();
                if (!pw) { var u = stepUser(); if (u && u.value) try { sessionStorage.setItem('__noctUser', u.value); } catch(e) {} return; }
                var uf = userFor(pw), user = uf && uf.value ? uf.value : '';
                if (!user) try { user = sessionStorage.getItem('__noctUser') || ''; } catch(e) {}
                var key = user + '\\n' + pw.value + '\\n' + submitted;
                if (key === lastKey) return; lastKey = key;
                B.candidate(user, pw.value, !!submitted, location.href);
              } catch(e) {}
            }
            document.addEventListener('submit', function(){ grab(true); }, true);
            document.addEventListener('keydown', function(e){
              if (e.key === 'Enter' && e.target && e.target.tagName === 'INPUT') grab(true); }, true);
            document.addEventListener('click', function(e){
              var t = e.target && e.target.closest ? e.target.closest('button,input[type=submit],input[type=button],[role=button],a') : null;
              if (!t) return;
              var label = (t.innerText || t.value || t.getAttribute('aria-label') || '') + ' ' + (t.id || '') + ' ' + (t.name || '');
              grab(t.type === 'submit' || SUBMIT_RE.test(label));
            }, true);
            document.addEventListener('focusout', function(e){ if (e.target && e.target.type === 'password') grab(false); }, true);
            window.addEventListener('pagehide', function(){ grab(true); });

            var state = '';
            function scan(){
              var s = pwFields(true).length ? 'p' : (stepUser() ? 'u' : '');
              if (s !== state) { state = s; if (s) B.found(s === 'p'); }
            }
            var timer = 0;
            function later(){ if (!timer) timer = setTimeout(function(){ timer = 0; scan(); }, 500); }
            document.addEventListener('DOMContentLoaded', scan);
            window.addEventListener('load', scan);
            document.addEventListener('focusin', later, true);
            try { new MutationObserver(later).observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['type','style','class','hidden']}); } catch(e) {}
            if (document.readyState !== 'loading') scan();

            function set(e, v){
              e.focus();
              var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
              d.set.call(e, v);
              e.dispatchEvent(new Event('input', {bubbles: true}));
              e.dispatchEvent(new Event('change', {bubbles: true}));
            }
            P.fill = function(user, pass){
              var pw = pwFields(true)[0];
              var uf = pw ? userFor(pw) : stepUser();
              if (uf && user && !uf.value) set(uf, user);
              else if (uf && user && uf.value !== user) set(uf, user);
              if (pw && pass) set(pw, pass);
              try { sessionStorage.setItem('__noctUser', user); } catch(e) {}
              lastKey = user + '\\n' + pass + '\\ntrue';
              return pw ? 'p' : (uf ? 'u' : '');
            };
            })();
            """;
}
