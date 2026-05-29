# Minimal — Launcher Android minimalista con Screen Time

Clon de "Minimalistic Phone" hecho en **Kotlin + Jetpack Compose**. Launcher de solo texto, blanco y negro, pensado para reducir el tiempo de pantalla.

## Funcionalidades (todas las "pro")

- **Inicio de solo texto**: reloj grande, fecha, batería opcional. Sin íconos.
- **Favoritos ilimitados** en el inicio, reordenables (mover ↑/↓).
- **Cajón de apps** alfabético con **buscador** (Enter abre el primer resultado).
- **Renombrar apps** (alias visible).
- **Ocultar apps** (y volver a mostrarlas desde Ajustes).
- **Reducir distracciones**: marcá apps como *distractoras* y aparece una **pantalla de fricción** ("¿Seguro que querés abrir X?") con cuenta regresiva configurable (3/5/10 s) que muestra cuánto la usaste hoy.
- **Screen Time**: tiempo total de hoy, **desbloqueos**, **gráfico de los últimos 7 días** y **ranking por app**.
- **Resumen de uso** en el inicio (tocable).
- **Tema oscuro AMOLED** (negro puro) o claro.
- **Gestos**: deslizá hacia arriba para abrir el cajón de apps.
- Acceso a **Info de la app** y **Desinstalar** desde mantener presionado.
- El botón **Atrás** nunca sale del launcher (vuelve al inicio).

## Cómo compilar

1. Abrí la carpeta `MinimalLauncher/` en **Android Studio** (Ladybug o más nuevo). Al sincronizar, Android Studio genera el Gradle wrapper automáticamente.
   - Alternativa por terminal (si tenés Gradle 8.9+): `gradle wrapper` y luego `./gradlew assembleDebug`.
2. Conectá el celular (depuración USB activada) y dale **Run ▶**, o instalá el APK:
   `app/build/outputs/apk/debug/app-debug.apk`

Requisitos: AGP 8.7.2 · Kotlin 2.0.21 · compileSdk 35 · minSdk 26 · JDK 17.

## Después de instalar

1. **Ponerlo como launcher por defecto**: presioná el botón Home y elegí *Minimal*, o entrá a *Ajustes → Establecer como launcher por defecto*.
2. **Activar Screen Time**: la primera vez, *Screen Time → Conceder acceso* te lleva a *Acceso a datos de uso* en Ajustes de Android. Activá *Minimal*. (Es un permiso especial que el sistema no concede automáticamente.)

## Notas técnicas

- Lista de apps vía `PackageManager.queryIntentActivities` (intent LAUNCHER).
- Tiempo de uso vía `UsageStatsManager.queryAndAggregateUsageStats`; desbloqueos contando eventos `KEYGUARD_HIDDEN`.
- Preferencias en `DataStore` (favoritos, ocultas, distractoras, renombres en JSON).
- `QUERY_ALL_PACKAGES` está declarado: es un uso permitido por Google Play para launchers, pero tenelo en cuenta si lo publicás.

## Estructura

```
app/src/main/java/com/martin/minimallauncher/
├── MainActivity.kt            # host Compose, HOME/back/resume
├── LauncherViewModel.kt       # estado combinado
├── data/                      # AppInfo, AppRepository, SettingsRepository, UsageStatsRepository
├── ui/                        # LauncherRoot, HomeScreen, AppDrawer, ScreenTimeScreen, SettingsScreen, Components
│   └── theme/Theme.kt
└── util/TimeFormat.kt
```
