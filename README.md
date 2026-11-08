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
- **Descarga dinámica de núcleos (`CoreManager`):** Descarga núcleos oficiales directamente desde el Buildbot de Libretro (`snes9x`, `fceumm`, `genesis_plus_gx`, `pcsx_rearmed`, `mupen64plus_next`, `mgba`) adaptados a la arquitectura del dispositivo (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`).
- **Seguridad binaria:** Validación estricta de cabecera binaria ELF y protección contra ataques *Zip-Slip*.
- **Audio estéreo de baja latencia:** Pipeline `AudioTrack` PCM a 44.1 kHz.
- **Renderizado 60 FPS con frame skipping inteligente:** Mantiene sincronizados audio y video en dispositivos modestos.

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
