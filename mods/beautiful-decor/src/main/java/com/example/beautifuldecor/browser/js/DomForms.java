package com.example.beautifuldecor.browser.js;

import com.example.beautifuldecor.browser.InternalPages;
import com.example.beautifuldecor.browser.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Formularios, controles, foco, clics y escritura. */
final class DomForms {
    private static final Set<String> TEXT_TYPES = new HashSet<>(Arrays.asList(
            "text", "search", "url", "tel", "email", "password", "number", "date", "datetime-local", "month", "week", "time"));
    private static final Set<String> INPUT_TYPES = new HashSet<>(Arrays.asList(
            "hidden", "text", "search", "tel", "url", "email", "password", "date", "month", "week", "time", "datetime-local", "number",
            "range", "color", "checkbox", "radio", "file", "submit", "image", "reset", "button"));
    private static final Set<String> LISTED = new HashSet<>(Arrays.asList("button", "fieldset", "input", "object", "output", "select", "textarea"));
    private static final Set<String> LABELABLE = new HashSet<>(Arrays.asList("button", "input", "meter", "output", "progress", "select", "textarea"));

    private DomForms() {
    }

    static String t(String es, String en) {
        return InternalPages.t(es, en);
    }

    static boolean isTextLike(String type) {
        return TEXT_TYPES.contains(type);
    }

    static String typeOf(Node n) {
        String t = DomSelector.inputType(n);
        return INPUT_TYPES.contains(t) ? t : "text";
    }

    /** Un campo donde se escribe (input de texto o textarea). */
    static boolean isTextControl(Node n) {
        if (n == null) {
            return false;
        }
        if (n.is("textarea")) {
            return true;
        }
        return n.is("input") && isTextLike(typeOf(n));
    }

    static boolean editable(Node n) {
        return isTextControl(n) && !n.hasAttr("readonly") && !DomSelector.isDisabled(n);
    }

    static boolean isListed(Node n) {
        return n.isElement() && LISTED.contains(n.tag) && !(n.is("input") && "image".equals(typeOf(n)));
    }

    // ------------------------------------------------------------------ propietario y listas

    static Node ownerForm(DomHost h, Node ctl) {
        String fid = ctl.attr("form");
        if (fid != null) {
            Node[] hit = new Node[1];
            DomNodes.collect(h.doc, null, x -> {
                if (hit[0] == null && x.is("form") && fid.equals(x.attr("id"))) {
                    hit[0] = x;
                }
                return false;
            });
            return hit[0];
        }
        for (Node p = ctl.parent; p != null; p = p.parent) {
            if (p.is("form")) {
                return p;
            }
        }
        return null;
    }

    static List<Node> controlsOf(DomHost h, Node form) {
        List<Node> out = new ArrayList<>();
        DomNodes.collect(h.doc, out, x -> isListed(x) && ownerForm(h, x) == form);
        return out;
    }

    static List<Node> optionsOf(Node select) {
        List<Node> list = new ArrayList<>();
        for (Node o : select.children) {
            if (o.is("option")) {
                list.add(o);
            } else if (o.is("optgroup")) {
                for (Node p : o.children) {
                    if (p.is("option")) {
                        list.add(p);
                    }
                }
            }
        }
        return list;
    }

    static boolean multiple(Node select) {
        return select.hasAttr("multiple");
    }

    /** Índice del option elegido en un select de una sola opción (la última marcada, o la primera). */
    static int selectedIndex(Node select) {
        List<Node> opts = optionsOf(select);
        int last = -1;
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).isChecked()) {
                last = i;
            }
        }
        if (last >= 0) {
            return last;
        }
        if (multiple(select)) {
            return -1;
        }
        return opts.isEmpty() ? -1 : 0;
    }

    static void selectIndex(DomHost h, Node select, int idx) {
        List<Node> opts = optionsOf(select);
        for (int i = 0; i < opts.size(); i++) {
            opts.get(i).formChecked = i == idx;
        }
        Node.touch();
    }

    static boolean optionSelected(Node o) {
        Node sel = selectOf(o);
        if (sel == null) {
            return o.isChecked();
        }
        if (multiple(sel)) {
            return o.isChecked();
        }
        List<Node> opts = optionsOf(sel);
        return selectedIndex(sel) == opts.indexOf(o);
    }

    static Node selectOf(Node option) {
        for (Node p = option.parent; p != null; p = p.parent) {
            if (p.is("select")) {
                return p;
            }
            if (!p.is("optgroup")) {
                return null;
            }
        }
        return null;
    }

    static String optionValue(Node o) {
        String v = o.attr("value");
        return v != null ? v : o.textContent();
    }

    static List<Node> selectedOptions(Node select) {
        List<Node> out = new ArrayList<>();
        List<Node> opts = optionsOf(select);
        if (multiple(select)) {
            for (Node o : opts) {
                if (o.isChecked()) {
                    out.add(o);
                }
            }
        } else {
            int i = selectedIndex(select);
            if (i >= 0) {
                out.add(opts.get(i));
            }
        }
        return out;
    }

    static void setChecked(DomHost h, Node n, boolean on) {
        if (on && n.is("input") && "radio".equals(typeOf(n))) {
            String name = n.attr("name");
            if (name != null && !name.isEmpty()) {
                Node form = ownerForm(h, n);
                List<Node> same = new ArrayList<>();
                DomNodes.collect(n.root(), same, x -> x.is("input") && x != n && "radio".equals(typeOf(x)) && name.equals(x.attr("name"))
                        && ownerForm(h, x) == form);
                for (Node o : same) {
                    o.formChecked = false;
                }
            }
        }
        n.formChecked = on;
        Node.touch();
    }

    static String inputValue(Node n) {
        String t = typeOf(n);
        switch (t) {
            case "checkbox", "radio": {
                String v = n.attr("value");
                return v == null ? "on" : v;
            }
            case "submit", "reset", "button", "image", "hidden": {
                String v = n.attr("value");
                return v == null ? "" : v;
            }
            case "file":
                return "";
            default: {
                String v = n.currentValue().replace("\r", "").replace("\n", "");
                if (t.equals("number") && !v.isEmpty()) {
                    try {
                        Double.parseDouble(v);
                    } catch (NumberFormatException e) {
                        return "";
                    }
                }
                return v;
            }
        }
    }

    static void setInputValue(Node n, String v) {
        String t = typeOf(n);
        switch (t) {
            case "checkbox", "radio", "submit", "reset", "button", "image", "hidden":
                n.setAttr("value", v);
                break;
            case "file":
                break;
            default:
                n.formValue = v.replace("\r", "").replace("\n", "");
                n.caret = -1;
                n.selAnchor = -1;
                break;
        }
        Node.touch();
    }

    // ------------------------------------------------------------------ validación

    /** null si el control es válido; si no, el mensaje que se le mostraría al usuario. */
    static String problem(DomHost h, Node c) {
        if (!c.isElement()) {
            return null;
        }
        String custom = h.customValidity.get(c);
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        if (DomSelector.isDisabled(c)) {
            return null;
        }
        String tag = c.tag;
        if (tag.equals("input")) {
            String t = typeOf(c);
            if (t.equals("hidden") || t.equals("submit") || t.equals("reset") || t.equals("button") || t.equals("image")) {
                return null;
            }
            String v = inputValue(c);
            boolean required = c.hasAttr("required");
            if (t.equals("checkbox")) {
                return required && !c.isChecked() ? t("Marca esta casilla si quieres continuar.", "Please check this box if you want to proceed.") : null;
            }
            if (t.equals("radio")) {
                if (!required) {
                    return null;
                }
                String name = c.attr("name");
                Node form = ownerForm(h, c);
                boolean any = c.isChecked();
                if (!any && name != null && !name.isEmpty()) {
                    List<Node> same = new ArrayList<>();
                    DomNodes.collect(c.root(), same, x -> x.is("input") && "radio".equals(typeOf(x)) && name.equals(x.attr("name")) && ownerForm(h, x) == form);
                    for (Node o : same) {
                        any |= o.isChecked();
                    }
                }
                return any ? null : t("Elige una de estas opciones.", "Please select one of these options.");
            }
            if (c.hasAttr("readonly") && !required) {
                return null;
            }
            if (v.isEmpty()) {
                return required && !c.hasAttr("readonly") ? t("Completa este campo.", "Please fill out this field.") : null;
            }
            return textProblem(c, t, v);
        }
        if (tag.equals("textarea")) {
            String v = c.currentValue().replace("\r", "");
            if (v.isEmpty()) {
                return c.hasAttr("required") && !c.hasAttr("readonly") ? t("Completa este campo.", "Please fill out this field.") : null;
            }
            return textProblem(c, "textarea", v);
        }
        if (tag.equals("select") && c.hasAttr("required")) {
            List<Node> sel = selectedOptions(c);
            boolean empty = sel.isEmpty() || (!multiple(c) && optionValue(sel.get(0)).isEmpty());
            return empty ? t("Elige un elemento de la lista.", "Please select an item in the list.") : null;
        }
        return null;
    }

    private static String textProblem(Node c, String type, String v) {
        String min = c.attr("minlength");
        String max = c.attr("maxlength");
        try {
            if (min != null && v.length() < Integer.parseInt(min.trim())) {
                return t("Usa al menos " + min.trim() + " caracteres.", "Use at least " + min.trim() + " characters.");
            }
            if (max != null && v.length() > Integer.parseInt(max.trim())) {
                return t("Usa como máximo " + max.trim() + " caracteres.", "Use at most " + max.trim() + " characters.");
            }
        } catch (NumberFormatException ignored) {
            // atributo roto: se ignora
        }
        if (type.equals("email") && !Pattern.matches("[^@\\s]+@[^@\\s]+", v.trim())) {
            return t("Escribe una dirección de correo válida.", "Please enter an email address.");
        }
        if (type.equals("url") && !Pattern.matches("[A-Za-z][A-Za-z0-9+.-]*:.+", v.trim())) {
            return t("Escribe una dirección web válida.", "Please enter a URL.");
        }
        String pattern = c.attr("pattern");
        if (pattern != null && !pattern.isEmpty() && !type.equals("textarea")) {
            try {
                if (!Pattern.matches("(?:" + pattern + ")", v)) {
                    String title = c.attr("title");
                    return title != null && !title.isEmpty() ? title : t("Usa el formato pedido.", "Please match the requested format.");
                }
            } catch (RuntimeException ignored) {
                // patrón que Java no entiende: se ignora
            }
        }
        if (type.equals("number")) {
            try {
                double d = Double.parseDouble(v);
                String lo = c.attr("min");
                String hi = c.attr("max");
                if (lo != null && d < Double.parseDouble(lo.trim())) {
                    return t("El valor debe ser mayor o igual a " + lo.trim() + ".", "Value must be greater than or equal to " + lo.trim() + ".");
                }
                if (hi != null && d > Double.parseDouble(hi.trim())) {
                    return t("El valor debe ser menor o igual a " + hi.trim() + ".", "Value must be less than or equal to " + hi.trim() + ".");
                }
            } catch (NumberFormatException ignored) {
                // se ignora
            }
        }
        return null;
    }

    static boolean willValidate(Node c) {
        if (!c.isElement() || DomSelector.isDisabled(c)) {
            return false;
        }
        if (c.is("input")) {
            String t = typeOf(c);
            return !(t.equals("hidden") || t.equals("submit") || t.equals("reset") || t.equals("button") || t.equals("image"));
        }
        return c.is("textarea") || c.is("select") || c.is("button") && "submit".equals(buttonType(c));
    }

    static String buttonType(Node b) {
        String t = b.attr("type");
        if (t == null) {
            return "submit";
        }
        t = t.trim().toLowerCase(Locale.ROOT);
        return t.equals("reset") || t.equals("button") ? t : "submit";
    }

    /** Revisa los controles del formulario; el primero con problema recibe el foco y su mensaje. */
    static boolean validateForm(DomHost h, Node form) {
        for (Node c : controlsOf(h, form)) {
            if (!willValidate(c)) {
                continue;
            }
            String p = problem(h, c);
            if (p != null) {
                DomEvents.fireSimple(h, c, "invalid", false, true);
                focus(h, c);
                h.env.status(p);
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ envío

    /** Los pares nombre/valor que se mandan al enviar el formulario. */
    static List<String[]> entries(DomHost h, Node form, Node submitter) {
        List<String[]> out = new ArrayList<>();
        for (Node c : controlsOf(h, form)) {
            if (DomSelector.isDisabled(c)) {
                continue;
            }
            String name = c.attr("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            switch (c.tag) {
                case "input": {
                    String t = typeOf(c);
                    switch (t) {
                        case "checkbox", "radio":
                            if (c.isChecked()) {
                                out.add(new String[] {name, inputValue(c)});
                            }
                            break;
                        case "submit":
                            if (c == submitter) {
                                out.add(new String[] {name, c.attr("value") == null ? "" : c.attr("value")});
                            }
                            break;
                        case "reset", "button", "image":
                            break;
                        case "file":
                            out.add(new String[] {name, ""});
                            break;
                        default:
                            out.add(new String[] {name, inputValue(c)});
                            break;
                    }
                    break;
                }
                case "button":
                    if (c == submitter && "submit".equals(buttonType(c))) {
                        out.add(new String[] {name, c.attr("value") == null ? "" : c.attr("value")});
                    }
                    break;
                case "select":
                    for (Node o : selectedOptions(c)) {
                        if (!DomSelector.isDisabled(o)) {
                            out.add(new String[] {name, optionValue(o)});
                        }
                    }
                    break;
                case "textarea":
                    out.add(new String[] {name, c.currentValue().replace("\r\n", "\n").replace("\n", "\r\n")});
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    static void doSubmit(DomHost h, Node form, Node submitter) {
        DomHost.Submission s = new DomHost.Submission();
        String method = form.attr("method");
        String enctype = form.attr("enctype");
        String action = form.attr("action");
        String target = form.attr("target");
        if (submitter != null) {
            if (submitter.attr("formmethod") != null) {
                method = submitter.attr("formmethod");
            }
            if (submitter.attr("formenctype") != null) {
                enctype = submitter.attr("formenctype");
            }
            if (submitter.attr("formaction") != null) {
                action = submitter.attr("formaction");
            }
            if (submitter.attr("formtarget") != null) {
                target = submitter.attr("formtarget");
            }
        }
        s.method = method != null && method.trim().equalsIgnoreCase("post") ? "POST" : "GET";
        s.enctype = enctype == null ? "application/x-www-form-urlencoded" : enctype.trim().toLowerCase(Locale.ROOT);
        if (!s.enctype.equals("multipart/form-data") && !s.enctype.equals("text/plain")) {
            s.enctype = "application/x-www-form-urlencoded";
        }
        s.action = action == null || action.isBlank() ? h.resolveUrl("") : h.resolveUrl(action);
        s.target = target == null ? "" : target;
        s.fields.addAll(entries(h, form, submitter));
        h.env.submit(s);
    }

    /** Envío pedido por el usuario o por requestSubmit(): revisa, avisa con el evento submit y envía. */
    static void submit(DomHost h, Node form, Node submitter, boolean validate) {
        if (validate && !form.hasAttr("novalidate") && !(submitter != null && submitter.hasAttr("formnovalidate"))
                && !validateForm(h, form)) {
            return;
        }
        DomEvents.DomEvent ev = DomEvents.create(h, "SubmitEvent", "submit", true, true);
        DomEvents.put(ev, "submitter", submitter == null ? Js.NULL : h.wrap(submitter));
        if (!DomEvents.dispatch(h, form, ev)) {
            return;
        }
        doSubmit(h, form, submitter);
    }

    static void reset(DomHost h, Node form, boolean fire) {
        if (fire && !DomEvents.fireSimple(h, form, "reset", true, true)) {
            return;
        }
        for (Node c : controlsOf(h, form)) {
            c.formValue = null;
            c.formChecked = null;
            c.caret = -1;
            c.selAnchor = -1;
            if (c.is("select")) {
                for (Node o : optionsOf(c)) {
                    o.formChecked = null;
                }
            }
        }
        Node.touch();
    }

    static Node defaultButton(DomHost h, Node form) {
        for (Node c : controlsOf(h, form)) {
            if ((c.is("button") && "submit".equals(buttonType(c))) || (c.is("input") && ("submit".equals(typeOf(c)) || "image".equals(typeOf(c))))) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ foco

    static void focus(DomHost h, Node n) {
        if (n == null || !n.isElement() || h.focused == n || !DomElements.focusable(n)) {
            return;
        }
        if (n.root() != h.doc) {
            return;
        }
        Node old = h.focused;
        if (old != null) {
            blurNode(h, old, n);
        }
        h.focused = n;
        if (isTextControl(n)) {
            h.focusValues.put(n, n.currentValue());
            if (n.caret < 0 || n.caret > n.currentValue().length()) {
                n.caret = n.currentValue().length();
            }
        }
        Node.touch();
        fireFocus(h, n, "focus", false, old);
        fireFocus(h, n, "focusin", true, old);
    }

    static void blur(DomHost h) {
        Node old = h.focused;
        if (old != null) {
            blurNode(h, old, null);
        }
    }

    private static void blurNode(DomHost h, Node old, Node next) {
        h.focused = null;
        if (isTextControl(old)) {
            String before = h.focusValues.remove(old);
            if (before != null && !before.equals(old.currentValue())) {
                DomEvents.fireSimple(h, old, "change", true, false);
            }
            old.selAnchor = -1;
        }
        Node.touch();
        fireFocus(h, old, "blur", false, next);
        fireFocus(h, old, "focusout", true, next);
    }

    private static void fireFocus(DomHost h, Node n, String type, boolean bubbles, Node related) {
        DomEvents.DomEvent e = DomEvents.create(h, "FocusEvent", type, bubbles, false);
        DomEvents.put(e, "relatedTarget", related == null ? Js.NULL : h.wrap(related));
        DomEvents.dispatch(h, n, e);
    }

    /** Foco al siguiente (o anterior) elemento que lo acepta. */
    static void focusNext(DomHost h, boolean backwards) {
        List<Node> all = new ArrayList<>();
        DomNodes.collect(h.doc, all, x -> x.isElement() && DomElements.focusable(x) && !(x.hasAttr("tabindex") && tabIndex(x) < 0)
                && !x.hasAttr("hidden"));
        if (all.isEmpty()) {
            return;
        }
        int i = h.focused == null ? -1 : all.indexOf(h.focused);
        int next;
        if (backwards) {
            next = i <= 0 ? all.size() - 1 : i - 1;
        } else {
            next = i + 1 >= all.size() ? 0 : i + 1;
        }
        focus(h, all.get(next));
    }

    private static int tabIndex(Node n) {
        try {
            return Integer.parseInt(n.attr("tabindex").trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ clics

    private static Node activationTarget(Node target) {
        for (Node n = target; n != null; n = n.parent) {
            if (!n.isElement()) {
                continue;
            }
            switch (n.tag) {
                case "a", "area":
                    if (n.hasAttr("href")) {
                        return n;
                    }
                    break;
                case "button", "label", "select":
                    return n;
                case "input":
                    if (!"hidden".equals(typeOf(n))) {
                        return n;
                    }
                    break;
                case "summary":
                    if (n.parent != null && n.parent.is("details")) {
                        return n;
                    }
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    private static boolean insideDisabled(Node target) {
        for (Node n = target; n != null; n = n.parent) {
            if (n.isElement() && DomSelector.isDisabled(n) && (n.is("button") || n.is("input") || n.is("select") || n.is("textarea"))) {
                return true;
            }
        }
        return false;
    }

    /** Un clic del usuario: pointerdown, mousedown, (foco), pointerup, mouseup, click. */
    static void userClick(DomHost h, Node target, int x, int y, int button, int mods) {
        if (target == null) {
            target = DomDocument.bodyOf(h);
        }
        if (target == null || insideDisabled(target)) {
            return;
        }
        int buttons = button == 0 ? 1 : button == 2 ? 2 : 4;
        DomEvents.dispatch(h, target, DomEvents.mouse(h, "pointerdown", true, true, x, y, button, buttons, mods, null));
        DomEvents.DomEvent down = DomEvents.mouse(h, "mousedown", true, true, x, y, button, buttons, mods, null);
        boolean ok = DomEvents.dispatch(h, target, down);
        if (ok) {
            Node f = target;
            while (f != null && !(f.isElement() && DomElements.focusable(f))) {
                f = f.parent;
            }
            if (f != null) {
                focus(h, f);
            } else {
                blur(h);
            }
        }
        DomEvents.dispatch(h, target, DomEvents.mouse(h, "pointerup", true, true, x, y, button, 0, mods, null));
        DomEvents.dispatch(h, target, DomEvents.mouse(h, "mouseup", true, true, x, y, button, 0, mods, null));
        if (button == 0) {
            click(h, target, x, y, mods);
        } else if (button == 1) {
            DomEvents.dispatch(h, target, DomEvents.mouse(h, "auxclick", true, true, x, y, button, 0, mods, null));
        } else if (button == 2) {
            DomEvents.dispatch(h, target, DomEvents.mouse(h, "contextmenu", true, true, x, y, button, 0, mods, null));
        }
        h.engine.runJobs();
    }

    /** El evento click y lo que el navegador hace después (seguir un enlace, marcar una casilla, enviar...). */
    static boolean click(DomHost h, Node target, int x, int y, int mods) {
        Node act = activationTarget(target);
        boolean toggles = act != null && act.is("input") && (typeOf(act).equals("checkbox") || typeOf(act).equals("radio"));
        boolean oldChecked = toggles && act.isChecked();
        Node previousRadio = null;
        if (toggles) {
            if (typeOf(act).equals("radio")) {
                String name = act.attr("name");
                Node form = ownerForm(h, act);
                if (name != null && !name.isEmpty()) {
                    List<Node> same = new ArrayList<>();
                    DomNodes.collect(act.root(), same, o -> o.is("input") && o != act && "radio".equals(typeOf(o)) && name.equals(o.attr("name"))
                            && ownerForm(h, o) == form && o.isChecked());
                    previousRadio = same.isEmpty() ? null : same.get(0);
                }
                setChecked(h, act, true);
            } else {
                setChecked(h, act, !oldChecked);
            }
        }
        DomEvents.DomEvent ev = DomEvents.mouse(h, "click", true, true, x, y, 0, 0, mods, null);
        boolean ok = DomEvents.dispatch(h, target, ev);
        if (!ok) {
            if (toggles) {
                if (typeOf(act).equals("radio")) {
                    if (previousRadio != null) {
                        setChecked(h, previousRadio, true);
                    }
                    act.formChecked = oldChecked;
                } else {
                    act.formChecked = oldChecked;
                }
                Node.touch();
            }
            return false;
        }
        if (act != null) {
            activate(h, act, target, ev, toggles, oldChecked, mods);
        }
        return true;
    }

    private static void activate(DomHost h, Node act, Node target, DomEvents.DomEvent ev, boolean toggled, boolean oldChecked, int mods) {
        switch (act.tag) {
            case "a", "area": {
                String href = act.attr("href");
                if (href == null) {
                    return;
                }
                String trimmed = href.trim();
                if (trimmed.regionMatches(true, 0, "javascript:", 0, 11)) {
                    h.runScript(null, trimmed.substring(11));
                    return;
                }
                String abs = trimmed.isEmpty() ? null : h.resolveUrl(trimmed);
                if (abs == null || abs.equals(trimmed) && !abs.startsWith("http") && !abs.startsWith("#") && !abs.startsWith("hs:")) {
                    h.env.status(t("No se puede abrir este tipo de enlace.", "This kind of link cannot be opened."));
                    return;
                }
                h.env.follow(abs, act.hasAttr("download"));
                return;
            }
            case "button": {
                String bt = buttonType(act);
                Node form = ownerForm(h, act);
                if (form != null && bt.equals("submit")) {
                    submit(h, form, act, true);
                } else if (form != null && bt.equals("reset")) {
                    reset(h, form, true);
                }
                return;
            }
            case "input": {
                String t = typeOf(act);
                if (t.equals("checkbox") || t.equals("radio")) {
                    if (toggled && (t.equals("checkbox") || !oldChecked)) {
                        DomEvents.DomEvent in = DomEvents.create(h, "Event", "input", true, false);
                        DomEvents.dispatch(h, act, in);
                        DomEvents.fireSimple(h, act, "change", true, false);
                    }
                } else if (t.equals("submit") || t.equals("image")) {
                    Node form = ownerForm(h, act);
                    if (form != null) {
                        submit(h, form, act, true);
                    }
                } else if (t.equals("reset")) {
                    Node form = ownerForm(h, act);
                    if (form != null) {
                        reset(h, form, true);
                    }
                } else if (t.equals("file")) {
                    h.env.status(t("Subir archivos no está disponible en este navegador.", "File upload is not available in this browser."));
                }
                return;
            }
            case "label": {
                Node control = labelControl(h, act);
                if (control != null && !DomNodes.isInclusiveAncestor(control, target)) {
                    if (!DomSelector.isDisabled(control)) {
                        focus(h, control);
                        click(h, control, 0, 0, mods);
                    }
                }
                return;
            }
            case "summary": {
                Node d = act.parent;
                if (d.hasAttr("open")) {
                    d.removeAttr("open");
                } else {
                    d.setAttr("open", "");
                }
                Node.touch();
                DomEvents.fireSimple(h, d, "toggle", false, false);
                return;
            }
            default:
                return;
        }
    }

    static Node labelControl(DomHost h, Node label) {
        String f = label.attr("for");
        if (f != null) {
            Node[] hit = new Node[1];
            DomNodes.collect(h.doc, null, x -> {
                if (hit[0] == null && x.isElement() && f.equals(x.attr("id"))) {
                    hit[0] = x;
                }
                return false;
            });
            return hit[0] != null && LABELABLE.contains(hit[0].tag) ? hit[0] : null;
        }
        List<Node> found = new ArrayList<>();
        DomNodes.collect(label, found, x -> x.isElement() && LABELABLE.contains(x.tag) && !(x.is("input") && "hidden".equals(typeOf(x))));
        return found.isEmpty() ? null : found.get(0);
    }

    /** El usuario eligió una opción de un select. */
    static void chooseOption(DomHost h, Node select, int index) {
        List<Node> opts = optionsOf(select);
        if (index < 0 || index >= opts.size() || DomSelector.isDisabled(opts.get(index)) || DomSelector.isDisabled(select)) {
            return;
        }
        if (multiple(select)) {
            Node o = opts.get(index);
            o.formChecked = !o.isChecked();
            Node.touch();
        } else {
            if (selectedIndex(select) == index) {
                return;
            }
            selectIndex(h, select, index);
        }
        focus(h, select);
        DomEvents.dispatch(h, select, DomEvents.create(h, "Event", "input", true, false));
        DomEvents.fireSimple(h, select, "change", true, false);
        h.engine.runJobs();
    }

    // ------------------------------------------------------------------ teclado

    private static int[] selection(Node n, String v) {
        int caret = n.caret < 0 || n.caret > v.length() ? v.length() : n.caret;
        if (n.selAnchor >= 0 && n.selAnchor <= v.length() && n.selAnchor != caret) {
            return new int[] {Math.min(n.selAnchor, caret), Math.max(n.selAnchor, caret)};
        }
        return new int[] {caret, caret};
    }

    static String selectedText(Node n) {
        if (!isTextControl(n)) {
            return "";
        }
        String v = n.currentValue();
        int[] s = selection(n, v);
        return v.substring(s[0], s[1]);
    }

    private static void replace(DomHost h, Node n, int start, int end, String ins, String inputType) {
        String v = n.currentValue();
        n.formValue = v.substring(0, start) + ins + v.substring(end);
        n.caret = start + ins.length();
        n.selAnchor = -1;
        Node.touch();
        DomEvents.DomEvent e = DomEvents.create(h, "InputEvent", "input", true, false);
        DomEvents.put(e, "data", ins.isEmpty() ? Js.NULL : ins);
        DomEvents.put(e, "inputType", inputType);
        DomEvents.dispatch(h, n, e);
    }

    /** Texto escrito con el teclado en el campo con foco. */
    static boolean typeText(DomHost h, String text) {
        Node n = h.focused;
        if (!editable(n) || text.isEmpty()) {
            return false;
        }
        boolean area = n.is("textarea");
        String t = area ? text.replace("\r\n", "\n").replace('\r', '\n') : text.replace("\r", "").replace("\n", "");
        if (n.is("input") && typeOf(n).equals("number")) {
            StringBuilder keep = new StringBuilder();
            for (int i = 0; i < t.length(); i++) {
                if ("0123456789.-+eE".indexOf(t.charAt(i)) >= 0) {
                    keep.append(t.charAt(i));
                }
            }
            t = keep.toString();
        }
        String v = n.currentValue();
        int[] s = selection(n, v);
        String max = n.attr("maxlength");
        if (max != null) {
            try {
                int room = Integer.parseInt(max.trim()) - (v.length() - (s[1] - s[0]));
                if (room < t.length()) {
                    t = room <= 0 ? "" : t.substring(0, room);
                }
            } catch (NumberFormatException ignored) {
                // se ignora
            }
        }
        if (t.isEmpty()) {
            return true;
        }
        replace(h, n, s[0], s[1], t, "insertText");
        return true;
    }

    private static int wordLeft(String v, int i) {
        while (i > 0 && Character.isWhitespace(v.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(v.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private static int wordRight(String v, int i) {
        while (i < v.length() && Character.isWhitespace(v.charAt(i))) {
            i++;
        }
        while (i < v.length() && !Character.isWhitespace(v.charAt(i))) {
            i++;
        }
        return i;
    }

    private static void moveCaret(Node n, int to, boolean extend) {
        String v = n.currentValue();
        int caret = n.caret < 0 || n.caret > v.length() ? v.length() : n.caret;
        if (extend) {
            if (n.selAnchor < 0) {
                n.selAnchor = caret;
            }
        } else {
            n.selAnchor = -1;
        }
        n.caret = Math.max(0, Math.min(v.length(), to));
        if (n.selAnchor == n.caret) {
            n.selAnchor = -1;
        }
        Node.touch();
    }

    /** Una tecla de edición en el campo con foco. Verdadero si se usó. */
    static boolean editKey(DomHost h, String key, int mods) {
        Node n = h.focused;
        if (!isTextControl(n)) {
            return false;
        }
        boolean ctrl = (mods & DomEvents.CTRL) != 0;
        boolean shift = (mods & DomEvents.SHIFT) != 0;
        String v = n.currentValue();
        int[] s = selection(n, v);
        int caret = n.caret < 0 || n.caret > v.length() ? v.length() : n.caret;
        boolean ed = editable(n);
        boolean area = n.is("textarea");
        switch (key) {
            case "Backspace":
                if (!ed) {
                    return true;
                }
                if (s[0] != s[1]) {
                    replace(h, n, s[0], s[1], "", "deleteContentBackward");
                } else if (s[0] > 0) {
                    int from = ctrl ? wordLeft(v, s[0]) : s[0] - 1;
                    replace(h, n, from, s[0], "", "deleteContentBackward");
                }
                return true;
            case "Delete":
                if (!ed) {
                    return true;
                }
                if (s[0] != s[1]) {
                    replace(h, n, s[0], s[1], "", "deleteContentForward");
                } else if (s[1] < v.length()) {
                    int to = ctrl ? wordRight(v, s[1]) : s[1] + 1;
                    replace(h, n, s[1], to, "", "deleteContentForward");
                }
                return true;
            case "ArrowLeft":
                if (s[0] != s[1] && !shift) {
                    moveCaret(n, s[0], false);
                } else {
                    moveCaret(n, ctrl ? wordLeft(v, caret) : caret - 1, shift);
                }
                return true;
            case "ArrowRight":
                if (s[0] != s[1] && !shift) {
                    moveCaret(n, s[1], false);
                } else {
                    moveCaret(n, ctrl ? wordRight(v, caret) : caret + 1, shift);
                }
                return true;
            case "Home": {
                int to = ctrl || !area ? 0 : v.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
                if (area && !ctrl && caret > 0 && v.charAt(caret - 1) == '\n') {
                    to = caret;
                }
                moveCaret(n, to, shift);
                return true;
            }
            case "End": {
                int to = ctrl || !area ? v.length() : v.indexOf('\n', caret);
                moveCaret(n, to < 0 ? v.length() : to, shift);
                return true;
            }
            case "ArrowUp", "ArrowDown": {
                if (!area) {
                    moveCaret(n, key.equals("ArrowUp") ? 0 : v.length(), shift);
                    return true;
                }
                int lineStart = v.lastIndexOf('\n', Math.max(0, caret - 1));
                if (caret == 0 || (lineStart >= 0 && lineStart == caret - 1 && caret > 0 && v.charAt(caret - 1) == '\n')) {
                    lineStart = caret == 0 ? -1 : caret - 1;
                } else if (lineStart < 0) {
                    lineStart = -1;
                }
                int ls = v.lastIndexOf('\n', caret - 1) + 1;
                int col = caret - ls;
                if (key.equals("ArrowUp")) {
                    if (ls == 0) {
                        moveCaret(n, 0, shift);
                    } else {
                        int prevStart = v.lastIndexOf('\n', ls - 2) + 1;
                        moveCaret(n, Math.min(prevStart + col, ls - 1), shift);
                    }
                } else {
                    int nl = v.indexOf('\n', caret);
                    if (nl < 0) {
                        moveCaret(n, v.length(), shift);
                    } else {
                        int nextEnd = v.indexOf('\n', nl + 1);
                        if (nextEnd < 0) {
                            nextEnd = v.length();
                        }
                        moveCaret(n, Math.min(nl + 1 + col, nextEnd), shift);
                    }
                }
                return true;
            }
            case "Enter":
                if (area) {
                    if (ed) {
                        replace(h, n, s[0], s[1], "\n", "insertLineBreak");
                    }
                    return true;
                }
                implicitSubmit(h, n);
                return true;
            case "a", "A":
                if (ctrl) {
                    n.selAnchor = 0;
                    n.caret = v.length();
                    if (v.isEmpty()) {
                        n.selAnchor = -1;
                    }
                    Node.touch();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private static void implicitSubmit(DomHost h, Node field) {
        String before = h.focusValues.get(field);
        if (before != null && !before.equals(field.currentValue())) {
            h.focusValues.put(field, field.currentValue());
            DomEvents.fireSimple(h, field, "change", true, false);
        }
        Node form = ownerForm(h, field);
        if (form == null) {
            return;
        }
        Node btn = defaultButton(h, form);
        if (btn != null) {
            if (!DomSelector.isDisabled(btn)) {
                click(h, btn, 0, 0, 0);
            }
            return;
        }
        int blocking = 0;
        for (Node c : controlsOf(h, form)) {
            if (c.is("input") && isTextLike(typeOf(c))) {
                blocking++;
            }
        }
        if (blocking <= 1) {
            submit(h, form, null, true);
        }
    }

    /** Pegar texto en el campo con foco. */
    static boolean paste(DomHost h, String text) {
        return typeText(h, text);
    }

    /** Enter o espacio sobre el elemento con foco que no es un campo de texto. */
    static boolean keyActivate(DomHost h, String key) {
        Node f = h.focused;
        if (f == null || isTextControl(f)) {
            return false;
        }
        boolean enter = key.equals("Enter");
        boolean space = key.equals(" ");
        if (!enter && !space) {
            return false;
        }
        String tag = f.tag;
        boolean isLink = tag.equals("a") || tag.equals("area");
        boolean isButton = tag.equals("button") || (tag.equals("input") && !isTextLike(typeOf(f)));
        boolean check = tag.equals("input") && (typeOf(f).equals("checkbox") || typeOf(f).equals("radio"));
        boolean summary = tag.equals("summary");
        if ((enter && (isLink || (isButton && !check) || summary)) || (space && (isButton || summary))) {
            click(h, f, 0, 0, 0);
            h.engine.runJobs();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ objeto form con acceso por nombre

    static final class FormObj extends DomObj {
        final DomHost h;
        final Node form;

        FormObj(DomHost h, JsObject proto, Node form) {
            super(proto, form);
            this.h = h;
            this.form = form;
        }

        @Override
        public Prop getOwn(Object key) {
            Prop p = super.getOwn(key);
            if (p != null || !(key instanceof String s) || s.isEmpty()) {
                return p;
            }
            int idx = Js.arrayIndex(s);
            if (idx >= 0) {
                List<Node> cs = controlsOf(this.h, this.form);
                return idx < cs.size() ? new Prop(this.h.wrap(cs.get(idx)), Prop.E) : null;
            }
            if (this.proto != null && this.proto.hasProperty(s)) {
                return null;
            }
            List<Node> hits = new ArrayList<>();
            for (Node c : controlsOf(this.h, this.form)) {
                if (s.equals(c.attr("id")) || s.equals(c.attr("name"))) {
                    hits.add(c);
                }
            }
            if (hits.isEmpty()) {
                return null;
            }
            if (hits.size() == 1) {
                return new Prop(this.h.wrap(hits.get(0)), Prop.C);
            }
            return new Prop(DomLists.list(this.h, "RadioNodeList", false, () -> hits), Prop.C);
        }
    }

    // ------------------------------------------------------------------ interfaces de JavaScript

    private static JsPromise resolved(DomHost h, Object v) {
        JsPromise p = new JsPromise(h.r);
        p.resolve(v);
        return p;
    }

    private static JsObject tags(DomHost h, String iface, String... tags) {
        JsObject p = h.iface(iface, h.htmlElementProto);
        for (String t : tags) {
            h.tagProtos.put(t, p);
        }
        return p;
    }

    private static JsObject validityObject(DomHost h, Node c) {
        JsObject v = Js.newObject();
        String p = problem(h, c);
        boolean custom = h.customValidity.get(c) != null && !h.customValidity.get(c).isEmpty();
        v.setData("valid", p == null);
        v.setData("valueMissing", p != null && !custom && (p.equals(t("Completa este campo.", "Please fill out this field."))
                || p.equals(t("Marca esta casilla si quieres continuar.", "Please check this box if you want to proceed."))
                || p.equals(t("Elige una de estas opciones.", "Please select one of these options."))
                || p.equals(t("Elige un elemento de la lista.", "Please select an item in the list."))));
        v.setData("customError", custom);
        for (String k : new String[] {"typeMismatch", "patternMismatch", "tooLong", "tooShort", "rangeUnderflow", "rangeOverflow", "stepMismatch", "badInput"}) {
            v.setData(k, false);
        }
        return v;
    }

    private static void validityMembers(DomHost h, JsObject p) {
        h.getter(p, "validity", n -> validityObject(h, n));
        h.getter(p, "validationMessage", n -> {
            String m = problem(h, n);
            return m == null ? "" : m;
        });
        h.getter(p, "willValidate", n -> willValidate(n));
        h.method(p, "setCustomValidity", 1, (n, a) -> {
            h.customValidity.put(n, DomHost.str(a, 0));
            return Js.UNDEFINED;
        });
        h.method(p, "checkValidity", 0, (n, a) -> {
            if (!willValidate(n) || problem(h, n) == null) {
                return true;
            }
            DomEvents.fireSimple(h, n, "invalid", false, true);
            return false;
        });
        h.method(p, "reportValidity", 0, (n, a) -> {
            if (!willValidate(n) || problem(h, n) == null) {
                return true;
            }
            DomEvents.fireSimple(h, n, "invalid", false, true);
            h.env.status(problem(h, n));
            return false;
        });
    }

    private static void ctlCommon(DomHost h, JsObject p) {
        h.attrStr(p, "name", "name");
        h.attrBool(p, "disabled", "disabled");
        h.attrBool(p, "required", "required");
        h.getter(p, "form", n -> h.w(ownerForm(h, n)));
        h.getter(p, "labels", n -> DomLists.list(h, "NodeList", true, () -> {
            List<Node> out = new ArrayList<>();
            DomNodes.collect(h.doc, out, x -> x.is("label") && labelControl(h, x) == n);
            return out;
        }));
        h.attrStr(p, "formAction", "formaction");
        h.attrStr(p, "formEnctype", "formenctype");
        h.attrStr(p, "formMethod", "formmethod");
        h.attrBool(p, "formNoValidate", "formnovalidate");
        h.attrStr(p, "formTarget", "formtarget");
        validityMembers(h, p);
    }

    private static void selectionMembers(DomHost h, JsObject p) {
        h.accessor(p, "selectionStart", n -> Js.num(selection(n, n.currentValue())[0]), (n, v) -> {
            String val = n.currentValue();
            int s = Math.max(0, Math.min(val.length(), (int) Js.toInteger(v)));
            int[] cur = selection(n, val);
            n.selAnchor = s;
            n.caret = Math.max(s, cur[1]);
            if (n.selAnchor == n.caret) {
                n.selAnchor = -1;
            }
            Node.touch();
        });
        h.accessor(p, "selectionEnd", n -> Js.num(selection(n, n.currentValue())[1]), (n, v) -> {
            String val = n.currentValue();
            int e = Math.max(0, Math.min(val.length(), (int) Js.toInteger(v)));
            int[] cur = selection(n, val);
            n.selAnchor = Math.min(cur[0], e);
            n.caret = e;
            if (n.selAnchor == n.caret) {
                n.selAnchor = -1;
            }
            Node.touch();
        });
        h.accessor(p, "selectionDirection", n -> "forward", (n, v) -> { });
        h.method(p, "setSelectionRange", 2, (n, a) -> {
            String val = n.currentValue();
            int s = Math.max(0, Math.min(val.length(), (int) Js.toInteger(Js.arg(a, 0))));
            int e = Math.max(0, Math.min(val.length(), (int) Js.toInteger(Js.arg(a, 1))));
            n.selAnchor = s == e ? -1 : Math.min(s, e);
            n.caret = Math.max(s, e);
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(p, "select", 0, (n, a) -> {
            String val = n.currentValue();
            focus(h, n);
            n.selAnchor = val.isEmpty() ? -1 : 0;
            n.caret = val.length();
            Node.touch();
            return Js.UNDEFINED;
        });
        h.method(p, "setRangeText", 1, (n, a) -> {
            String val = n.currentValue();
            int[] cur = selection(n, val);
            int s = a.length >= 3 ? Math.max(0, Math.min(val.length(), (int) Js.toInteger(a[1]))) : cur[0];
            int e = a.length >= 3 ? Math.max(s, Math.min(val.length(), (int) Js.toInteger(a[2]))) : cur[1];
            String ins = DomHost.str(a, 0);
            n.formValue = val.substring(0, s) + ins + val.substring(e);
            n.caret = s + ins.length();
            n.selAnchor = -1;
            Node.touch();
            return Js.UNDEFINED;
        });
    }

    static void install(DomHost h) {
        Realm r = h.r;
        JsObject he = h.htmlElementProto;
        h.method(he, "click", 0, (n, a) -> {
            if (!insideDisabled(n)) {
                click(h, n, 0, 0, 0);
                h.engine.runJobs();
            }
            return Js.UNDEFINED;
        });
        h.method(he, "focus", 0, (n, a) -> {
            focus(h, n);
            return Js.UNDEFINED;
        });
        h.method(he, "blur", 0, (n, a) -> {
            if (h.focused == n) {
                blur(h);
            }
            return Js.UNDEFINED;
        });

        // form
        JsObject form = tags(h, "HTMLFormElement", "form");
        h.getter(form, "elements", n -> ((DomObj) h.wrap(n)).cached("elements", () -> DomLists.list(h, "HTMLFormControlsCollection", true,
                () -> controlsOf(h, n))));
        h.getter(form, "length", n -> Js.num(controlsOf(h, n).size()));
        h.attrStr(form, "name", "name");
        h.attrStr(form, "acceptCharset", "accept-charset");
        h.attrStr(form, "autocomplete", "autocomplete");
        h.attrBool(form, "noValidate", "novalidate");
        h.attrStr(form, "target", "target");
        h.attrEnum(form, "method", "method", "get", "get", "post", "dialog");
        h.accessor(form, "action", n -> {
            String v = n.attr("action");
            return v == null || v.isBlank() ? h.resolveUrl("") : h.resolveUrl(v);
        }, (n, v) -> {
            n.setAttr("action", Js.toStr(v));
            Node.touch();
        });
        h.attrEnum(form, "enctype", "enctype", "application/x-www-form-urlencoded", "application/x-www-form-urlencoded",
                "multipart/form-data", "text/plain");
        h.attrEnum(form, "encoding", "enctype", "application/x-www-form-urlencoded", "application/x-www-form-urlencoded",
                "multipart/form-data", "text/plain");
        h.method(form, "submit", 0, (n, a) -> {
            doSubmit(h, n, null);
            return Js.UNDEFINED;
        });
        h.method(form, "requestSubmit", 0, (n, a) -> {
            Node sub = DomHost.asNode(Js.arg(a, 0));
            if (sub != null && ownerForm(h, sub) != n) {
                throw h.domError("NotFoundError", "Failed to execute 'requestSubmit' on 'HTMLFormElement': The specified element is not owned by this form element.");
            }
            submit(h, n, sub, true);
            return Js.UNDEFINED;
        });
        h.method(form, "reset", 0, (n, a) -> {
            reset(h, n, true);
            return Js.UNDEFINED;
        });
        h.method(form, "checkValidity", 0, (n, a) -> {
            boolean ok = true;
            for (Node c : controlsOf(h, n)) {
                if (willValidate(c) && problem(h, c) != null) {
                    DomEvents.fireSimple(h, c, "invalid", false, true);
                    ok = false;
                }
            }
            return ok;
        });
        h.method(form, "reportValidity", 0, (n, a) -> validateForm(h, n));

        // input
        JsObject input = tags(h, "HTMLInputElement", "input");
        ctlCommon(h, input);
        selectionMembers(h, input);
        h.accessor(input, "type", n -> typeOf(n), (n, v) -> {
            n.setAttr("type", Js.toStr(v));
            Node.touch();
        });
        h.accessor(input, "value", n -> inputValue(n), (n, v) -> setInputValue(n, v == Js.NULL ? "" : Js.toStr(v)));
        h.accessor(input, "defaultValue", n -> n.attr("value") == null ? "" : n.attr("value"), (n, v) -> {
            n.setAttr("value", Js.toStr(v));
            Node.touch();
        });
        h.accessor(input, "checked", n -> n.isChecked(), (n, v) -> setChecked(h, n, Js.truthy(v)));
        h.attrBool(input, "defaultChecked", "checked");
        h.accessor(input, "indeterminate", n -> n.hasAttr("data-indeterminate"), (n, v) -> {
            if (Js.truthy(v)) {
                n.setAttr("data-indeterminate", "");
            } else {
                n.removeAttr("data-indeterminate");
            }
            Node.touch();
        });
        h.attrStr(input, "placeholder", "placeholder");
        h.attrBool(input, "readOnly", "readonly");
        h.attrStr(input, "autocomplete", "autocomplete");
        h.attrStr(input, "min", "min");
        h.attrStr(input, "max", "max");
        h.attrStr(input, "step", "step");
        h.attrStr(input, "pattern", "pattern");
        h.attrStr(input, "accept", "accept");
        h.attrStr(input, "alt", "alt");
        h.attrUrl(input, "src", "src");
        h.attrBool(input, "multiple", "multiple");
        h.attrInt(input, "maxLength", "maxlength", -1);
        h.attrInt(input, "minLength", "minlength", -1);
        h.attrInt(input, "size", "size", 20);
        h.attrInt(input, "width", "width", 0);
        h.attrInt(input, "height", "height", 0);
        h.getter(input, "files", n -> typeOf(n).equals("file") ? Lib.array(r, new ArrayList<Object>()) : Js.NULL);
        h.getter(input, "list", n -> Js.NULL);
        h.accessor(input, "valueAsNumber", n -> {
            try {
                return Js.num(Double.parseDouble(inputValue(n).trim()));
            } catch (NumberFormatException e) {
                return Js.NAN;
            }
        }, (n, v) -> setInputValue(n, Js.numToString(Js.toNumber(v))));
        h.method(input, "stepUp", 0, (n, a) -> {
            stepNumber(n, a.length > 0 ? (int) Js.toInteger(a[0]) : 1);
            return Js.UNDEFINED;
        });
        h.method(input, "stepDown", 0, (n, a) -> {
            stepNumber(n, -(a.length > 0 ? (int) Js.toInteger(a[0]) : 1));
            return Js.UNDEFINED;
        });
        h.method(input, "showPicker", 0, (n, a) -> Js.UNDEFINED);

        // textarea
        JsObject ta = tags(h, "HTMLTextAreaElement", "textarea");
        ctlCommon(h, ta);
        selectionMembers(h, ta);
        h.getter(ta, "type", n -> "textarea");
        h.accessor(ta, "value", n -> n.currentValue().replace("\r\n", "\n"), (n, v) -> {
            n.formValue = v == Js.NULL ? "" : Js.toStr(v);
            n.caret = -1;
            n.selAnchor = -1;
            Node.touch();
        });
        h.accessor(ta, "defaultValue", n -> n.rawText(), (n, v) -> DomNodes.setText(h, n, Js.toStr(v)));
        h.getter(ta, "textLength", n -> Js.num(n.currentValue().length()));
        h.attrStr(ta, "placeholder", "placeholder");
        h.attrBool(ta, "readOnly", "readonly");
        h.attrStr(ta, "wrap", "wrap");
        h.attrStr(ta, "autocomplete", "autocomplete");
        h.attrInt(ta, "rows", "rows", 2);
        h.attrInt(ta, "cols", "cols", 20);
        h.attrInt(ta, "maxLength", "maxlength", -1);
        h.attrInt(ta, "minLength", "minlength", -1);

        // button
        JsObject button = tags(h, "HTMLButtonElement", "button");
        ctlCommon(h, button);
        h.accessor(button, "type", n -> buttonType(n), (n, v) -> {
            n.setAttr("type", Js.toStr(v));
            Node.touch();
        });
        h.attrStr(button, "value", "value");

        // select / option
        JsObject select = tags(h, "HTMLSelectElement", "select");
        ctlCommon(h, select);
        h.attrBool(select, "multiple", "multiple");
        h.attrInt(select, "size", "size", 0);
        h.attrStr(select, "autocomplete", "autocomplete");
        h.getter(select, "type", n -> multiple(n) ? "select-multiple" : "select-one");
        h.getter(select, "options", n -> ((DomObj) h.wrap(n)).cached("options", () -> DomLists.list(h, "HTMLOptionsCollection", true,
                () -> optionsOf(n))));
        h.getter(select, "selectedOptions", n -> DomLists.list(h, "HTMLCollection", true, () -> selectedOptions(n)));
        h.getter(select, "length", n -> Js.num(optionsOf(n).size()));
        h.accessor(select, "selectedIndex", n -> Js.num(selectedIndex(n)), (n, v) -> {
            int i = (int) Js.toInteger(v);
            List<Node> opts = optionsOf(n);
            selectIndex(h, n, i >= 0 && i < opts.size() ? i : -1);
        });
        h.accessor(select, "value", n -> {
            List<Node> sel = selectedOptions(n);
            return sel.isEmpty() ? "" : optionValue(sel.get(0));
        }, (n, v) -> {
            String want = Js.toStr(v);
            List<Node> opts = optionsOf(n);
            int found = -1;
            for (int i = 0; i < opts.size() && found < 0; i++) {
                if (optionValue(opts.get(i)).equals(want)) {
                    found = i;
                }
            }
            selectIndex(h, n, found);
        });
        h.method(select, "item", 1, (n, a) -> {
            List<Node> opts = optionsOf(n);
            double i = Js.toInteger(Js.arg(a, 0));
            return i >= 0 && i < opts.size() ? h.wrap(opts.get((int) i)) : Js.NULL;
        });
        h.method(select, "namedItem", 1, (n, a) -> {
            String name = DomHost.str(a, 0);
            for (Node o : optionsOf(n)) {
                if (name.equals(o.attr("id")) || name.equals(o.attr("name"))) {
                    return h.wrap(o);
                }
            }
            return Js.NULL;
        });
        h.method(select, "add", 1, (n, a) -> {
            Node o = DomHost.nodeArg(a, 0, "add");
            Object b = Js.arg(a, 1);
            Node ref = null;
            if (b instanceof Number num) {
                List<Node> opts = optionsOf(n);
                int i = num.intValue();
                ref = i >= 0 && i < opts.size() ? opts.get(i) : null;
            } else {
                ref = DomHost.asNode(b);
            }
            Node par = ref != null && ref.parent != null ? ref.parent : n;
            DomNodes.insertNode(h, par, o, ref);
            return Js.UNDEFINED;
        });
        h.method(select, "remove", 0, (n, a) -> {
            if (a.length == 0) {
                DomNodes.detach(h, n);
            } else {
                List<Node> opts = optionsOf(n);
                int i = (int) Js.toInteger(a[0]);
                if (i >= 0 && i < opts.size()) {
                    DomNodes.detach(h, opts.get(i));
                }
            }
            return Js.UNDEFINED;
        });
        h.method(select, "showPicker", 0, (n, a) -> Js.UNDEFINED);
        JsObject optgroup = tags(h, "HTMLOptGroupElement", "optgroup");
        h.attrBool(optgroup, "disabled", "disabled");
        h.attrStr(optgroup, "label", "label");
        JsObject option = tags(h, "HTMLOptionElement", "option");
        h.attrBool(option, "disabled", "disabled");
        h.attrBool(option, "defaultSelected", "selected");
        h.accessor(option, "label", n -> n.attr("label") != null ? n.attr("label") : n.textContent(), (n, v) -> {
            n.setAttr("label", Js.toStr(v));
            Node.touch();
        });
        h.accessor(option, "value", n -> optionValue(n), (n, v) -> {
            n.setAttr("value", Js.toStr(v));
            Node.touch();
        });
        h.accessor(option, "text", n -> n.textContent(), (n, v) -> DomNodes.setText(h, n, Js.toStr(v)));
        h.accessor(option, "selected", n -> optionSelected(n), (n, v) -> {
            Node sel = selectOf(n);
            boolean on = Js.truthy(v);
            if (sel != null && !multiple(sel)) {
                List<Node> opts = optionsOf(sel);
                selectIndex(h, sel, on ? opts.indexOf(n) : -1);
            } else {
                n.formChecked = on;
                Node.touch();
            }
        });
        h.getter(option, "index", n -> {
            Node sel = selectOf(n);
            return Js.num(sel == null ? 0 : optionsOf(sel).indexOf(n));
        });
        h.getter(option, "form", n -> {
            Node sel = selectOf(n);
            return sel == null ? Js.NULL : h.w(ownerForm(h, sel));
        });
        Lib.ctor(r, "Option", 0, option, (t, a) -> {
            throw Js.typeError("Class constructor Option cannot be invoked without 'new'");
        }, (a, nt) -> {
            Node o = Node.element("option", null);
            if (a.length > 0 && a[0] != Js.UNDEFINED) {
                o.add(Node.text(Js.toStr(a[0])));
            }
            if (a.length > 1 && a[1] != Js.UNDEFINED) {
                o.setAttr("value", Js.toStr(a[1]));
            }
            if (a.length > 2 && Js.truthy(a[2])) {
                o.setAttr("selected", "");
            }
            if (a.length > 3 && Js.truthy(a[3])) {
                o.formChecked = true;
            }
            DomObj d = new DomObj(Lib.protoFrom(nt, option), o);
            o.host = d;
            return d;
        });
        JsObject opts = h.protos.get("HTMLOptionsCollection");
        Lib.method(r, opts, "add", 1, (t, a) -> {
            DomLists.DomList l = (DomLists.DomList) t;
            Node sel = null;
            List<Node> items = l.items();
            if (!items.isEmpty()) {
                sel = selectOf(items.get(0));
            }
            if (sel != null) {
                Node o = DomHost.nodeArg(a, 0, "add");
                DomNodes.insertNode(h, sel, o, null);
            }
            return Js.UNDEFINED;
        });
        Lib.method(r, opts, "remove", 1, (t, a) -> {
            DomLists.DomList l = (DomLists.DomList) t;
            List<Node> items = l.items();
            int i = (int) Js.toInteger(Js.arg(a, 0));
            if (i >= 0 && i < items.size()) {
                DomNodes.detach(h, items.get(i));
            }
            return Js.UNDEFINED;
        });

        // label, fieldset, output
        JsObject label = tags(h, "HTMLLabelElement", "label");
        h.attrStr(label, "htmlFor", "for");
        h.getter(label, "control", n -> h.w(labelControl(h, n)));
        h.getter(label, "form", n -> {
            Node c = labelControl(h, n);
            return c == null ? Js.NULL : h.w(ownerForm(h, c));
        });
        JsObject fieldset = tags(h, "HTMLFieldSetElement", "fieldset");
        h.attrBool(fieldset, "disabled", "disabled");
        h.attrStr(fieldset, "name", "name");
        h.getter(fieldset, "type", n -> "fieldset");
        h.getter(fieldset, "form", n -> h.w(ownerForm(h, n)));
        h.getter(fieldset, "elements", n -> DomLists.list(h, "HTMLCollection", true, () -> {
            List<Node> out = new ArrayList<>();
            DomNodes.collect(n, out, DomForms::isListed);
            return out;
        }));
        JsObject output = tags(h, "HTMLOutputElement", "output");
        h.attrStr(output, "name", "name");
        h.attrStr(output, "htmlFor", "for");
        h.getter(output, "form", n -> h.w(ownerForm(h, n)));
        h.getter(output, "type", n -> "output");
        h.accessor(output, "value", n -> n.rawText(), (n, v) -> DomNodes.setText(h, n, Js.toStr(v)));
        h.accessor(output, "defaultValue", n -> n.rawText(), (n, v) -> DomNodes.setText(h, n, Js.toStr(v)));
    }

    private static void stepNumber(Node n, int times) {
        String t = typeOf(n);
        if (!t.equals("number") && !t.equals("range")) {
            return;
        }
        double step = 1;
        try {
            if (n.attr("step") != null) {
                step = Double.parseDouble(n.attr("step").trim());
            }
        } catch (NumberFormatException ignored) {
            // paso malo: 1
        }
        double cur = 0;
        try {
            cur = Double.parseDouble(inputValue(n).trim());
        } catch (NumberFormatException ignored) {
            // vacío: 0
        }
        double v = cur + step * times;
        try {
            if (n.attr("max") != null) {
                v = Math.min(v, Double.parseDouble(n.attr("max").trim()));
            }
            if (n.attr("min") != null) {
                v = Math.max(v, Double.parseDouble(n.attr("min").trim()));
            }
        } catch (NumberFormatException ignored) {
            // límites malos
        }
        setInputValue(n, Js.numToString(v));
    }
}
