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
- Booleanos: `showClock`, `showDate`, `showBattery`, `showScreenTimeHome`, `frictionEnabled`, `amoledDark`, `clockOpensAlarms`, `hideStatusBar`, `alphabetIndex`, `searchBarBottom`, `drawerShowTitle`, `drawerShowUsage`, `onboarded`
- Direcciones de gestos (permutación de `{0,1,2}` = izq/der/arriba): `widgetsDir`, `quickLaunchDir`, `drawerDir` (la clave legacy `widgets_on_left` se lee solo para migrar)
- Ints (tamaños/alineación/espaciado): `clockSize`, `dateSize`, `favoritesSize`, `homeAlign`, `verticalPos`, `appDrawerSize`, `appDrawerAlign`, `scrubberWidth` (ancho táctil de la guía, dp), `drawerTopSpace` (espacio superior del cajón, dp)
- `drawerTitle`: String (título del cajón; vacío → default localizado `drawer_default_title`)
- `frictionSeconds`: Int (valores válidos: 3, 5, 10)
- `quickLaunchPackage`: String? (app de acceso rápido)

`WidgetsRepository` (`launcher_widgets`, separado) persiste la lista de `WidgetPlacement(appWidgetId, heightDp)` como JSON. `WidgetController` (creado en `MainActivity`) aloja el `AppWidgetHost`, hace el binding con consentimiento y cachea las `AppWidgetHostView`.

### Permiso de uso (`PACKAGE_USAGE_STATS`)

Este permiso **no se puede solicitar con un dialog estándar**; el usuario debe concederlo manualmente en *Ajustes → Privacidad → Aplicaciones con acceso de uso*. `UsageStatsRepository.hasPermission()` usa `AppOpsManager` para verificarlo. Toda la UI de tiempo de pantalla queda bloqueada detrás de este permiso.

### Pantalla de fricción

`FrictionDialog` (en `ui/FrictionDialog.kt`) se muestra al abrir apps marcadas como "distractoras" si `frictionEnabled = true`. Muestra el tiempo de uso del día y un countdown configurable (3/5/10 s) antes de habilitar el botón "Abrir igual".

### Modo Foco — límites de tiempo (Fase 2, en progreso)

`ui/FocusLimit.kt` define `TimeLimitDialog` (elegir el límite diario por app, en minutos, desde el menú largo) y `LimitReachedDialog` (bloqueo suave: se muestra al abrir una app que ya superó su límite del día, con countdown antes de "Abrir igual"). La verificación se hace en `onAppClick` de `LauncherRoot` comparando `usage.perAppToday[pkg]` contra `appLimits[pkg]`, con prioridad sobre la fricción. **Solo se aplica al lanzar desde Foco y requiere el permiso de uso** (sin `perAppToday` el límite nunca dispara).

`data/FocusSession.kt` modela las **sesiones de foco programadas** (`FocusSession`: `start`/`end` en minutos del día, `days` = weekdays 1..7, `enabled`), serializadas como JSON en `focusSessions`. `ui/FocusSessions.kt` tiene el editor (`FocusSessionEditorDialog`, con steppers de ±15 min y chips de días) y `FocusBlockDialog` (firme, informativo).

**Bloqueo agresivo:** `LauncherRoot` tiene un tick por minuto (`nowTick`) y calcula la sesión activa (`activeFocusSession`), `blockedPackages` = las apps distractoras si hay sesión, y `focusUntil` (hora de fin). Ese set se pasa a `HomeScreen` y `AppDrawer`; `AppRow` con `blocked = true` se **grisa**, pero el **tap sí llega**: rutea por `onAppClick` y muestra el `FocusBlockDialog` (mensaje firme "en pausa a propósito", no abre) en vez de lanzar — así no parece un error. El long-press sigue gestionándola. El auto-launch de búsqueda ignora apps bloqueadas, y el acceso rápido se rutea por `onAppClick`. `onAppClick` es el único choke point: orden **sesión de foco (firme)** → límite diario → fricción → lanzar. Además, `HomeScreen` muestra un **chip de foco** (`showFocusOnHome`, default on): tappable para **iniciar foco manual** (25/50 min o indefinido, vía `FocusControlSheet` —un bottom sheet minimalista—) cuando está inactivo, para **reanudar** una sesión programada salteada, o para **salir** cuando está activo. Con `dndInFocus`, un `LaunchedEffect` pone **No molestar** (`util/Dnd.kt`, `setInterruptionFilter`) mientras el foco está activo y lo saca al terminar; requiere el acceso a No molestar (`ACCESS_NOTIFICATION_POLICY`, concedido por el usuario, sin adb). Con `strictFocus` (**modo estricto/hardcore**), un foco con **fin fijo** (temporizado o sesión programada; nunca indefinido) queda **bloqueado**: `focusLocked` oculta la opción de salir del sheet, se deshabilita la opción "hasta que lo apague" al iniciar, y el toggle de modo estricto en Ajustes queda deshabilitado mientras hay un foco bloqueado en curso (para no poder desactivarlo y escapar). El estado combinado se calcula en `LauncherRoot`: `focusActiveNow = scheduledActive || manualActive`, donde `manualActive = now < manualFocusUntil` (epoch; `Long.MAX_VALUE` = indefinido) y `scheduledActive = hay sesión && now >= focusSkipUntil`. "Salir" limpia `manualFocusUntil` y, si era una sesión programada, setea `focusSkipUntil` al fin de esa ventana (la saltea sin desactivar futuras sesiones). `manualFocusUntil`/`focusSkipUntil` son estado transitorio (no se restauran en el import de backup). La UI de Modo Foco vive en una única sección de Ajustes (sesiones + fricción + apps distractoras).

**Bloqueo en todo el sistema (`enforceBlocks`, default on).** La misma decisión de `onAppClick` se extrajo a `data/FocusGate.kt` (`decideBlock(...)` → `BlockDecision` = `Allow`/`FocusBlock`/`LimitReached`/`Friction`, con la prioridad **sesión → límite → fricción**), usada tanto por `LauncherRoot.onAppClick` como por el `NotificationAccessibilityService`. Con `enforceBlocks` y el servicio de accesibilidad activo, el servicio escucha `TYPE_WINDOW_STATE_CHANGED` (solo el **nombre de paquete**, `canRetrieveWindowContent=false`), cachea settings (Flow) + un snapshot de uso (refresco cada 60s), y al detectar la **transición** a una app distractora/limitada (no en navegación interna, vía `lastForeground`) lanza `BlockActivity` por encima. `BlockActivity` (tarea propia, `taskAffinity=""`) reusa `FocusBlockDialog`/`LimitReachedDialog`/`FrictionDialog`: "volver" va al home; "abrir igual" (fricción/límite) concede un grace corto vía `service/FocusGuard` (`showingFor` + grace) y revela la app. Sigue sin haber servicio persistente propio: el trabajo va sobre el `AccessibilityService` opcional que el usuario activa.

### Gestos y accesibilidad

`NotificationAccessibilityService` (servicio de accesibilidad mínimo, activado por el usuario) habilita desplegar el panel de notificaciones (swipe hacia abajo en el inicio) y bloquear la pantalla (doble toque). Los gestos se detectan con `pointerInput` + `awaitEachGesture` en `HomeScreen` (swipe hacia abajo = notificaciones; el resto = acceso rápido si el gesto apunta a `quickLaunchDir`). **Importante:** el lado del acceso rápido no tiene página, así que el dedo se mueve en la dirección **opuesta** a la de revelar una página de ese lado — igual que el pager (una página a la izquierda se revela deslizando el dedo a la derecha), el acceso rápido a la derecha se dispara con dedo a la izquierda y viceversa. Si se invierte esta relación, el gesto de acceso rápido roba la dirección que el pager necesita para llegar a la página del lado opuesto y "se rompe" ese lado. Durante el swipe, el contenido del inicio acompaña el dedo (traslación amortiguada vía `graphicsLayer` + estado `Float`, eje según `quickLaunchDir`) y vuelve con un resorte al soltar. El cajón usa la fase `Initial` para volver al inicio con poca resistencia desde el tope sin robarle el gesto a la guía alfabética; ese volver-al-inicio por swipe solo se activa cuando el cajón está asignado "arriba" (`enableSwipeDownToHome`).

La **guía alfabética** (`AlphabetScrubber` en `AppDrawer.kt`) tiene ancho táctil configurable (`scrubberWidth`), resalta la letra de la sección actual (derivada de `firstVisibleItemIndex`) y muestra una burbuja flotante con la letra mientras se arrastra. El **encabezado del cajón** (`DrawerHeader`) puede mostrar un título configurable y/o el resumen de uso del día, y agrega `drawerTopSpace` para que la lista empiece más abajo (se oculta al buscar).

### Badges de notificaciones

`NotificationService` (`NotificationListenerService`, opcional, activado por el usuario) publica un `StateFlow<Map<String,Int>>` (`counts`) con la cantidad de notificaciones activas *clearables* por paquete — **solo cuenta, nunca contenido**. Con `showNotificationBadges`, `LauncherRoot` lo colecta y pasa `badgeCounts` a `HomeScreen`/`AppDrawer`; `AppRow` muestra un globo con el número (9+ máximo). Se concede en *Ajustes → acceso a notificaciones* (`ACTION_NOTIFICATION_LISTENER_SETTINGS`).

### Componentes de UI compartidos

`Components.kt` se dividió por componente: `AppRow.kt`, `AppOptionsSheet.kt`, `RenameDialog.kt`, `FrictionDialog.kt` y `ScreenHeader.kt`, todos en el paquete `ui`. `Modifiers.kt` expone `clickableText` (clickable sin ripple).

## Stack técnico

- **Kotlin** 2.0.21 · **Jetpack Compose BOM** 2024.12.01 · **Gradle** 8.9
- `compileSdk` 35 · `minSdk` 26 · `targetSdk` 35
- DataStore Preferences 1.1.1 · kotlinx.serialization 1.7.3
- El tema AMOLED dark/light se aplica en runtime sin reiniciar la Activity

## Dirección visual

**"Frío en reposo, cálido en foco."** La app es negro OLED monocromo y quieto; el estado de **foco** introduce una **luz cálida ámbar** (`FocusWarm`/`OnFocusWarm` en `theme/Theme.kt`), usada independientemente del acento elegido: el chip de foco se enciende en ámbar y `HomeScreen` dibuja un glow radial cálido desde arriba (`drawBehind`) mientras el foco está activo. El **ícono** (`ic_launcher_foreground.xml`, gradientes radiales + `ic_launcher_monochrome.xml`) es ese mismo foco encendido. Tipografía como interfaz (sin íconos): display fino y tight, etiquetas en mayúscula con tracking en las secciones de Ajustes. Grises "elegidos" (el modo claro tiene leve sesgo cálido hacia el ámbar). El ámbar `#D8A24A` es además una de las opciones de acento.
