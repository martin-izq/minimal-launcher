# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Comandos de desarrollo

```bash
# Compilar APK de debug
./gradlew assembleDebug

# Compilar APK de release
./gradlew assembleRelease

# Instalar directamente en dispositivo/emulador conectado
./gradlew installDebug

# Limpiar artefactos de build
./gradlew clean
```

No hay tests automatizados en el proyecto (ni unitarios ni de instrumentación).

## Arquitectura

**Foco** es un launcher de Android minimalista (sin íconos, solo texto) escrito en Kotlin + Jetpack Compose. Sigue MVVM con un único Activity y navegación por overlays modales, sin back stack de Fragments.

### Flujo de datos

```
PackageManager     ──► AppRepository        ──┐
DataStore          ──► SettingsRepository   ──┤
DataStore          ──► WidgetsRepository    ──┼─► LauncherViewModel (LauncherUiState) ──► UI
UsageStatsManager  ──► UsageStatsRepository ──┘
```

`LauncherViewModel` combina los 4 flows en un único `StateFlow<LauncherUiState>` mediante `combine()`. Toda mutación pasa por métodos públicos del ViewModel (lanzar app, alternar favorito, ocultar, renombrar, agregar/mover/redimensionar widget, etc.).

### Estructura de la UI

**Gestos configurables (asignación libre).** Tres funciones —widgets, acceso rápido y app drawer— se reparten las tres direcciones libres desde el inicio (**izquierda / derecha / arriba**); "abajo" queda siempre para el panel de notificaciones. `SettingsRepository` guarda `widgetsDir`, `quickLaunchDir` y `drawerDir` (0=izq, 1=der, 2=arriba) forzados a ser una permutación de `{0,1,2}`; `setGestureDir(item, dir)` intercambia si la dirección ya estaba ocupada.

`LauncherRoot` arma los pagers dinámicamente según esa asignación:
- **`WidgetScreen`** (widgets de terceros vía `AppWidgetHost`) y **`AppDrawer`** (lista alfabética + búsqueda) son *pantallas*; se ubican en la dirección asignada.
- El **acceso rápido** no es una página: es un gesto en `HomeScreen` (deslizar hacia `quickLaunchDir`) que lanza una app configurable.
- Las pantallas horizontales (izq/der) van como páginas del `HorizontalPager` externo `[izq?, Home, der?]`; si una pantalla está "arriba", `HomeScreen` se envuelve en un `VerticalPager` anidado `[Home, pantalla]`.
- `HomeScreen` (`quickLaunchDir`) captura el swipe hacia el lado del acceso rápido; ese lado nunca tiene página, así que el pager no lo consume.
- Ambos pagers usan `beyondViewportPageCount = 1` para mantener vivas las `AppWidgetHostView` (si no, se desanclan y quedan invisibles al volver).

Sobre el pager se superponen 2 overlays controlados por flags locales en `LauncherRoot`:
- `ScreenTimeScreen`: estadísticas de uso (requiere permiso especial)
- `SettingsScreen`: preferencias + gestión de apps ocultas

En el primer arranque, `LauncherRoot` muestra `OnboardingScreen` en vez del pager mientras `settings.onboarded` sea false (wizard: bienvenida → poner como launcher por defecto → permisos opcionales → tips). Se gatea con `vm.settingsLoaded` (StateFlow que pasa a true tras la primera lectura de DataStore) para no mostrar un flash del onboarding antes de que carguen los settings reales.

### Internacionalización (i18n)

Todos los textos visibles están en recursos: `res/values/strings.xml` (inglés, idioma por defecto) y `res/values-es/strings.xml` (español). Android elige el idioma según el locale del SO. **No agregar literales de UI en el código**: usar `stringResource(R.string.…)` (o `context.getString` fuera de composables) y añadir la clave en ambos `strings.xml`. La fecha del inicio y los días del gráfico de Screen Time usan `Locale.getDefault()`.

### Datos persistidos (DataStore)

`SettingsRepository` (`launcher_settings`) gestiona las preferencias vía DataStore Preferences:
- `favorites`: lista ordenada (separada por `\n`)
- `hidden` / `distracting`: conjuntos de package names
- `renames`: `Map<String, String>` serializado como JSON (kotlinx.serialization); **solo aplica a favoritos**, el cajón muestra el nombre original
- `appLimits`: `Map<String, Int>` (package → minutos/día) serializado como JSON; límite de tiempo diario por app
- Booleanos: `showClock`, `showDate`, `showBattery`, `showScreenTimeHome`, `frictionEnabled`, `amoledDark`, `clockOpensAlarms`, `hideStatusBar`, `alphabetIndex`, `searchBarBottom`, `drawerShowTitle`, `onboarded`
- Direcciones de gestos (permutación de `{0,1,2}` = izq/der/arriba): `widgetsDir`, `quickLaunchDir`, `drawerDir` (la clave legacy `widgets_on_left` se lee solo para migrar)
- Ints (tamaños/alineación/espaciado): `clockSize`, `dateSize`, `favoritesSize`, `homeAlign`, `verticalPos`, `appDrawerSize`, `appDrawerAlign`, `scrubberWidth` (ancho táctil de la guía, dp), `drawerTopSpace` (espacio superior del cajón, dp)
- `drawerTitle`: String (título del cajón; vacío → default localizado `drawer_default_title`)
- `frictionSeconds`: Int (valores válidos: 3, 5, 10)
- `sessionIdleMinutes`: Int (1..30, default 5; ventana de "misma visita" del guard system-wide)
- `limitWarnMinutes`: Int (0..10, default 1; aviso previo antes de que un límite diario corte en pleno uso, 0 = sin aviso)
- `quickLaunchPackage`: String? (app de acceso rápido)

`WidgetsRepository` (`launcher_widgets`, separado) persiste la lista de `WidgetPlacement(appWidgetId, heightDp)` como JSON. `WidgetController` (creado en `MainActivity`) aloja el `AppWidgetHost`, hace el binding con consentimiento y cachea las `AppWidgetHostView`.

### Widgets: tamaños, edición y selector

**No imponerle a un widget un alto fuera de lo que declara.** Un widget solo renderiza en las medidas que provee: estirado más allá de su `maxResizeHeight` no hay `RemoteViews` aplicable y muestra su estado de error ("No se puede mostrar el contenido") — era la causa de los widgets rotos. `ui/widgets/WidgetSizing.kt` es la única fuente de verdad: `widgetSizing(info, density, screenHeightDp)` deriva `defaultHeightDp` (desde `targetCellHeight` en API 31+, si no `minHeight`), `minHeightDp`, `maxHeightDp` y `resizable`; `resolve()`/`clamp()` acotan el alto guardado. Ojo: `minHeight`/`min|maxResizeHeight` vienen en **píxeles**, hay que dividirlos por `density`.

**`resizeMode` no bloquea el redimensionado.** Se probó y fue peor: un widget cuyo tamaño recomendado calculamos mal quedaba varado ahí, sin forma de corregirlo. `resizeMode` solo aprieta el **tope por defecto** cuando el widget no declara `maxResizeHeight` (2× su alto natural en vez del 85% de pantalla), suficiente para ajustarlo sin poder estirarlo hasta romperlo. `resizable` sale del **rango** (`max - min`), no del flag: si el widget realmente tiene una sola medida, no hay barra de agarre.

`WidgetScreen` persiste el alto acotado vía un `LaunchedEffect` (migración: cura solo los widgets guardados fuera de rango) y `WidgetHostViewItem` informa el **ancho medido real** (con `onSizeChanged`, no una estimación de pantalla) y solo llama `updateAppWidgetOptions` **cuando el tamaño cambió** (llamarlo en cada frame dispara `onAppWidgetOptionsChanged` y hace parpadear algunos widgets). Medir con `BoxWithConstraints` **no sirve acá**: es un `SubcomposeLayout` y una re-subcomposición reconstruye el `AndroidView`, re-parentando la vista del widget y matando el gesto en curso.

**Widgets con scroll propio: `WidgetFrame` decide a la mitad del `touchSlop`.** El scroll de Compose reclama el gesto al slop completo; si el frame decidiera en ese mismo umbral sería un empate cuyo ganador depende del timing de dispatch — y es lo que hacía que widgets scrolleables (Gmail, agenda) dejaran de scrollear ante cambios cercanos que ni tocaban esta lógica. Con la mitad, el widget siempre pide el gesto primero; un arrastre horizontal lo suelta a tiempo para el pager, que recién arranca al slop completo.

**Edición.** La pantalla no tiene botones: un **long-press en cualquier parte** abre un `MinimalMenu` con "Agregar widget" / "Editar widgets". En modo edición aparece "Listo" en el encabezado (atrás también sale).

**Nada del modo edición cambia el layout.** Todo va *superpuesto* dentro del recuadro del widget, nunca alrededor: si suma alto, la página se corre y hay que adivinar dónde van a quedar los widgets al salir. Por eso la **barra de agarre** del alto se ancla al borde inferior *dentro* del widget (target de 44 dp a lo ancho, chip visible chico sobre un scrim para que se lea encima del contenido), y mover/quitar viven detrás de un **long-press sobre el widget** (otro `MinimalMenu`) en vez de una fila de botones ↑/↓/Quitar, que sumaba muchísimo alto. Ese detector de taps sí puede ir en una capa Compose encima del widget, porque en modo edición el widget ya está inerte por `blockTouches`.

El long-press se detecta en Compose (`Modifier.onLongPress`) observando la fase **`Initial` sin consumir**: así los taps siguen llegando al widget, y si el scroll o el arrastre de la barra reclaman el puntero se aborta. Hacerlo con un `GestureDetector` dentro de `WidgetFrame` **no funciona**: cuando Compose se queda con el gesto la `View` deja de recibir eventos, nunca ve el movimiento que cancelaría el long press y entra a modo edición con solo scrollear. El corte real es el `touchSlop`. Aparte, `blockTouches` en `WidgetFrame` evita que el widget reaccione mientras se edita o hay un menú encima (si no, se come el tap que abrió el menú), pero deja caer el gesto a Compose para que la página siga scrolleando.

**Selector.** `WidgetPicker` carga los providers en `Dispatchers.IO` vía `produceState` (hacerlo inline congelaba la pantalla: es una llamada a `PackageManager` por widget), con búsqueda y una miniatura por fila (`loadPreviewImage`, fallback a `loadIcon`) cargada de forma perezosa e independiente para no bloquear el scroll.

### Permiso de uso (`PACKAGE_USAGE_STATS`)

Este permiso **no se puede solicitar con un dialog estándar**; el usuario debe concederlo manualmente en *Ajustes → Privacidad → Aplicaciones con acceso de uso*. `UsageStatsRepository.hasPermission()` usa `AppOpsManager` para verificarlo. Toda la UI de tiempo de pantalla queda bloqueada detrás de este permiso.

El uso de **hoy** (tiempo por app + desbloqueos) se calcula recorriendo el **stream de eventos** (`queryEvents`) y emparejando `MOVE_TO_FOREGROUND`/`MOVE_TO_BACKGROUND`, no con `queryAndAggregateUsageStats`: este último devuelve buckets diarios completos cuyo `totalTimeInForeground` **no está recortado** al rango pedido, así que recién pasada la medianoche seguía informando la sesión completa del día anterior (horas de uso y desbloqueos fantasma). Una sesión abierta antes del inicio de la ventana (cruza medianoche) se cuenta desde `startOfDay`. El gráfico semanal reusa ese total preciso para hoy y sigue usando el agregado (más barato) para los días completos pasados.

### Pantallas de bloqueo (fricción / límite / foco)

Las tres pantallas de bloqueo comparten `BlockScreen` (`ui/BlockScreen.kt`), un scaffold **fullscreen** con la identidad de la marca: glow ámbar radial desde arriba que **respira** (alpha animado en ciclo ~4 s), kicker en mayúsculas con tracking en `FocusWarm`, nombre de la app en display fina, countdown como número gigante ámbar (`BlockCountdown`, se desvanece al llegar a 0) y jerarquía de acciones invertida — la salida sana (`BlockPrimaryAction`, píldora con borde) es la prominente y "Abrir igual" (`BlockQuietAction`) es texto silencioso deshabilitado hasta el fin del countdown. Incluye `BackHandler` (atrás = descartar).

- `FrictionDialog` (`ui/FrictionDialog.kt`): al abrir apps "distractoras" si `frictionEnabled = true`. Muestra tiempo de uso del día en la app, su % del tiempo de pantalla total (`totalTodayMs`, opcional) y countdown configurable (3/5/10 s). La **frase de la pausa rota** (`ui/FrictionMessages.kt`): un mensaje fijo deja de leerse a los pocos días, así que hay tres sets (`friction_lines_calm|mid|direct` en ambos `strings.xml`) y el **tono escala con `usedTodayMs`** (calmo <20 min, intermedio 20-60, directo 1 h+; sin permiso de uso siempre calmo). La elección usa una *shuffle bag* en memoria del proceso (`FrictionMessages.next`) —se recorre todo el set antes de repetir, y una bolsa nueva nunca abre con la última frase mostrada—, compartida por `LauncherRoot` y `BlockActivity`; no se persiste (perderla sólo re-baraja, y no cuesta I/O al abrir la app).
- `LimitReachedDialog` (`ui/FocusLimit.kt`): límite diario alcanzado; **bloqueo firme** — sin countdown ni "Abrir igual", la app no se puede abrir hasta el día siguiente.
- `LimitWarningDialog` (`ui/FocusLimit.kt`): aviso previo al corte del límite. No es un bloqueo (la app sigue viva detrás): número gigante con los minutos restantes —**estático**, porque mientras esta pantalla está arriba la app está pausada y su uso no corre—, "Salir ahora" prominente y "Seguir usándola" como acción silenciosa (esa vuelta va con bridge para que el guard no la lea como apertura nueva).
- `FocusBlockDialog` (`ui/FocusSessions.kt`): bloqueo firme durante sesión de foco; sin countdown ni escape.

Se usan idénticas desde `LauncherRoot` (choke point) y `BlockActivity` (guard system-wide; el servicio pasa `totalTodayMs` por extra). `TimeLimitDialog` (picker de límite) sigue siendo un `MinimalDialog` común, pero con la misma pieza de elección que `FocusControlScreen`: número gigante ámbar + `MinimalSlider`.

### Modo Foco — límites de tiempo (Fase 2, en progreso)

`ui/FocusLimit.kt` define `TimeLimitDialog` (elegir el límite diario por app, en minutos, desde el menú largo) y `LimitReachedDialog` (bloqueo firme: sin countdown ni "Abrir igual", no se puede reabrir hasta el día siguiente). El límite se verifica en dos momentos: **al abrir** (`onAppClick` de `LauncherRoot` comparando `usage.perAppToday[pkg]` contra `appLimits[pkg]`, con prioridad sobre la fricción; y la transición del guard) y **durante el uso** (watcher del servicio, ver más abajo). **Requiere el permiso de uso** (sin `perAppToday` el límite nunca dispara); la verificación en vivo además necesita el servicio de accesibilidad + `enforceBlocks`.

`data/FocusSession.kt` modela las **sesiones de foco programadas** (`FocusSession`: `start`/`end` en minutos del día, `days` = weekdays 1..7, `enabled`), serializadas como JSON en `focusSessions`. `ui/FocusSessions.kt` tiene el editor (`FocusSessionEditorDialog`, con steppers de ±15 min y chips de días) y `FocusBlockDialog` (firme, informativo).

**Bloqueo agresivo:** `LauncherRoot` tiene un tick por minuto (`nowTick`) y calcula la sesión activa (`activeFocusSession`), `blockedPackages` = las apps distractoras si hay sesión, y `focusUntil` (hora de fin). Ese set se pasa a `HomeScreen` y `AppDrawer`; `AppRow` con `blocked = true` se **grisa**, pero el **tap sí llega**: rutea por `onAppClick` y muestra el `FocusBlockDialog` (mensaje firme "en pausa a propósito", no abre) en vez de lanzar — así no parece un error. El long-press sigue gestionándola. El auto-launch de búsqueda ignora apps bloqueadas, y el acceso rápido se rutea por `onAppClick`. `onAppClick` es el único choke point: orden **sesión de foco (firme)** → límite diario → fricción → lanzar. Además, `HomeScreen` muestra un **chip de foco** (`showFocusOnHome`, default on): tappable para **iniciar foco manual** (25/50 min o indefinido, vía `FocusControlScreen` —una pantalla completa sobre el scaffold `BlockScreen`, misma identidad que las pantallas de bloqueo: glow ámbar que respira, número gigante de minutos con slider para elegir la duración—) cuando está inactivo, para **reanudar** una sesión programada salteada, o para **salir** cuando está activo. Con `dndInFocus`, un `LaunchedEffect` pone **No molestar** (`util/Dnd.kt`, `setInterruptionFilter`) mientras el foco está activo y lo saca al terminar; requiere el acceso a No molestar (`ACCESS_NOTIFICATION_POLICY`, concedido por el usuario, sin adb). Con `strictFocus` (**modo estricto/hardcore**), un foco con **fin fijo** (temporizado o sesión programada; nunca indefinido) queda **bloqueado**: `focusLocked` oculta la opción de salir de la pantalla, se deshabilita la opción "hasta que lo apague" al iniciar, y el toggle de modo estricto en Ajustes queda deshabilitado mientras hay un foco bloqueado en curso (para no poder desactivarlo y escapar). El estado combinado se calcula en `LauncherRoot`: `focusActiveNow = scheduledActive || manualActive`, donde `manualActive = now < manualFocusUntil` (epoch; `Long.MAX_VALUE` = indefinido) y `scheduledActive = hay sesión && now >= focusSkipUntil`. "Salir" limpia `manualFocusUntil` y, si era una sesión programada, setea `focusSkipUntil` al fin de esa ventana (la saltea sin desactivar futuras sesiones). `manualFocusUntil`/`focusSkipUntil` son estado transitorio (no se restauran en el import de backup). La UI de Modo Foco vive en una única sección de Ajustes (sesiones + fricción + apps distractoras).

**Bloqueo en todo el sistema (`enforceBlocks`, default on).** La misma decisión de `onAppClick` se extrajo a `data/FocusGate.kt` (`decideBlock(...)` → `BlockDecision` = `Allow`/`FocusBlock`/`LimitReached`/`Friction`, con la prioridad **sesión → límite → fricción**), usada tanto por `LauncherRoot.onAppClick` como por el `NotificationAccessibilityService`. Con `enforceBlocks` y el servicio de accesibilidad activo, el servicio escucha `TYPE_WINDOW_STATE_CHANGED` (solo el **nombre de paquete**, `canRetrieveWindowContent=false`), cachea settings (Flow) + un snapshot de uso (refresco cada 60s), y al detectar la **transición** a una app distractora/limitada (no en navegación interna, vía `currentApp`) lanza `BlockActivity` por encima. `BlockActivity` (tarea propia, `taskAffinity=""`, `excludeFromRecents`) reusa `FocusBlockDialog`/`LimitReachedDialog`/`FrictionDialog`: "volver"/"ahora no" va al home; "abrir igual" (solo fricción) revela la app (el límite diario ya no ofrece esa salida). La coordinación va por `service/FocusGuard`. `showingFor` evita relanzar un bloqueo mientras uno está en pantalla. La **navegación interna** (mismo paquete) nunca dispara (`pkg == currentApp` corta antes). Ese tracker es frágil, así que hay que cuidar qué lo mueve: **el teclado no cuenta como cambio de app**. Tener entrada de launcher no alcanza para descartarlo (Gboard tiene una), así que se filtra contra `InputMethodManager.enabledInputMethodList` (refrescado junto al snapshot de uso). Si una ventana ajena desplaza a `currentApp`, la **siguiente pantalla interna** de la app deja de cumplir `pkg == currentApp` y se lee como apertura nueva — así aparecía fricción al abrir un perfil, escribir un tuit o cerrar una historia sin haber salido nunca de la app.

Para que la fricción sea **una vez por sentada** y no por cada evento de ventana, el guard modela **sesiones por app** (`FocusGuard`): pasar el gate abre una sesión (`openSession`) y mientras esté viva la app no vuelve a bloquear. Un **desvío a un paquete neutro** (una historia/link que abre una custom tab, cualquier app que abriste desde adentro) solo **aparca** la sesión (`parkSession`): sigue viva por `sessionIdleMinutes` (configurable en Ajustes → Modo Foco, default 5 min), así que seguir un link y volver es la misma sentada. **`sessionIdleMinutes` mide tiempo *fuera*, nunca tiempo de uso**: la sesión de la app en primer plano se guarda con vencimiento `IN_USE` (`Long.MAX_VALUE`) y sólo `parkSession` le pone fecha. Cuando `openSession` fijaba `now + idleMs`, la sentada se vencía sola a los 5 minutos de scrollear y era la otra mitad del bug de arriba: tracker desplazado + sesión vencida = fricción en pleno uso. En cambio, llegar al **home de Foco** o cambiar a **otra app vigilada** (distractora o con límite) **termina** la sesión (`endSession`) y la próxima apertura vuelve a friccionar. La decisión de a-dónde-vas se toma al recibir el nuevo paquete (`newIsWatched` → `endSession` del anterior; si no, `parkSession`). Aparte, un **bridge** de un solo uso (`grantBridge`/`consumeBridge`, `BRIDGE_MS = 12 s`) exime la apertura en sí cuando Foco lanza la app o el usuario elige "abrir igual", para que el guard no bloquee ese mismo arranque; al consumirlo (o al volver dentro de la sesión) se llama `openSession`. Al cerrarse el bloqueo, `BlockActivity` re-arma el tracker (`rearmTracker` → `currentApp=null`) para que reentrar cuente como entrada nueva. Sigue sin haber servicio persistente propio: el trabajo va sobre el `AccessibilityService` opcional que el usuario activa.

**Límite diario en vivo (corte durante el uso).** La decisión de arriba solo se toma en la **transición** hacia la app, así que una sentada que arranca por debajo del límite correría sin tope. Para eso el servicio mantiene un **watcher** (`restartLimitWatch`, un `Job` por app en primer plano, arrancado justo después de fijar `currentApp` y por eso común a todos los caminos: bridge, sesión viva o `Allow`): mientras la app con límite esté al frente, cada tick relee el uso real (`UsageStatsRepository.perAppToday()`, la versión barata de `snapshot()` sin el agregado semanal) y compara contra `appLimits[pkg]`. **No lleva un contador propio**: `UsageStatsManager` es la fuente de verdad y deja de acumular solo cuando la app se pausa. El delay es adaptativo y **apunta al próximo evento** (`restante - warnMs` si el aviso todavía no salió, si no `restante`, acotado a 10–60 s): consulta poco cuando falta mucho, pero aterriza *en* el aviso en vez de pisarlo — con un aviso de 1 min contra un tope de 60 s, dormir siempre hasta el corte hacía que el aviso saliera en un punto al azar entre 60 y 0 s antes del bloqueo. No mide con la pantalla apagada (`PowerManager.isInteractive`) ni mientras hay un bloqueo en pantalla. Al agotarse, `fireLimitBlock` llama a `FocusGuard.endSession(pkg)` —**imprescindible**: si la sentada siguiera viva, volver a entrar pasaría por `sessionAlive()` sin re-evaluar— y lanza `BlockActivity` con `LimitReached` (mismo bloqueo firme que al abrir). Cada tick refresca el cache `usageToday`, así que la siguiente apertura ya bloquea de entrada sin esperar el refresco de 60 s. Con `limitWarnMinutes > 0`, `warnOnce` muestra antes el aviso de cortesía (una vez por app por día) — **es una pantalla, no un Toast**: el sistema descarta los toasts de una app en segundo plano con las notificaciones desactivadas (`Suppressing toast from package … by user request`), que es cualquier instalación sin `POST_NOTIFICATIONS`; una pantalla no necesita permiso porque ya lanzamos `BlockActivity` desde ahí. `warnOnce` también toma `showingFor`, sin lo cual el evento de ventana de nuestra propia pantalla resetearía `currentApp` y mataría el watcher antes del corte.

### Gestos y accesibilidad

`NotificationAccessibilityService` (servicio de accesibilidad mínimo, activado por el usuario) habilita desplegar el panel de notificaciones (swipe hacia abajo en el inicio) y bloquear la pantalla (doble toque). Los gestos se detectan con `pointerInput` + `awaitEachGesture` en `HomeScreen` (swipe hacia abajo = notificaciones; el resto = acceso rápido si el gesto apunta a `quickLaunchDir`). **Importante:** el lado del acceso rápido no tiene página, así que el dedo se mueve en la dirección **opuesta** a la de revelar una página de ese lado — igual que el pager (una página a la izquierda se revela deslizando el dedo a la derecha), el acceso rápido a la derecha se dispara con dedo a la izquierda y viceversa. Si se invierte esta relación, el gesto de acceso rápido roba la dirección que el pager necesita para llegar a la página del lado opuesto y "se rompe" ese lado. Durante el swipe, el contenido del inicio acompaña el dedo (traslación amortiguada vía `graphicsLayer` + estado `Float`, eje según `quickLaunchDir`) y vuelve con un resorte al soltar. El cajón usa la fase `Initial` para volver al inicio con poca resistencia desde el tope sin robarle el gesto a la guía alfabética; ese volver-al-inicio por swipe solo se activa cuando el cajón está asignado "arriba" (`enableSwipeDownToHome`).

La **guía alfabética** (`AlphabetScrubber` en `AppDrawer.kt`) tiene ancho táctil configurable (`scrubberWidth`), resalta la letra de la sección actual (derivada de `firstVisibleItemIndex`) y muestra una burbuja flotante con la letra mientras se arrastra. El **encabezado del cajón** (`DrawerHeader`) puede mostrar un título configurable (`drawerShowTitle`/`drawerTitle`) y agrega `drawerTopSpace` para que la lista empiece más abajo (se oculta al buscar).

### Badges de notificaciones

`NotificationService` (`NotificationListenerService`, opcional, activado por el usuario) publica un `StateFlow<Map<String,Int>>` (`counts`) con la cantidad de notificaciones activas *clearables* por paquete — **solo cuenta, nunca contenido**. Con `showNotificationBadges`, `LauncherRoot` lo colecta y pasa `badgeCounts` a `HomeScreen`/`AppDrawer`; `AppRow` muestra un globo con el número (9+ máximo). Se concede en *Ajustes → acceso a notificaciones* (`ACTION_NOTIFICATION_LISTENER_SETTINGS`).

### Componentes de UI compartidos

`Components.kt` se dividió por componente: `AppRow.kt`, `AppOptionsSheet.kt`, `RenameDialog.kt`, `FrictionDialog.kt`, `BlockScreen.kt` y `ScreenHeader.kt`, todos en el paquete `ui`. `Modifiers.kt` expone `clickableText` (clickable sin ripple).

## Stack técnico

- **Kotlin** 2.0.21 · **Jetpack Compose BOM** 2024.12.01 · **Gradle** 8.9
- `compileSdk` 35 · `minSdk` 26 · `targetSdk` 35
- DataStore Preferences 1.1.1 · kotlinx.serialization 1.7.3
- El tema AMOLED dark/light se aplica en runtime sin reiniciar la Activity

## Dirección visual

**"Frío en reposo, cálido en foco."** La app es negro OLED monocromo y quieto; el estado de **foco** introduce una **luz cálida ámbar** (`FocusWarm`/`OnFocusWarm` en `theme/Theme.kt`), usada independientemente del acento elegido: el chip de foco se enciende en ámbar y `HomeScreen` dibuja un glow radial cálido desde arriba (`drawBehind`) mientras el foco está activo. El **ícono** (`ic_launcher_foreground.xml`, gradientes radiales + `ic_launcher_monochrome.xml`) es ese mismo foco encendido. Tipografía como interfaz (sin íconos): display fino y tight, etiquetas en mayúscula con tracking en las secciones de Ajustes. Grises "elegidos" (el modo claro tiene leve sesgo cálido hacia el ámbar). El ámbar `#D8A24A` es además una de las opciones de acento.
