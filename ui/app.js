// Amatista Launcher — lógica de la interfaz (sin frameworks, para que cargue al instante).
"use strict";

const T = window.__TAURI__;
const invoke = (cmd, args) => T.core.invoke(cmd, args);
const listen = (ev, fn) => T.event.listen(ev, fn);
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

const state = {
  settings: null,
  instances: [],
  accounts: [],
  canLocal: false,
  running: new Set(),
  loader: "vanilla",
  versions: [],
  content: { kind: "mod", source: "modrinth", offset: 0, total: 0 },
};

// ---------------- Utilidades ----------------

function openUrl(url) {
  return T.opener.openUrl(url).catch(() => {});
}

function fmtNum(n) {
  n = Number(n || 0);
  if (n >= 1e6) return (n / 1e6).toFixed(1).replace(".0", "") + " M";
  if (n >= 1e3) return (n / 1e3).toFixed(1).replace(".0", "") + " k";
  return String(n);
}

function avatar(uuid) {
  return uuid ? `https://mc-heads.net/avatar/${uuid}/64` : "img/logo.png";
}
// Si la cabeza del jugador no carga (sin internet), se usa el logo.
document.addEventListener("error", (e) => {
  const img = e.target;
  if (img.tagName === "IMG" && !img.dataset.fallback) { img.dataset.fallback = "1"; img.src = "img/logo.png"; }
}, true);

const LOADER = {
  vanilla: { icon: "🌱", name: "Vanilla" },
  forge: { icon: "⚒️", name: "Forge" },
  optifine: { icon: "✨", name: "OptiFine" },
};

// Tareas en curso (esquina inferior derecha).
const tasks = new Map();
function taskEl(name) {
  let t = tasks.get(name);
  if (!t) {
    const el = document.createElement("div");
    el.className = "task";
    el.innerHTML = `<b></b><small></small><div class="bar indet"><i></i></div>`;
    $("b", el).textContent = name;
    $("#tasks").appendChild(el);
    t = { el, timer: null };
    tasks.set(name, t);
  }
  clearTimeout(t.timer);
  return t;
}
function taskProgress({ task, done, total, message }) {
  const t = taskEl(task);
  $("small", t.el).textContent = message || "";
  const bar = $(".bar", t.el);
  if (total > 0) {
    bar.classList.remove("indet");
    $("i", bar).style.width = Math.min(100, (done / total) * 100) + "%";
  } else {
    bar.classList.add("indet");
  }
  if (total > 0 && done >= total) finishTask(task, true, message);
}
function finishTask(name, ok, message) {
  const t = taskEl(name);
  t.el.classList.toggle("ok", !!ok);
  t.el.classList.toggle("err", !ok);
  if (message) $("small", t.el).textContent = message;
  const bar = $(".bar", t.el);
  bar.classList.remove("indet");
  $("i", bar).style.width = "100%";
  t.timer = setTimeout(() => { t.el.remove(); tasks.delete(name); }, ok ? 2500 : 9000);
}
function toast(message, ok = true) {
  const name = (ok ? "✔ " : "✖ ") + message;
  taskEl(name);
  finishTask(name, ok, "");
}

function modal(html) {
  $("#modal-body").innerHTML = html;
  $("#modal").classList.add("show");
}
function closeModal() {
  $("#modal").classList.remove("show");
}
function confirmBox(title, text, okLabel = "Aceptar") {
  return new Promise((resolve) => {
    modal(`<h3>${esc(title)}</h3><p>${esc(text)}</p><div class="row"><button class="btn" id="m-no">Cancelar</button><button class="btn primary" id="m-yes">${esc(okLabel)}</button></div>`);
    $("#m-no").onclick = () => { closeModal(); resolve(false); };
    $("#m-yes").onclick = () => { closeModal(); resolve(true); };
  });
}

// ---------------- Navegación ----------------

function go(page) {
  $$("#nav button").forEach((b) => b.classList.toggle("active", b.dataset.page === page));
  $$(".page").forEach((p) => p.classList.toggle("active", p.id === "page-" + page));
  if (page === "content" && !$("#results").children.length) searchContent(true);
}
$$("#nav button").forEach((b) => (b.onclick = () => go(b.dataset.page)));
$("#account-chip").onclick = () => go("accounts");
document.addEventListener("click", (e) => {
  const a = e.target.closest("[data-url]");
  if (a) { e.preventDefault(); openUrl(a.dataset.url); }
});

// ---------------- Instancias / Jugar ----------------

function selectedInstance() {
  return state.instances.find((i) => i.id === state.settings.selected_instance) || state.instances[0];
}
function selectedAccount() {
  return state.accounts.find((a) => a.uuid === state.settings.selected_account) || state.accounts[0];
}

async function loadInstances() {
  state.instances = await invoke("instances_list");
  renderInstances();
  renderTargetInstances();
}

function renderInstances() {
  const box = $("#instances");
  const sel = selectedInstance();
  if (!state.instances.length) {
    box.innerHTML = `<div class="empty">Aún no tienes instancias.<br><br><button class="btn primary" onclick="go('install')">Instalar Minecraft</button></div>`;
  } else {
    box.innerHTML = state.instances.map((i) => {
      const l = LOADER[i.loader] || LOADER.vanilla;
      const running = state.running.has(i.id);
      return `<div class="instance ${sel && sel.id === i.id ? "selected" : ""}" data-id="${esc(i.id)}">
        <div class="top"><div class="ic">${l.icon}</div><div style="min-width:0">
          <b>${esc(i.name)} ${running ? '<span class="running-dot" title="En ejecución"></span>' : ""}</b>
          <small>${esc(i.mc_version)} · <span class="badge ${esc(i.loader)}">${l.name}${i.loader_version ? " " + esc(i.loader_version) : ""}</span></small>
        </div></div>
        <div class="actions">
          <button class="btn small" data-act="folder" title="Abrir carpeta"><svg><use href="#i-folder"/></svg></button>
          <button class="btn small" data-act="mods" title="Contenido">Contenido</button>
          <button class="btn small danger" data-act="delete" title="Eliminar"><svg><use href="#i-trash"/></svg></button>
        </div>
      </div>`;
    }).join("");
  }
  renderHero();
}

function renderHero() {
  const i = selectedInstance();
  const btn = $("#play-btn");
  if (!i) {
    $("#hero-kind").textContent = "Sin instancias";
    $("#hero-title").textContent = "Instala tu primer juego";
    $("#hero-sub").textContent = "Ve a Instalar y elige Vanilla, OptiFine o Forge.";
    btn.disabled = true;
    return;
  }
  const l = LOADER[i.loader] || LOADER.vanilla;
  $("#hero-kind").textContent = l.name;
  $("#hero-title").textContent = i.name;
  const acc = selectedAccount();
  $("#hero-sub").textContent = `Minecraft ${i.mc_version}` + (acc ? ` · Jugando como ${acc.name}` : " · Añade una cuenta para jugar");
  btn.disabled = false;
  const running = state.running.has(i.id);
  btn.classList.toggle("stop", running);
  btn.innerHTML = running ? "DETENER" : `<svg><use href="#i-play"/></svg>JUGAR`;
}

$("#instances").addEventListener("click", async (e) => {
  const card = e.target.closest(".instance");
  if (!card) return;
  const id = card.dataset.id;
  const act = e.target.closest("[data-act]")?.dataset.act;
  if (act === "folder") {
    const dir = await invoke("instance_dir", { id });
    T.opener.openPath(dir).catch(() => {});
    return;
  }
  if (act === "mods") {
    state.settings.selected_instance = id;
    await saveSettings();
    $("#target-instance").value = id;
    go("content");
    return;
  }
  if (act === "delete") {
    const inst = state.instances.find((i) => i.id === id);
    if (await confirmBox("Eliminar instancia", `Se borrará "${inst.name}" con sus mundos y mods. No se puede deshacer.`, "Eliminar")) {
      try { await invoke("instance_delete", { id }); toast("Instancia eliminada"); } catch (err) { toast(err, false); }
      loadInstances();
    }
    return;
  }
  state.settings.selected_instance = id;
  saveSettings();
  renderInstances();
});

$("#play-btn").onclick = async () => {
  const i = selectedInstance();
  if (!i) return go("install");
  if (state.running.has(i.id)) {
    try { await invoke("kill_instance", { id: i.id }); } catch (err) { toast(err, false); }
    return;
  }
  const acc = selectedAccount();
  if (!acc) { toast("Primero inicia sesión en Cuentas", false); return go("accounts"); }
  const btn = $("#play-btn");
  btn.disabled = true;
  btn.innerHTML = `<span class="spinner"></span> Preparando…`;
  try {
    await invoke("launch_instance", { id: i.id, account: acc.uuid });
  } catch (err) {
    finishTask(`Iniciando ${i.name}`, false, String(err));
  }
  btn.disabled = false;
  renderHero();
};

// Registro del juego (limitado para no consumir memoria).
const consoleEl = $("#console");
let logLines = 0;
$("#toggle-console").onclick = () => {
  consoleEl.hidden = !consoleEl.hidden;
  $("#toggle-console").textContent = consoleEl.hidden ? "Mostrar registro del juego" : "Ocultar registro del juego";
};
function appendLog(line, isErr) {
  const div = document.createElement("div");
  if (isErr) div.className = "e";
  div.textContent = line;
  consoleEl.appendChild(div);
  if (++logLines > 1500) { consoleEl.firstChild.remove(); logLines--; }
  if (!consoleEl.hidden) consoleEl.scrollTop = consoleEl.scrollHeight;
}

// ---------------- Instalar ----------------

async function loadVersions() {
  const snapshots = $("#show-snapshots").checked && state.loader === "vanilla";
  try {
    state.versions = await invoke("list_versions", { snapshots });
  } catch (err) {
    toast(err, false);
    state.versions = [];
  }
  $("#mc-version").innerHTML = state.versions
    .map((v) => `<option value="${esc(v.id)}">${esc(v.id)}${v.latest ? " (última)" : ""}${v.type !== "release" ? " · " + esc(v.type) : ""}${v.installed ? " ✓" : ""}</option>`)
    .join("");
  if (state.loader === "forge") loadForgeVersions();
}

async function loadForgeVersions() {
  const mc = $("#mc-version").value;
  const sel = $("#forge-version");
  sel.innerHTML = `<option>Cargando…</option>`;
  try {
    const list = await invoke("forge_versions", { mc });
    if (!list.length) {
      sel.innerHTML = `<option value="">Forge no está disponible para ${esc(mc)}</option>`;
      return;
    }
    sel.innerHTML = list
      .map((v) => `<option value="${esc(v.full)}">${esc(v.forge)}${v.recommended ? " ★ recomendada" : ""}${v.latest ? " · más reciente" : ""}</option>`)
      .join("");
    const rec = list.find((v) => v.recommended) || list[0];
    sel.value = rec.full;
  } catch (err) {
    sel.innerHTML = `<option value="">No se pudo cargar la lista</option>`;
    toast(err, false);
  }
}

function setLoader(loader) {
  state.loader = loader;
  $$(".loader-card").forEach((c) => c.classList.toggle("active", c.dataset.loader === loader));
  $("#forge-fields").hidden = loader !== "forge";
  $("#optifine-fields").hidden = loader !== "optifine";
  $("#install-actions").hidden = loader === "optifine";
  $("#snap-field").hidden = loader !== "vanilla";
  $("#mc-version").closest(".field").hidden = loader === "optifine";
  if (loader === "forge") loadForgeVersions();
}
$$(".loader-card").forEach((c) => (c.onclick = () => setLoader(c.dataset.loader)));
$("#show-snapshots").onchange = loadVersions;
$("#mc-version").onchange = () => state.loader === "forge" && loadForgeVersions();

$("#install-btn").onclick = async () => {
  const btn = $("#install-btn");
  btn.disabled = true;
  try {
    let inst;
    if (state.loader === "forge") {
      const full = $("#forge-version").value;
      if (!full) throw "Elige una versión de Forge";
      inst = await invoke("install_forge", { full });
    } else {
      inst = await invoke("install_vanilla", { mc: $("#mc-version").value });
    }
    toast(`${inst.name} instalado`);
    state.settings.selected_instance = inst.id;
    await saveSettings();
    await loadInstances();
    loadVersions();
    go("play");
  } catch (err) {
    toast(String(err), false);
  }
  btn.disabled = false;
};

$("#optifine-open").onclick = async () => {
  const info = await invoke("optifine_info");
  openUrl(info.download_page);
};
$("#optifine-pick").onclick = async () => {
  const file = await T.dialog.open({ title: "Elige el archivo de OptiFine", filters: [{ name: "OptiFine", extensions: ["jar"] }] });
  if (!file) return;
  try {
    const inst = await invoke("install_optifine", { path: file });
    toast(`${inst.name} instalado`);
    state.settings.selected_instance = inst.id;
    await saveSettings();
    await loadInstances();
    go("play");
  } catch (err) {
    toast(String(err), false);
  }
};

// ---------------- Contenido ----------------

let gameVersionsLoaded = false;
async function ensureGameVersions() {
  if (gameVersionsLoaded) return;
  try {
    const list = await invoke("content_game_versions");
    $("#filter-version").innerHTML = `<option value="">Todas las versiones</option>` + list.map((v) => `<option>${esc(v)}</option>`).join("");
    gameVersionsLoaded = true;
    const inst = selectedInstance();
    if (inst) $("#filter-version").value = inst.mc_version;
  } catch (_) { /* sin conexión */ }
}

function renderTargetInstances() {
  const sel = $("#target-instance");
  const current = selectedInstance();
  sel.innerHTML = state.instances.length
    ? state.instances.map((i) => `<option value="${esc(i.id)}">Instalar en: ${esc(i.name)}</option>`).join("")
    : `<option value="">Sin instancias</option>`;
  if (current) sel.value = current.id;
}

function updateFilterLayout() {
  const k = state.content.kind;
  const usesLoader = k === "mod" || k === "modpack";
  $("#filter-loader").hidden = !usesLoader;
  $("#filters").classList.toggle("no-loader", !usesLoader);
  $("#target-instance").hidden = k === "modpack";
  if (k === "modpack") $("#filters").style.gridTemplateColumns = "1fr 170px 150px";
  else $("#filters").style.gridTemplateColumns = "";
}

let searchSeq = 0;
async function searchContent(reset) {
  await ensureGameVersions();
  const c = state.content;
  if (reset) { c.offset = 0; $("#results").innerHTML = ""; }
  const seq = ++searchSeq;
  const args = {
    source: c.source,
    kind: c.kind,
    query: $("#search").value.trim(),
    mc: $("#filter-version").value,
    loader: $("#filter-loader").value,
    offset: c.offset,
  };
  if (reset) $("#results").innerHTML = `<div class="empty"><span class="spinner"></span></div>`;
  try {
    const r = await invoke("content_search", args);
    if (seq !== searchSeq) return; // llegó una búsqueda más nueva
    if (reset) $("#results").innerHTML = "";
    c.total = r.total || 0;
    c.offset += r.hits.length;
    if (!r.hits.length && reset) $("#results").innerHTML = `<div class="empty">No se encontró nada con esos filtros.</div>`;
    const frag = document.createDocumentFragment();
    for (const h of r.hits) {
      const div = document.createElement("div");
      div.className = "result";
      div.innerHTML = `${h.icon ? `<img loading="lazy" src="${esc(h.icon)}" alt="">` : `<div class="noimg"></div>`}
        <div class="body">
          <b title="${esc(h.title)}">${esc(h.title)}</b>
          <p>${esc(h.description)}</p>
          <div class="meta"><span>⬇ ${fmtNum(h.downloads)}</span><span>· ${esc(h.author || "")}</span>
            <button class="btn small ghost" data-open="${esc(h.url)}" title="Ver página"><svg><use href="#i-ext"/></svg></button>
            <button class="btn small primary" data-install>${c.kind === "modpack" ? "Instalar modpack" : "Instalar"}</button>
          </div>
        </div>`;
      $("[data-install]", div).onclick = (e) => installContent(h, e.currentTarget);
      $("[data-open]", div).onclick = () => openUrl(h.url);
      frag.appendChild(div);
    }
    $("#results").appendChild(frag);
    $("#load-more").hidden = c.offset >= c.total;
  } catch (err) {
    if (seq !== searchSeq) return;
    $("#results").innerHTML = `<div class="empty">${esc(err)}</div>`;
    $("#load-more").hidden = true;
  }
}

async function installContent(h, btn) {
  btn.disabled = true;
  const old = btn.textContent;
  btn.innerHTML = `<span class="spinner"></span>`;
  try {
    if (state.content.kind === "modpack") {
      const inst = await invoke("modpack_install", { source: h.source, project: String(h.id), title: h.title, mc: $("#filter-version").value });
      toast(`Modpack ${inst.name} listo`);
      state.settings.selected_instance = inst.id;
      await saveSettings();
      await loadInstances();
    } else {
      const instance = $("#target-instance").value;
      if (!instance) throw "Primero crea una instancia en Instalar";
      const files = await invoke("content_install", { source: h.source, kind: state.content.kind, project: String(h.id), instance });
      toast(`Instalado: ${files}`);
    }
    btn.textContent = "Instalado ✓";
  } catch (err) {
    toast(String(err), false);
    btn.textContent = old;
    btn.disabled = false;
  }
}

$$("#content-tabs button").forEach((b) => (b.onclick = () => {
  $$("#content-tabs button").forEach((x) => x.classList.toggle("active", x === b));
  state.content.kind = b.dataset.kind;
  updateFilterLayout();
  searchContent(true);
}));
$$("#source-tabs button").forEach((b) => (b.onclick = () => {
  $$("#source-tabs button").forEach((x) => x.classList.toggle("active", x === b));
  state.content.source = b.dataset.source;
  searchContent(true);
}));
let searchTimer = null;
$("#search").oninput = () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => searchContent(true), 350); };
$("#filter-version").onchange = () => searchContent(true);
$("#filter-loader").onchange = () => searchContent(true);
$("#load-more").onclick = () => searchContent(false);
$("#target-instance").onchange = (e) => {
  const inst = state.instances.find((i) => i.id === e.target.value);
  if (inst && gameVersionsLoaded) { $("#filter-version").value = inst.mc_version; searchContent(true); }
};

// ---------------- Cuentas ----------------

async function loadAccounts() {
  const r = await invoke("accounts_list");
  state.accounts = r.accounts;
  state.canLocal = r.can_create_local;
  const sel = selectedAccount();
  $("#accounts").innerHTML = state.accounts.length
    ? state.accounts.map((a) => `<div class="acc ${sel && sel.uuid === a.uuid ? "selected" : ""}" data-uuid="${esc(a.uuid)}" data-kind="${esc(a.kind)}">
        <img class="avatar" src="${avatar(a.uuid)}" alt="" loading="lazy">
        <div class="grow"><b>${esc(a.name)}</b><br><small class="hint">${a.kind === "microsoft" ? "Cuenta de Microsoft" + (a.owns_game ? " · Minecraft verificado" : "") : "Cuenta local"}</small></div>
        <button class="btn small" data-act="use">${sel && sel.uuid === a.uuid ? "En uso ✓" : "Usar"}</button>
        <button class="btn small danger" data-act="remove"><svg><use href="#i-trash"/></svg></button>
      </div>`).join("")
    : `<div class="empty">No hay cuentas todavía.</div>`;
  $("#local-name").disabled = !state.canLocal;
  $("#local-create").disabled = !state.canLocal;
  $("#local-hint").textContent = state.canLocal
    ? "Juega sin conexión con el nombre que quieras (solo en mundos y servidores sin verificación)."
    : "Se activa después de iniciar sesión con una cuenta de Microsoft que tenga Minecraft Java.";
  const chip = sel;
  $("#chip-name").textContent = chip ? chip.name : "Sin cuenta";
  $("#chip-kind").textContent = chip ? (chip.kind === "microsoft" ? "Microsoft" : "Local") : "Inicia sesión";
  $("#chip-avatar").src = chip ? avatar(chip.uuid) : "img/logo.png";
  renderHero();
}

$("#accounts").addEventListener("click", async (e) => {
  const row = e.target.closest(".acc");
  const act = e.target.closest("[data-act]")?.dataset.act;
  if (!row || !act) return;
  if (act === "use") {
    state.settings.selected_account = row.dataset.uuid;
    await saveSettings();
  } else if (act === "remove") {
    if (!(await confirmBox("Quitar cuenta", "Se cerrará la sesión de esta cuenta en el launcher.", "Quitar"))) return;
    await invoke("account_remove", { uuid: row.dataset.uuid, kind: row.dataset.kind });
  }
  loadAccounts();
});

$("#ms-login").onclick = async () => {
  let code;
  try {
    code = await invoke("ms_login_start");
  } catch (err) {
    return modal(`<h3>No se pudo iniciar sesión</h3><p>${esc(err)}</p><div class="row"><button class="btn" onclick="closeModal()">Cerrar</button><button class="btn primary" onclick="closeModal();go('settings')">Ir a Ajustes</button></div>`);
  }
  modal(`<h3>Iniciar sesión con Microsoft</h3>
    <p>Abre <b>${esc(code.verification_uri)}</b> en tu navegador y escribe este código:</p>
    <div class="device-code">${esc(code.user_code)}</div>
    <p class="hint" style="display:flex;align-items:center;gap:10px;margin-top:14px"><span class="spinner"></span> Esperando a que inicies sesión…</p>
    <div class="row"><button class="btn" id="m-cancel">Cancelar</button><button class="btn primary" id="m-open">Copiar código y abrir</button></div>`);
  let cancelled = false;
  $("#m-cancel").onclick = () => { cancelled = true; closeModal(); };
  $("#m-open").onclick = () => {
    navigator.clipboard?.writeText(code.user_code).catch(() => {});
    openUrl(code.verification_uri);
  };
  try {
    const acc = await invoke("ms_login_finish", { deviceCode: code.device_code, interval: code.interval, expiresIn: code.expires_in });
    if (cancelled) return;
    closeModal();
    state.settings.selected_account = acc.uuid;
    await saveSettings();
    toast(`¡Hola, ${acc.name}!`);
    loadAccounts();
  } catch (err) {
    if (!cancelled) modal(`<h3>No se pudo iniciar sesión</h3><p>${esc(err)}</p><div class="row"><button class="btn primary" onclick="closeModal()">Cerrar</button></div>`);
  }
};

$("#local-create").onclick = async () => {
  try {
    const acc = await invoke("account_create_local", { name: $("#local-name").value });
    state.settings.selected_account = acc.uuid;
    await saveSettings();
    $("#local-name").value = "";
    toast(`Cuenta local ${acc.name} creada`);
    loadAccounts();
  } catch (err) {
    toast(String(err), false);
  }
};

// ---------------- Ajustes ----------------

async function saveSettings() {
  try {
    state.settings = await invoke("save_settings", { settings: state.settings });
  } catch (err) {
    toast(String(err), false);
  }
}

const mb = (v) => (v >= 1024 ? (v / 1024).toFixed(v % 1024 ? 1 : 0) + " GB" : v + " MB");

function bindSettings(sys) {
  const s = state.settings;
  const totalRam = sys.memory_mb || 16384;
  const maxR = $("#s-max-mem");
  maxR.max = Math.max(2048, Math.floor((totalRam * 0.85) / 256) * 256);
  $("#s-ram").textContent = sys.memory_mb ? `(tu equipo tiene ${mb(totalRam)})` : "";
  const bind = (id, key, kind = "value", after) => {
    const el = $(id);
    if (kind === "checked") el.checked = !!s[key]; else el.value = s[key];
    el.addEventListener("change", () => {
      let v = kind === "checked" ? el.checked : el.value;
      if (el.type === "range" || el.type === "number") v = Number(v);
      state.settings[key] = v;
      saveSettings();
      after && after();
    });
    if (el.type === "range") el.addEventListener("input", () => after && after());
  };
  const memLabels = () => {
    $("#s-max-mem-v").textContent = mb(Number(maxR.value));
    $("#s-min-mem-v").textContent = mb(Number($("#s-min-mem").value));
    $("#s-conc-v").textContent = $("#s-conc").value;
  };
  bind("#s-game-dir", "game_dir", "value", () => { loadInstances(); loadVersions(); });
  bind("#s-max-mem", "max_memory_mb", "value", memLabels);
  bind("#s-min-mem", "min_memory_mb", "value", memLabels);
  bind("#s-java", "java_path");
  bind("#s-gc", "optimized_gc", "checked");
  bind("#s-jvm", "extra_jvm_args");
  bind("#s-width", "width");
  bind("#s-height", "height");
  bind("#s-fullscreen", "fullscreen", "checked");
  bind("#s-on-launch", "on_launch");
  bind("#s-conc", "concurrent_downloads", "value", memLabels);
  bind("#s-snapshots", "show_snapshots", "checked", () => { $("#show-snapshots").checked = state.settings.show_snapshots; loadVersions(); });
  bind("#s-cf-key", "curseforge_api_key");
  bind("#s-client-id", "ms_client_id");
  memLabels();
  $("#show-snapshots").checked = s.show_snapshots;
  $("#about").textContent = `Amatista Launcher ${sys.version} · Carpeta por defecto: ${sys.default_game_dir} · Hecho por Forja Estudios: santiagogd2513-cpu (pruebas e identificación de errores) y Claude de Anthropic (código y corrección de errores).`;

  $("#s-pick-dir").onclick = async () => {
    const dir = await T.dialog.open({ directory: true, title: "Carpeta del juego" });
    if (dir) { $("#s-game-dir").value = dir; $("#s-game-dir").dispatchEvent(new Event("change")); }
  };
  $("#s-open-dir").onclick = async () => T.opener.openPath(await invoke("game_dir")).catch(() => {});
  $("#s-pick-java").onclick = async () => {
    const f = await T.dialog.open({ title: "Ejecutable de Java" });
    if (f) { $("#s-java").value = f; $("#s-java").dispatchEvent(new Event("change")); }
  };
}

// ---------------- Eventos del backend ----------------

listen("progress", (e) => taskProgress(e.payload));
listen("game-started", (e) => { state.running.add(e.payload); renderInstances(); });
listen("game-exit", (e) => {
  state.running.delete(e.payload.instance);
  renderInstances();
  if (e.payload.code !== 0 && e.payload.code !== -1) {
    toast(`El juego se cerró con un error (código ${e.payload.code}). Mira el registro.`, false);
    consoleEl.hidden = false;
  }
});
listen("game-log", (e) => appendLog(e.payload.line, e.payload.error));

// ---------------- Inicio ----------------

(async function init() {
  const [settings, sys] = await Promise.all([invoke("get_settings"), invoke("system_info")]);
  state.settings = settings;
  bindSettings(sys);
  (await invoke("running_instances")).forEach((id) => state.running.add(id));
  await Promise.all([loadInstances(), loadAccounts()]);
  setLoader("vanilla");
  loadVersions();
  updateFilterLayout();
})();
