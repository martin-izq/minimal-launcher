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

**MinimalLauncher** es un launcher de Android minimalista (sin íconos, solo texto) escrito en Kotlin + Jetpack Compose. Sigue MVVM con un único Activity y navegación por overlays modales, sin back stack de Fragments.

### Flujo de datos

```
PackageManager     ──► AppRepository        ──┐
DataStore          ──► SettingsRepository   ──┤
DataStore          ──► WidgetsRepository    ──┼─► LauncherViewModel (LauncherUiState) ──► UI
UsageStatsManager  ──► UsageStatsRepository ──┘
```

`LauncherViewModel` combina los 4 flows en un único `StateFlow<LauncherUiState>` mediante `combine()`. Toda mutación pasa por métodos públicos del ViewModel (lanzar app, alternar favorito, ocultar, renombrar, agregar/mover/redimensionar widget, etc.).

### Estructura de la UI

`LauncherRoot` contiene un `HorizontalPager` de 2 páginas:
- **Página de widgets** → `WidgetScreen`: widgets reales de terceros vía `AppWidgetHost`
- **Página de inicio** → un `VerticalPager` anidado de 2 páginas:
  - `HomeScreen`: reloj, fecha, batería, favoritos
  - `AppDrawer`: lista alfabética de apps + búsqueda en tiempo real

El lado de la pantalla de widgets (izquierda/derecha) es configurable. El **acceso rápido** no es una página del pager: es un gesto en `HomeScreen` (deslizar hacia el lado opuesto a los widgets) que lanza una app configurable. El `HorizontalPager` usa `beyondViewportPageCount = 1` para mantener viva la página de widgets (si no, las `AppWidgetHostView` se desanclan y quedan invisibles al volver).

Sobre el pager se superponen 2 overlays controlados por flags locales en `LauncherRoot`:
- `ScreenTimeScreen`: estadísticas de uso (requiere permiso especial)
- `SettingsScreen`: preferencias + gestión de apps ocultas

### Internacionalización (i18n)

Todos los textos visibles están en recursos: `res/values/strings.xml` (inglés, idioma por defecto) y `res/values-es/strings.xml` (español). Android elige el idioma según el locale del SO. **No agregar literales de UI en el código**: usar `stringResource(R.string.…)` (o `context.getString` fuera de composables) y añadir la clave en ambos `strings.xml`. La fecha del inicio y los días del gráfico de Screen Time usan `Locale.getDefault()`.

### Datos persistidos (DataStore)

`SettingsRepository` (`launcher_settings`) gestiona las preferencias vía DataStore Preferences:
- `favorites`: lista ordenada (separada por `\n`)
- `hidden` / `distracting`: conjuntos de package names
- `renames`: `Map<String, String>` serializado como JSON (kotlinx.serialization); **solo aplica a favoritos**, el cajón muestra el nombre original
- Booleanos: `showClock`, `showDate`, `showBattery`, `showScreenTimeHome`, `frictionEnabled`, `amoledDark`, `clockOpensAlarms`, `widgetsOnLeft`, `alphabetIndex`, `searchBarBottom`
- Ints (tamaños/alineación): `clockSize`, `dateSize`, `favoritesSize`, `homeAlign`, `verticalPos`, `appDrawerSize`, `appDrawerAlign`
- `frictionSeconds`: Int (valores válidos: 3, 5, 10)
- `quickLaunchPackage`: String? (app de acceso rápido)

`WidgetsRepository` (`launcher_widgets`, separado) persiste la lista de `WidgetPlacement(appWidgetId, heightDp)` como JSON. `WidgetController` (creado en `MainActivity`) aloja el `AppWidgetHost`, hace el binding con consentimiento y cachea las `AppWidgetHostView`.

### Permiso de uso (`PACKAGE_USAGE_STATS`)

Este permiso **no se puede solicitar con un dialog estándar**; el usuario debe concederlo manualmente en *Ajustes → Privacidad → Aplicaciones con acceso de uso*. `UsageStatsRepository.hasPermission()` usa `AppOpsManager` para verificarlo. Toda la UI de tiempo de pantalla queda bloqueada detrás de este permiso.

### Pantalla de fricción

`FrictionDialog` (en `ui/FrictionDialog.kt`) se muestra al abrir apps marcadas como "distractoras" si `frictionEnabled = true`. Muestra el tiempo de uso del día y un countdown configurable (3/5/10 s) antes de habilitar el botón "Abrir igual".

### Gestos y accesibilidad

`NotificationAccessibilityService` (servicio de accesibilidad mínimo, activado por el usuario) habilita desplegar el panel de notificaciones (swipe hacia abajo en el inicio) y bloquear la pantalla (doble toque). Los gestos se detectan con `pointerInput` + `awaitEachGesture` en `HomeScreen`; el cajón usa la fase `Initial` para volver al inicio con poca resistencia desde el tope sin robarle el gesto a la guía alfabética.

### Componentes de UI compartidos

`Components.kt` se dividió por componente: `AppRow.kt`, `AppOptionsSheet.kt`, `RenameDialog.kt`, `FrictionDialog.kt` y `ScreenHeader.kt`, todos en el paquete `ui`. `Modifiers.kt` expone `clickableText` (clickable sin ripple).

## Stack técnico

- **Kotlin** 2.0.21 · **Jetpack Compose BOM** 2024.12.01 · **Gradle** 8.9
- `compileSdk` 35 · `minSdk` 26 · `targetSdk` 35
- DataStore Preferences 1.1.1 · kotlinx.serialization 1.7.3
- El tema AMOLED dark/light se aplica en runtime sin reiniciar la Activity
