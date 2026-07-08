# Roadmap — Foco

**North Star:** Foco no es "otro launcher minimalista", es un **launcher de uso intencional / enfoque**, ético (todo local, sin ads, sin tracking) y pulido. El minimalismo es el medio; el bienestar digital es el diferenciador.

## Fase 0 — Listo para publicar (técnico)
- [x] Rebrand a **Foco** (nombre + ícono de foco minimalista)
- [x] Asignación libre de gestos (widgets / acceso rápido / drawer)
- [x] Mejoras del cajón (encabezado, scrubber configurable, auto-launch)
- [x] Settings reorganizados (secciones colapsables)
- [ ] Firma de release + R8/`shrinkResources`, build **AAB**
- [ ] **Privacy policy** (URL) + **Data safety** ("no se recopilan datos")
- [ ] Declarar `QUERY_ALL_PACKAGES` y el servicio de accesibilidad en la Consola

## Fase 1 — Retención inmediata: Onboarding
Wizard de primer arranque: bienvenida, poner como launcher por defecto, permisos
opcionales explicados (uso / accesibilidad), tips de uso.
- Flag `onboarded` en settings; deep-links a los ajustes de permisos.
- *Alto impacto, bajo esfuerzo — evita la desinstalación en el primer minuto.*

## Fase 2 — Diferenciador: Modo Foco ⭐
El corazón de la marca, sobre la base de la pantalla de fricción:
- [x] **Límites de tiempo por app** (UsageStats): al superar X min → bloqueo suave.
- [x] **Sesiones programadas** (franjas horarias) que bloquean apps distractoras.
- [ ] **Nudge a escala de grises** (deep-link / atajo, opcionalmente programado).
- Nota: el enforcement actual es solo-al-lanzar-desde-Foco (sin servicio en background → cero batería). Un bloqueo "duro" (recientes/notificaciones) requeriría monitoreo en runtime → evaluar batería y política de Play.

## Fase 3 — Paridad: Notificaciones mínimas
Badges de no-leídas en favoritos y/o lista mínima (`NotificationListenerService`,
permiso sensible → declarar).

## Fase 4 — Búsqueda universal
Extender el buscador del cajón a contactos, ajustes y "buscar en web".

## Fase 5 — Monetización ética
Tip jar / "supporter" con perk cosmético (temas/acentos, estilos de ícono) vía
Play Billing. Flag `isSupporter`, pantalla de apoyo no invasiva. Sin quitar nada
funcional. Sin ads, sin suscripción agresiva.

## Fase 6 — Pulido / extra
Temas y acentos, backup/restore de settings, gestos configurables extra.

## Transversal — Reducir riesgo en Play
Minimizar la dependencia del **servicio de accesibilidad** (mayor riesgo de
política) y dejar todos los permisos justificados.

---

**Orden recomendado:** Fase 0 → Fase 1 (onboarding) → Fase 2 (Modo Foco).
