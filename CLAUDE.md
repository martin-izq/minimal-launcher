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
PackageManager ──► AppRepository ──┐
DataStore       ──► SettingsRepository ──► LauncherViewModel (LauncherUiState) ──► UI
UsageStatsManager ► UsageStatsRepository ┘
```

`LauncherViewModel` combina los 3 flows en un único `StateFlow<LauncherUiState>` mediante `combine()`. Toda mutación pasa por métodos públicos del ViewModel (lanzar app, alternar favorito, ocultar, renombrar, etc.).

### Estructura de la UI

`LauncherRoot` contiene un `VerticalPager` de 2 páginas:
- **Página 0** → `HomeScreen`: reloj, fecha, batería, favoritos, accesos rápidos
- **Página 1** → `AppDrawer`: lista alfabética de apps + búsqueda en tiempo real

Sobre el pager se superponen 2 overlays controlados por flags en `LauncherUiState`:
- `ScreenTimeScreen`: estadísticas de uso (requiere permiso especial)
- `SettingsScreen`: preferencias + gestión de apps ocultas

### Datos persistidos (DataStore)

`SettingsRepository` gestiona todo mediante DataStore Preferences:
- `favorites`: lista ordenada (separada por `\n`)
- `hidden` / `distracting`: conjuntos de package names
- `renames`: `Map<String, String>` serializado como JSON con kotlinx.serialization
- Booleanos: `showClock`, `showDate`, `showBattery`, `showScreenTimeHome`, `frictionEnabled`, `amoledDark`
- `frictionSeconds`: Int (valores válidos: 3, 5, 10)

### Permiso de uso (`PACKAGE_USAGE_STATS`)

Este permiso **no se puede solicitar con un dialog estándar**; el usuario debe concederlo manualmente en *Ajustes → Privacidad → Aplicaciones con acceso de uso*. `UsageStatsRepository.hasPermission()` usa `AppOpsManager` para verificarlo. Toda la UI de tiempo de pantalla queda bloqueada detrás de este permiso.

### Pantalla de fricción

`FrictionDialog` (en `Components.kt`) se muestra al abrir apps marcadas como "distractoras" si `frictionEnabled = true`. Muestra el tiempo de uso del día y un countdown configurable (3/5/10 s) antes de habilitar el botón "Abrir de todas formas".

## Stack técnico

- **Kotlin** 2.0.21 · **Jetpack Compose BOM** 2024.12.01 · **Gradle** 8.9
- `compileSdk` 35 · `minSdk` 26 · `targetSdk` 35
- DataStore Preferences 1.1.1 · kotlinx.serialization 1.7.3
- El tema AMOLED dark/light se aplica en runtime sin reiniciar la Activity
