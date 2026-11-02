# 💜 Amatista Launcher

Launcher de **Minecraft Java Edition** ligero y rápido, 100 % en español, con tema morado.
Hecho con [Tauri](https://tauri.app) (núcleo en Rust + interfaz web sin frameworks): instalador de pocos MB,
arranque instantáneo y descargas en paralelo con verificación SHA-1.

## Funciones

- **Tres formas de jugar**, cada una como instancia con su propia carpeta (mods, mundos, shaders…):
  - 🌱 **Vanilla**: cualquier versión oficial (y snapshots si las activas).
  - ✨ **OptiFine**: el launcher abre optifine.net, tú descargas el `.jar` y él instala Minecraft + OptiFine solo
    (OptiFine no permite descargas automáticas de terceros).
  - ⚒️ **Forge**: se ejecuta el **instalador oficial de Forge** sin ventana, así se descargan todas sus librerías y se
    aplican todos sus parches (processors), con compatibilidad total. Los Forge antiguos (≤ 1.12.2) también funcionan.
- **Java automático**: descarga el Java oficial de Mojang que pide cada versión (8, 17, 21…).
- **Cuentas**:
  - **Iniciar sesión con Microsoft** (código de dispositivo → Xbox Live → Minecraft), con renovación automática.
  - **Cuenta local**: se activa después de verificar una cuenta de Microsoft que tenga Minecraft Java.
- **Contenido** de **Modrinth** y **CurseForge**: mods, paquetes de recursos, modpacks y shaders, con buscador y
  filtros por versión de Minecraft y cargador (el filtro de cargador solo en mods y modpacks). Instala dependencias
  automáticamente; los modpacks crean su propia instancia con Forge.
- **Optimizado**: argumentos G1GC ajustados, memoria sugerida según tu RAM, 32 descargas simultáneas,
  verificación completa solo la primera vez (los siguientes inicios son inmediatos), compilación con LTO.

## Descargar

GitHub Actions compila el launcher en cada cambio y lo publica en **Releases**:

- **Windows**: `AmatistaLauncher-Setup.exe`
- **Linux**: `AmatistaLauncher.AppImage` o `.deb`

En cada compilación también se ejecuta una **prueba real**: instala Minecraft 1.20.1 vanilla y Forge y los arranca
en una pantalla virtual para comprobar que el juego inicia.

## Configuración necesaria

### Iniciar sesión con Microsoft
Microsoft exige que cada launcher tenga su propia aplicación registrada:

1. Crea una aplicación en [Azure → Registros de aplicaciones](https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade)
   con el tipo de cuenta **“Solo cuentas personales de Microsoft”** y activa **“Permitir flujos de clientes públicos”**.
2. Pide a Mojang que apruebe el ID de la aplicación en [aka.ms/mce-reviewappid](https://aka.ms/mce-reviewappid).
3. Pega el **Id. de aplicación (cliente)** en *Ajustes → Cuenta de Microsoft*.

### CurseForge
Crea una clave de API gratuita en [console.curseforge.com](https://console.curseforge.com/) y pégala en
*Ajustes → CurseForge*. Modrinth no necesita clave.

## Dónde se guardan las cosas

| Qué | Dónde (Windows) |
|---|---|
| Juego (versiones, librerías, assets, Java, instancias) | `%APPDATA%\.amatista` (se puede cambiar en Ajustes) |
| Ajustes y cuentas del launcher | `%APPDATA%\AmatistaLauncher` |

## Compilar

```bash
npm install
npm run tauri -- dev      # modo desarrollo
npm run tauri -- build    # instalador
cd src-tauri && cargo test -- --ignored   # prueba real (necesita internet y pantalla)
```

---
No es un producto oficial de Minecraft. No está aprobado ni asociado con Mojang ni Microsoft.
