# 🧠 AI_CONTEXT.md — Guía Maestra de Arquitectura y Contexto de RetroTV Hub

> **Propósito de este documento:** Este archivo contiene el contexto técnico exhaustivo, decisiones de arquitectura, estado actual del proyecto, hallazgos críticos de depuración en hardware real (Android TV `armeabi-v7a`) y flujos de trabajo de **RetroTV Hub (PlayStation 5 Edition)**. Está diseñado para que cualquier agente de IA o desarrollador pueda retomar el proyecto de inmediato sin perder contexto ni reintroducir bugs ya resueltos.

---

## 1. Visión General del Proyecto

- **Nombre:** RetroTV Hub (PlayStation 5 Edition)
- **Repositorio GitHub:** `https://github.com/julianmcancelo/RetroTVHub` (rama principal: `main`)
- **Application ID:** `com.retro.tvbox` (en modo debug se instala como `com.retro.tvbox.debug`)
- **Plataformas Objetivo:**
  1. **Android TV / Google TV / TV Boxes** (navegación 100% D-Pad / control remoto y gamepads Bluetooth/USB).
  2. **Smartphones y Tablets Android** (navegación táctil, gestos swipe y controles en pantalla estilo DualSense con vibración háptica).
- **Dispositivo de Pruebas Principal (Hardware Real):**
  - **Android TV:** Conectado por ADB inalámbrico en `192.168.0.7:39999` (puerto depuración inalámbrica Android 11+).
  - **Arquitectura de CPU del TV:** `armeabi-v7a` (32-bit ARM sobre kernel de 64-bit). **¡MUY IMPORTANTE!** Cualquier núcleo Libretro o configuración gráfica debe ser compatible con `armeabi-v7a` y no depender de OpenGL ES 3.2 ni Vulkan si usa renderizado por software.

---

## 2. Arquitectura del Sistema y Estructura de Directorios

```text
RetroTVHub/
├── AI_CONTEXT.md                          # Este documento de contexto para IAs
├── README.md                              # Documentación general del repositorio
├── catalog/                               # Manifiestos OTA servidos vía GitHub Raw
│   ├── consoles.json                      # Definición de las 6 consolas y sus cores Libretro
│   ├── psx.json                           # 35 juegos de PlayStation 1 en formato .chd
│   ├── snes.json                          # 20 juegos de Super Nintendo (.sfc)
│   ├── n64.json                           # 18 juegos de Nintendo 64 (.z64)
│   ├── nes.json                           # 18 juegos de Family Game / NES (.nes)
│   ├── genesis.json                       # 18 juegos de Sega Genesis / Mega Drive (.md)
│   └── gba.json                           # 20 juegos de Game Boy Advance (.gba)
└── app/
    ├── build.gradle.kts                   # Configuración Gradle, NDK, Compose TV & Material3
    └── src/main/
        ├── AndroidManifest.xml            # Permisos de red, almacenamiento, TV Leanback launcher
        ├── assets/
        │   └── catalog/                   # Copia exacta offline de catalog/*.json (carga instantánea)
        ├── cpp/
        │   ├── CMakeLists.txt             # Compilación de libnative-lib.so (C++17)
        │   └── bridge.cpp                 # Puente JNI <-> Libretro API (dlopen, video, audio, input, variables)
        └── java/com/retro/tvbox/
            ├── RetroApplication.kt        # Singleton de inicialización ( managers + Ktor Server)
            ├── core/
            │   ├── CoreManager.kt         # Descarga, extracción y validación ELF de cores desde buildbot.libretro.com
            │   ├── EmulatorRunner.kt      # Hilo de ejecución a 60 FPS (SurfaceHolder, vsync, audio, SRAM)
            │   └── LibretroBridge.kt      # Interfaz Kotlin JNI + conversión RGB565/XRGB8888 + AudioTrack no bloqueante
            ├── data/
            │   ├── BiosManager.kt         # Descarga automática y verificación MD5/tamaño de BIOS (PS1, PS2, GBA)
            │   ├── CacheManager.kt        # Gestión de cacheDir/temp_roms/ y purga automática (Play-on-Demand)
            │   ├── CatalogModels.kt       # Data classes: ConsoleEntry, GameEntry, ConsoleCatalog, RomSource
            │   ├── ExternalRomScanner.kt  # Escáner automático de ROMs en /sdcard/Download y USB + extractor ZIP
            │   ├── MemoryCardManager.kt   # Creador y gestor de Memory Cards (.mcr 128KB PS1 / .ps2 8MB PS2)
            │   └── SaveManager.kt         # Persistencia de SRAM (.srm) y Save States (.state) en filesDir/saves/
            ├── network/
            │   ├── EphemeralDownloader.kt # Descarga streaming con progreso, validación HTML/tamaño y extracción .zip/.7z
            │   ├── InputServer.kt         # Servidor Ktor CIO + WebSockets (puerto 8080) para usar celulares como mandos vía QR
            │   └── RepositoryClient.kt    # Cliente OTA con fallback instantáneo a assets/catalog/
            └── ui/
                ├── MainActivity.kt        # Orquestador de pantallas, diálogos de descarga, intents de lanzamiento directo
                ├── HomeScreen.kt          # Dashboard principal estilo PS5 (Media Bar, Hero Banner, grilla de juegos)
                ├── EmulatorScreen.kt      # Vista SurfaceView del emulador, controles táctiles y menú OSD de pausa
                ├── BiosSettingsDialog.kt  # Modal de estado, descarga en 1 clic e importación de BIOS
                ├── ImportGamesDialog.kt   # Modal con guía QR a WowROMs / Archive.org y escáner de carpeta Descargas
                ├── MemoryCardDialog.kt    # Modal de gestión y backup de Memory Cards de PS1/PS2
                ├── QrControllerDialog.kt  # Modal con código QR dinámico para conectar smartphones como joysticks
                ├── components/
                │   ├── Ps5TopBar.kt       # Barra superior PS5 (categorías Juegos/Multimedia, pills de acción, reloj)
                │   └── TouchControllerOverlay.kt # Controles táctiles transparentes con botones PlayStation
                └── theme/
                    └── Theme.kt           # Paleta de colores PS5 (Midnight Blue, Electric Cyan, Neon Magenta)
```

---

## 3. Decisiones Técnicas Críticas y Problemas Resueltos (¡NO REVERTIR!)

### 3.1. Carga Nativa de PlayStation 1 (`pcsx_rearmed`) y Archivos `.chd`
- **Problema histórico:** Al cargar juegos de PS1 (`.chd`, `.bin`, `.iso`), el puente JNI antiguo intentaba leer toda la ROM en memoria RAM desde C++ (`fopen` + `fread`) y pasarle el buffer `retro_game_info.data` al core. Además de agotar la RAM en archivos de 300-600 MB, `pcsx_rearmed` exige `need_fullpath = true` para abrir el contenedor `.chd` directamente desde el sistema de archivos.
- **Solución implementada en [`bridge.cpp`](app/src/main/cpp/bridge.cpp):**
  1. Antes de cargar el juego, `nativeLoadGame` llama a `g_core.retro_get_system_info(&sys_info)`.
  2. Si `sys_info.need_fullpath` es `true` (como en `pcsx_rearmed` y `parallel_n64`), **NO** se lee el archivo en memoria: se pasa `info.path = romPath`, `info.data = nullptr`, `info.size = 0`.
  3. Se configuraron variables críticas de entorno Libretro en `g_core_vars` dentro de [`bridge.cpp`](app/src/main/cpp/bridge.cpp):
     - `pcsx_rearmed_drc = "enabled"` (Dynamic Recompiler activo para 60 FPS en ARM).
     - `pcsx_rearmed_frameskip = "0"`.
     - `pcsx_rearmed_pad1type = "standard"`, `pcsx_rearmed_pad2type = "standard"`.

### 3.2. Emulación de Nintendo 64 en Android TV (`parallel_n64` vs `mupen64plus_next`)
- **Problema histórico:** `mupen64plus_next_gles3` crasheaba o dejaba la pantalla en negro en el Android TV porque requiere extensiones de OpenGL ES 3.1/3.2 y callbacks `RETRO_ENVIRONMENT_SET_HW_RENDER` que nuestro `SurfaceView` 2D por software (`ANativeWindow` / `Canvas`) no expone.
- **Solución implementada:**
  1. Se migró la consola `n64` en [`consoles.json`](catalog/consoles.json) al núcleo **`parallel_n64`** (`parallel_n64_libretro_android.so.zip`).
  2. En [`bridge.cpp`](app/src/main/cpp/bridge.cpp), `parallel_n64` está configurado para renderizar por software usando el plugin **`angrylion`** multihilo acoplado al RSP **`cxd4`**:
     - `parallel-n64-gfxplugin = "angrylion"`
     - `parallel-n64-rspplugin = "cxd4"` *(¡CRÍTICO! `angrylion` requiere un plugin RSP LLE como `cxd4` para recibir comandos RDP crudos; si se cambia a `"hle"`, la pantalla queda completamente negra).*
     - `parallel-n64-cpucore = "dynamic_recompiler"`
     - `parallel-n64-angrylion-multithread = "all threads"`
     - `parallel-n64-angrylion-vioverlay = "Unfiltered"`
     - `parallel-n64-angrylion-sync = "Low"`
     - `parallel-n64-gfxplugin-accuracy = "low"`
     - `parallel-n64-screensize = "320x240"`
     - `parallel-n64-framerate = "fullspeed"`

### 3.3. Audio No Bloqueante (`AudioTrack.WRITE_NON_BLOCKING`)
- **Problema histórico:** En [`LibretroBridge.kt`](app/src/main/java/com/retro/tvbox/core/LibretroBridge.kt), `audioTrack.write(samples, 0, count)` bloqueaba el hilo de emulación JNI cada vez que el buffer de audio de Android se llenaba o cuando un frame de N64/PS1 tardaba más de 16.6 ms, provocando tartamudeo severo (*audio crackling* y caída de FPS).
- **Solución implementada:** Se cambió a `audioTrack.write(samples, 0, safeLength, AudioTrack.WRITE_NON_BLOCKING)` en [`LibretroBridge.kt`](app/src/main/java/com/retro/tvbox/core/LibretroBridge.kt), permitiendo que el loop de video en [`EmulatorRunner.kt`](app/src/main/java/com/retro/tvbox/core/EmulatorRunner.kt) mantenga el ritmo sin bloquearse jamás.

### 3.4. Conversión de Pixel Format (`0RGB1555`, `RGB565`, `XRGB8888`)
- En [`bridge.cpp`](app/src/main/cpp/bridge.cpp), el callback `environment_callback` captura `RETRO_ENVIRONMENT_SET_PIXEL_FORMAT`:
  - `pcsx_rearmed` y `snes9x` suelen usar `RETRO_PIXEL_FORMAT_RGB565` (2 bytes por píxel).
  - `parallel_n64` (`angrylion`) usa `RETRO_PIXEL_FORMAT_XRGB8888` (4 bytes por píxel).
- `video_refresh_callback` convierte tanto `0RGB1555` como `RGB565` y `XRGB8888` a un buffer unificado `ARGB_8888` (`uint32_t`) que se copia directamente al `Bitmap` de Kotlin en [`LibretroBridge.kt`](app/src/main/java/com/retro/tvbox/core/LibretroBridge.kt) mediante `copyPixelsFromBuffer`.

### 3.5. Sincronización de Catálogos (`assets/catalog/` vs `catalog/` OTA y Caché Local)
- La app tiene dos fuentes para los catálogos JSON:
  1. `app/src/main/assets/catalog/*.json` (empaquetado dentro del APK, carga en `0 ms` al iniciar la app mediante `RepositoryClient.loadAssetCatalogSync`).
  2. `catalog/*.json` en el repositorio GitHub (descargado en segundo plano por `RepositoryClient.fetchConsoleCatalog`).
- **Regla de Oro al agregar o editar juegos:**
  - **SIEMPRE** actualiza ambos archivos simultáneamente (`catalog/<consola>.json` y `app/src/main/assets/catalog/<consola>.json`).
  - En [`RepositoryClient.kt`](app/src/main/java/com/retro/tvbox/network/RepositoryClient.kt), si el catálogo empaquetado en `assets` tiene más o igual cantidad de juegos que el descargado o cacheado (`assetCatalog.games.size >= parsed.games.size`), se prefiere el de `assets` para evitar que una caché vieja pise juegos nuevos antes de hacer `git push`.

---

## 4. Catálogo Actual de Juegos (129 Juegos Verificados)

Todos los enlaces de descarga apuntan a colecciones verificadas de **Archive.org** sin bloqueos de CORS ni páginas intermedias:

| Consola | ID | Núcleo Libretro | Formato ROM | Cantidad | Colección Archive.org Principal |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **PlayStation 1** | `psx` | `pcsx_rearmed` | `.chd` | **35** | `archive.org/download/ps1-rip-chd-ck/` |
| **Super Nintendo** | `snes` | `snes9x` | `.sfc` | **20** | `archive.org/download/snes-usa-romset-complete-collection/` |
| **Game Boy Advance** | `gba` | `mgba` | `.gba` | **20** | `archive.org/download/ef_gba_no-intro_2024-02-21/` |
| **Nintendo 64** | `n64` | `parallel_n64` | `.z64` | **18** | `archive.org/download/pkmn-stadium2-usa/` y `SUPERMARIO64_201706/` |
| **Family Game / NES** | `nes` | `fceumm` | `.nes` | **18** | `archive.org/download/No-Intro_NES/` |
| **Sega Genesis** | `genesis` | `genesis_plus_gx` | `.md` | **18** | `archive.org/download/noIntroSegaMegaDriveGenesis2019Jan26/` |

### Juegos Preinstalados en la Caché del Android TV (`192.168.0.7:39999`)
En el directorio `/data/user/0/com.retro.tvbox.debug/cache/temp_roms/` del TV ya se encuentran descargados y listos para abrir instantáneamente:
- `psx_ctr.chd` (*Crash Team Racing* — 65 MB)
- `psx_silent_hill.chd` (*Silent Hill* — 40 MB)
- `psx_re2.chd` (*Resident Evil 2 - Leon Disc* — 117 MB)
- `psx_tekken3.chd` (*Tekken 3* — 23 MB)
- `psx_mvc.chd` (*Marvel vs. Capcom* — 38 MB)
- `psx_gt1.chd` (*Gran Turismo* — 46 MB)
- `psx_ff6.chd` (*Final Fantasy VI* — 13.3 MB)
- `n64_zelda_oot.z64` (*The Legend of Zelda: Ocarina of Time* — 32 MB)
- `gen_sonic2.md` (*Sonic the Hedgehog 2* — 1.0 MB)

---

## 5. Comandos Útiles de Desarrollo, ADB y Pruebas en Vivo

### 5.1. Compilar e Instalar en el Android TV (PowerShell en Windows)
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
.\gradlew.bat assembleDebug
& $adb connect 192.168.0.7:39999
& $adb -s 192.168.0.7:39999 install -r app\build\outputs\apk\debug\app-debug.apk
```

### 5.2. Limpiar Caché de Manifiestos en el TV (tras actualizar catálogos JSON)
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb -s 192.168.0.7:39999 shell "run-as com.retro.tvbox.debug rm -rf /data/user/0/com.retro.tvbox.debug/files/manifest_cache"
```

### 5.3. Lanzar un Juego Directamente por Intent (Deep Launch para Testing Rápido)
[`MainActivity.kt`](app/src/main/java/com/retro/tvbox/ui/MainActivity.kt) soporta los extras `launch_console_id` y `launch_game_id` para iniciar cualquier juego directamente desde ADB sin navegar con el control remoto:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
# Ejemplo: Lanzar Crash Team Racing de PS1 directamente
& $adb -s 192.168.0.7:39999 shell am force-stop com.retro.tvbox.debug
& $adb -s 192.168.0.7:39999 shell am start -n com.retro.tvbox.debug/com.retro.tvbox.ui.MainActivity --es launch_console_id "psx" --es launch_game_id "psx_ctr"
```

### 5.4. Capturar Pantalla del TV y Inspeccionar Logs Nativos
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
# Capturar screenshot del TV
& $adb -s 192.168.0.7:39999 exec-out screencap -p > tv_capture.png

# Ver logs del motor Libretro y puente JNI
& $adb -s 192.168.0.7:39999 logcat -d -s LibretroBridge:D EmulatorRunner:D EphemeralDownloader:D
```

---

## 6. Próximos Pasos Sugeridos / Ideas de Evolución Futura

1. **Soporte Multi-Disco en PS1 (`.m3u` o cambio de disco en el menú OSD):**
   - Exponer la interfaz `RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE` en [`bridge.cpp`](app/src/main/cpp/bridge.cpp) para permitir cambiar entre Disco 1 y Disco 2 en juegos como *Resident Evil 2*, *Final Fantasy VIII/IX*, *Metal Gear Solid* o *Gran Turismo 2*.
2. **Aceleración por Hardware OpenGL ES (`GLSurfaceView`) Opcional:**
   - Actualmente el renderizado se realiza en `SurfaceView` copiando el framebuffer a un `Bitmap` (`ARGB_8888`) escalado por hardware 2D, lo cual es 100% estable en todos los cores por software (`pcsx_rearmed`, `parallel_n64` con `angrylion`, `snes9x`, `mgba`, `fceumm`, `genesis_plus_gx`). Si en el futuro se desea soportar Dreamcast (`flycast`) o PSP (`ppsspp`), se deberá implementar un `GLSurfaceView` con `RETRO_ENVIRONMENT_SET_HW_RENDER`.
3. **Sincronización en la Nube de Partidas Guardadas (Saves / Save States):**
   - Permitir exportar/importar o sincronizar automáticamente la carpeta `filesDir/saves/` con Google Drive o una carpeta compartida en red local (SMB/WebDAV).
4. **Filtros Visuales CRT / Scanlines:**
   - Añadir un toggle en el menú de pausa OSD ([`EmulatorScreen.kt`](app/src/main/java/com/retro/tvbox/ui/EmulatorScreen.kt)) para superponer una máscara ligera de scanlines CRT o suavizado bilineal sobre el `SurfaceView`.
