<div align="center">

# tvhome

**Un launcher estilo Apple TV de 95 KB para Android TV, y un kit reversible para limpiar la caja donde corre.**

Sin Gradle. Sin AndroidX. Sin código de terceros. Tres permisos.
Nunca se desinstala nada — todo cambio se deshace con un comando.

[![Android 9+](https://img.shields.io/badge/Android-9%2B%20(API%2028)-3DDC84?logo=android&logoColor=white)](#requisitos)
[![APK 95 KB](https://img.shields.io/badge/APK-95%20KB-blue)](#por-qué-pesa-tan-poco)
[![Dependencias 0](https://img.shields.io/badge/dependencias-0-success)](#por-qué-pesa-tan-poco)
[![Build 7s](https://img.shields.io/badge/build%20completo-~7s-orange)](docs/BUILD.md)
[![License MIT](https://img.shields.io/badge/licencia-MIT-lightgrey)](LICENSE)

![La pantalla de inicio](docs/img/home-dock.png)

[English](README.md)

</div>

---

## Qué es esto

Dos cosas que resultan ir bien juntas.

**Un launcher.** Negro puro. Un dock con cuatro apps que realmente usás, el
resto en una grilla abajo. Un único resaltado de vidrio translúcido que salta
de baldosa en baldosa — sin bordes por baldosa, sin nombres, sin filas de
"recomendado para vos". Lo único que reacciona al foco es el fondo: baña toda
la pantalla con un color muestreado del propio banner de la app.

**Un kit de limpieza.** Scripts de PowerShell que sacan una foto del estado de
la caja, deshabilitan los paquetes de telemetría, ACR y publicidad en tandas
pequeñas verificadas, y generan un script de restauración a partir de lo que
*realmente* se deshabilitó — así el rollback sólo puede tocar paquetes que esta
herramienta tocó.

Construido y verificado sobre una **Xiaomi Mi Box S** (Android 9, API 28,
1920×1080 @ 320 dpi, ~1 GB de RAM libre). Debería andar en cualquier caja
Android TV de esa generación o más nueva; lo único específico del dispositivo
es la lista de paquetes a deshabilitar, que vive en un solo script.

---

## Capturas

| Dock | Grilla |
|---|---|
| ![Dock con Netflix enfocado](docs/img/home-dock.png) | ![Grilla con Live Channels enfocado](docs/img/home-grid.png) |
| El fondo es rojo Netflix porque Netflix está enfocado. | Abajo desde el dock sube la grilla en un solo movimiento. |

| Otra app, otro color | El vidrio sobre el engranaje |
|---|---|
| ![Dock con Spotify enfocado](docs/img/home-focus-green.png) | ![Píldora de ajustes enfocada](docs/img/settings-pill.png) |
| `ColorSampler` dibuja el banner a 32×18 y elige el píxel más saturado. | El mismo resaltado que se mueve entre baldosas también aterriza en la barra superior. |

### Antes

<table>
<tr>
<td width="50%"><img src="docs/img/stock-launcher.png" alt="Launcher de fábrica"></td>
<td width="50%"><img src="docs/img/home-dock.png" alt="tvhome"></td>
</tr>
<tr>
<td align="center"><b>De fábrica</b> — filas patrocinadas, pestaña Shop, PatchWall, ACR.</td>
<td align="center"><b>tvhome</b> — un reloj, el clima, tus apps.</td>
</tr>
</table>

---

## Por qué pesa tan poco

95 KB, y lo más grande que hay adentro es la tipografía.

| En lugar de | Esto usa |
|---|---|
| `androidx.recyclerview` | Un árbol de vistas estático. Con ~15 apps no hay nada que reciclar, y el focus search de RecyclerView pelea con un D-pad determinista. |
| `androidx.palette` | `ColorSampler` — dibuja el banner a 32×18 y elige el píxel más saturado. ~40 líneas. |
| `androidx.dynamicanimation` | `Spring` — un resorte amortiguado sobre un callback de `Choreographer`. ~30 líneas. |
| Gradle | `aapt2` → `javac` → `R8/D8` → `jar` → `zipalign` → `apksigner`, en un script de PowerShell. |

También es deliberadamente barato en tiempo de ejecución, porque la caja tiene
alrededor de 1 GB de RAM libre: **sin blur, sin fondo de video, sin polling en
segundo plano.** El fondo es un gradiente radial ancho, no un blur real — a la
distancia a la que se mira una TV se ve igual y cuesta un `drawRect` por frame.

El manifiesto declara exactamente tres permisos, y dos existen sólo para que el
clima pueda verificar que hay red antes de hacer un pedido cada treinta minutos:

```
ACCESS_NETWORK_STATE    INTERNET    QUERY_ALL_PACKAGES
```

Poné `weather_enabled` en `false` en `config.xml` y el launcher no hace ningún
pedido de red, que es como salió originalmente.

---

## Requisitos

- Un dispositivo Android TV con **API 28 o superior**, accesible por ADB
- **Android SDK** con `platform-tools`, un `build-tools` y un `platforms`
- Un **JDK** (17 va bien) en el `PATH`
- **PowerShell 7+** en Windows
- La PC y la TV en la misma red

Todo lo demás — R8, la clave de firma — se descarga o se genera en el primer build.

---

## Arranque rápido

```powershell
git clone https://github.com/TU-USUARIO/tvhome.git
cd tvhome\tools

# 1. Apuntá los scripts a tu caja. Copiá el ejemplo y editá la IP.
copy device.example.ps1 device.local.ps1
notepad device.local.ps1

# 2. Sacá una foto de todo antes de tocar nada.
.\backup.ps1

# 3. Compilá e instalá el launcher. El primer run baja R8 (~17 MB) una vez.
.\build-install.ps1

# 4. Hacelo HOME.
. .\_env.ps1
Sh "cmd package set-home-activity com.binario.tvhome/.HomeActivity"
Sh "pm disable-user --user 0 com.google.android.tvlauncher"
```

> **Activá ADB primero.** En la TV: Ajustes → Preferencias del dispositivo →
> Acerca de → apretá OK sobre **Compilación** siete veces → atrás → **Opciones
> de desarrollador** → **Depuración por USB** *y* **Depuración por red**. El
> primer `adb connect` levanta un diálogo *"¿Permitir depuración por USB?"*
> **en la pantalla de la TV** — aceptalo y tildá *"Permitir siempre desde esta
> computadora"*, o todos los comandos después van a decir `unauthorized`.

La limpieza de paquetes es un paso aparte y opcional — leé
[docs/DEBLOAT.md](docs/DEBLOAT.md) antes de correrla, porque la lista es
específica de la caja para la que se escribió.

---

## Configuración

Todo lo que probablemente quieras cambiar vive en **un solo archivo**:
[`launcher/res/values/config.xml`](launcher/res/values/config.xml).

```xml
<string-array name="dock_packages">
    <item>com.google.android.youtube.tv</item>   <!-- el de más a la izquierda -->
    <item>com.netflix.ninja</item>
    <item>com.stremio.one</item>
    <item>com.spotify.tv.android</item>          <!-- el de más a la derecha -->
</string-array>
```

| Opción | Qué hace |
|---|---|
| `dock_packages` | Las cuatro apps del dock, en orden. Un paquete que no está instalado se saltea en silencio. |
| `hidden_packages` | Apps que nunca se muestran, ni en el dock ni en la grilla. |
| `grid_columns` | Columnas debajo del dock (4). |
| `show_ambient_glow` | El baño de color a pantalla completa. `false` = negro plano. |
| `backdrop_intensity` | Qué tan fuerte es, 0–100. 25 es un susurro, 60 es audaz (35). |
| `tile_corner_radius` | Radio de las esquinas de los banners, en dp (10). El resaltado deriva su propio radio de acá, así las dos curvas quedan concéntricas. |
| `spring_stiffness` / `spring_damping` | Cómo se mueve el resaltado. Amortiguación menor a 1.0 se pasa un poco (900 / 0.78). |
| `show_tile_labels` | Nombre de la app debajo de la baldosa enfocada. Apagado por defecto. |
| `weather_enabled` | El clima al lado del reloj. **Lo único que usa la red.** |
| `weather_fahrenheit` | °F en lugar de °C. |

Después recompilá:

```powershell
.\build-install.ps1 -Restart
```

Un ciclo completo de editar → compilar → reinstalar → relanzar toma unos siete
segundos, que es toda la razón por la que este proyecto no tiene Gradle.

---

## Documentación

| | |
|---|---|
| [**docs/LAUNCHER.md**](docs/LAUNCHER.md) | Cómo se comporta el launcher y por qué — foco, el baño de color, D-pad determinista, la barra superior, el clima. |
| [**docs/DEBLOAT.md**](docs/DEBLOAT.md) | Cada paquete deshabilitado, qué hace, y qué se dejó intacto a propósito. |
| [**docs/BUILD.md**](docs/BUILD.md) | El pipeline sin Gradle, y las tres trampas que costaron tiempo real. |
| [**docs/TROUBLESHOOTING.md**](docs/TROUBLESHOOTING.md) | Pantalla negra, app que desapareció, control que no responde, cómo volver al launcher de fábrica. |

*(La documentación larga está en inglés.)*

---

## Nada de esto es permanente

Esta es la parte que vale la pena leer dos veces.

- **Nunca se desinstala nada.** Los paquetes se apagan con
  `pm disable-user --user 0`, que sobrevive reinicios y se deshace con un comando.
- **`restore.ps1` se genera de lo que realmente pasó.** `debloat.ps1` verifica
  cada deshabilitación y agrega a `backup/disabled.log` sólo los éxitos
  confirmados; `restore.ps1` lee ese log y *se niega* a reactivar cualquier cosa
  que no esté ahí.
- **`debloat.ps1` tiene una lista protegida** y no deshabilita los paquetes sin
  los que una TV no funciona — el stack del control remoto y Bluetooth, los
  dispositivos de entrada, el teclado en pantalla, Play Store y Play Services,
  el instalador de paquetes, WebView, Ajustes, el login de portales cautivos.
- **La caja nunca puede quedarse sin pantalla de inicio.**
  `com.android.tv.settings` registra un `FallbackHome` con prioridad −1000
  debajo de todo.
- **Un comando devuelve todo:**

  ```powershell
  .\restore.ps1 -All -StockLauncher
  ```

---

## Estructura

```
tvhome/
├── launcher/            el APK: manifest, res/, java/, la tipografía Outfit
│   └── res/values/config.xml      ← el único archivo que editás
├── tools/               PowerShell: backup, debloat, restore, build, shot, key, dns
├── gate/                un APK descartable para probar que la caja acepta un APK que declara HOME
├── docs/                la documentación larga y las capturas
├── backup/              la foto de tu dispositivo (ignorado por git — tiene tu serial y MAC)
├── screenshots/         capturas crudas de shot.ps1 (ignorado por git)
└── build/               salida (ignorado por git, se regenera en ~7s)
```

---

## Hoja de ruta

- [ ] **Botón de modo cine** — una segunda píldora de vidrio que dispara una
      escena de Home Assistant para bajar las luces. Diseño definido,
      implementación pendiente.
- [ ] Volver la lista de paquetes de debloat configurable en vez de hardcodeada,
      para que otros dispositivos puedan traer la suya
- [ ] Protector de pantalla opcional con sólo el reloj, para reemplazar el que
      se deshabilitó
- [ ] Un port de los scripts de `tools/` a shell para Linux/macOS
- [ ] Override por app del color de fondo muestreado

Issues y PRs bienvenidos — ver [CONTRIBUTING.md](CONTRIBUTING.md).

---

## Créditos

- [**Outfit**](https://fonts.google.com/specimen/Outfit) de Smartsheet Inc.,
  SIL Open Font License 1.1 — la tipografía variable del reloj.
- [**Open-Meteo**](https://open-meteo.com) — clima, gratis, sin API key.
- [**ipapi.co**](https://ipapi.co) — posición aproximada por IP pública,
  cacheada una semana. Ojo con lo que eso significa: ese servicio ve la IP
  pública de tu caja. Poné `weather_enabled=false` para no usarlo.

Los banners de las apps en las capturas pertenecen a sus dueños y se muestran
tal como aparecen en el dispositivo.

## Licencia

[MIT](LICENSE).
