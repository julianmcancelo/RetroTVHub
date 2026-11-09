# 🎮 RetroTV Hub (PlayStation 5 Edition)

**RetroTV Hub** es una aplicación nativa para **Android TV** y **smartphones Android** diseñada para ofrecer una experiencia retro integral con interfaz visual inspirada en **PlayStation 4 y PlayStation 5**. Integra emulación para múltiples consolas clásicas (NES, SNES, Sega Genesis, GBA, PS1, PS2 y N64), descarga de juegos bajo demanda en caché efímera (**Play-on-Demand**), tarjetas de memoria virtuales protegidas y soporte multijugador con joysticks Bluetooth/USB o smartphones como mandos mediante código QR.

---

## 🌟 Características Principales

### 🎨 Experiencia Visual PS5 Dashboard
- **Fondo dinámico inmersivo:** Degradado azul medianoche (`#080C14` a `#151D2A`) con auras ambientales difuminadas en Cyan y Magenta.
- **Barra Superior PS5 (`Ps5TopBar`):** Reloj en tiempo real, indicador dinámico de batería, Wi-Fi, avatar de usuario y accesos rápidos a BIOS, Memory Cards y ajustes de caché.
- **Controles táctiles en pantalla con respuesta háptica:** D-Pad estilo Sony, botones icónicos de PlayStation (▲ Verde, ● Rojo, ✕ Azul, ■ Rosa), gatillos L1/R1/L2/R2 y botón flotante de menú OSD para móviles.
- **100% Responsive:** Navegación por gestos táctiles (swipe/tap) en celulares y por control remoto / D-pad en televisores Android TV.

### ⚙️ Motor de Emulación Libretro Oficial
- **Descarga dinámica de núcleos (`CoreManager`):** Descarga núcleos oficiales directamente desde el Buildbot de Libretro (`snes9x`, `fceumm`, `genesis_plus_gx`, `pcsx_rearmed`, `parallel_n64`, `mgba`) adaptados a la arquitectura del dispositivo (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
- **Optimización nativa PS1 y N64 (`bridge.cpp`):**
  - **PlayStation 1 (`pcsx_rearmed`):** Soporte nativo `need_fullpath` para carga directa de imágenes comprimidas **`.chd`**, `.bin`/`.cue`, `.iso` y `.pbp` con Dynamic Recompiler (`drc`) activo a 60 FPS.
  - **Nintendo 64 (`parallel_n64`):** Renderizado por software multihilo (`angrylion` + `cxd4` RSP + `dynamic_recompiler`) compatible con GPUs de televisores Android TV (`armeabi-v7a` / `arm64-v8a`) sin requerir OpenGL ES 3.1+.
- **Seguridad binaria:** Validación estricta de cabecera binaria ELF y protección contra ataques *Zip-Slip*.
- **Audio estéreo de baja latencia:** Pipeline `AudioTrack` PCM a 44.1 kHz en modo `WRITE_NON_BLOCKING` para evitar bloqueos de frame en el hilo JNI.
- **Renderizado 60 FPS con frame skipping inteligente:** Mantiene sincronizados audio y video en dispositivos modestos.

### 📚 Catálogo Integrado de 129 Juegos Clásicos + Importador Local
- **129 Clásicos Preconfigurados (OTA + Offline Bundle):**
  - **PlayStation 1 (35 juegos `.chd`):** *Crash Team Racing*, *Silent Hill*, *Resident Evil 1, 2 y 3*, *Gran Turismo 1 y 2*, *Final Fantasy VI, VIII, IX y Tactics*, *Tekken 3*, *Castlevania: Symphony of the Night*, *Metal Gear Solid*, *Pepsiman*, *Winning Eleven 2002*, *Dino Crisis 1 y 2*, *Spyro 1-3*, *Crash Bandicoot 1-3*, *Tony Hawk's Pro Skater 2*, *Marvel vs. Capcom*, y más.
  - **Super Nintendo (20 juegos):** *Super Mario World*, *Zelda: A Link to the Past*, *Chrono Trigger*, *Donkey Kong Country 1-3*, *Super Metroid*, *Super Mario RPG*, *Street Fighter II Turbo*, *Ultimate Mortal Kombat 3*, *International Superstar Soccer Deluxe*, y más.
  - **Nintendo 64 (18 juegos):** *Zelda: Ocarina of Time*, *Majora's Mask*, *Super Mario 64*, *Mario Kart 64*, *Star Fox 64*, *Banjo-Kazooie*, *Donkey Kong 64*, *Paper Mario*, *Mario Party 1-3*, *Conker's Bad Fur Day*, *Resident Evil 2*, y más.
  - **Family Game / NES (18 juegos):** *Super Mario Bros. 1-3*, *Contra*, *Castlevania I y III*, *Mega Man 2 y 3*, *Punch-Out!!*, *Ninja Gaiden*, *Battletoads*, *Pac-Man*, y más.
  - **Sega Genesis (18 juegos):** *Sonic 1, 2 y Sonic & Knuckles*, *Streets of Rage 2 y 3*, *Gunstar Heroes*, *Contra: Hard Corps*, *Comix Zone*, *Ultimate Mortal Kombat 3*, *Earthworm Jim*, y más.
  - **Game Boy Advance (20 juegos):** *Pokémon Edición Esmeralda*, *Zelda: The Minish Cap*, *Metroid Fusion & Zero Mission*, *Castlevania: Aria of Sorrow*, *Golden Sun*, *Fire Emblem*, *Final Fantasy VI Advance*, *Mario & Luigi: Superstar Saga*, y más.
- **Escáner de ROMs Externas (`ExternalRomScanner`):** Detecta automáticamente ROMs descargadas desde el navegador (como *WowROMs* o *Archive.org*) en la carpeta `Downloads` o unidades USB, descomprime archivos `.zip` al vuelo y los integra en tu biblioteca.

### 💾 Almacenamiento, BIOS y Play-on-Demand
- **Gestor automático de BIOS (`BiosManager`):** Detección y descarga en 1 clic de BIOS para PS1 (`scph1001.bin`), PS2 (`scph39001.bin`) y GBA (`gba_bios.bin`), más selector manual para importar archivos locales o pendrives USB.
- **Memory Cards Oficiales (`MemoryCardManager`):** Soporte exacto para tarjetas de 15 bloques de PS1 (128 KB `.mcr`) y PS2 (8 MB `.ps2`) con superbloque Sony, respaldos a Descargas y exportación a USB.
- **Play-on-Demand:** Las ROMs se descargan temporalmente en `cacheDir/temp_roms/`, se ejecutan de inmediato y se purgan al salir (`purgeOnExit`), liberando espacio en el TV sin afectar jamás las partidas guardadas en `filesDir/saves/`.

### 📱 Celular como Mando Virtual (Multijugador local)
- Microservidor embebido Ktor CIO con WebSockets en el puerto 8080.
- Código QR dinámico en pantalla: escanea con cualquier smartphone (Android / iOS) en la misma red Wi-Fi y juega al instante como Jugador 2, 3 o 4 sin instalar nada adicional.

---

## 🛠️ Tecnologías y Requisitos

- **Lenguaje:** Kotlin 1.9.23
- **Build System:** Android Gradle Plugin 8.3.2 / Gradle 8.7
- **UI:** Jetpack Compose para TV (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`) + Jetpack Compose Material 3
- **Red:** OkHttp 4.12.0, Ktor Server CIO 2.3.9 & WebSockets
- **Utilidades:** ZXing 3.5.3 (Códigos QR), Coil 2.6.0 (Imágenes)
- **SDK Requerido:** `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 34` (Android 14)

---

## 🚀 Compilación e Instalación

Para compilar el proyecto localmente:

```bash
# Compilar APK de depuración
./gradlew assembleDebug

# El APK se generará en:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 Licencia

Este proyecto está disponible bajo la licencia MIT.
