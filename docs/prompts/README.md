# Uso de asistentes de IA en este proyecto

La consigna (sección 8) permite usar asistentes de IA y aclara que el equipo tiene que poder comprender, justificar y modificar el código que incorpora. Esta carpeta registra cómo se usaron.

## Cómo trabajamos

- El asistente es Claude Code, de Anthropic: el 11 y el 12/10 con el modelo Claude Fable 5.1 (el plan y las primeras fases) y desde el 12/10 con Claude Opus 5.5, que Taiel eligió para cuidar el cupo de uso. Trabajó sobre el informe de la Etapa 1, el Figma, la consigna y el material de la cátedra.
- Antes de cada fase, el asistente presenta un plan y espera el ok de Taiel. El equipo corrige el plan cuando hace falta; lo que cambió está en el archivo de cada fase.
- El asistente escribe el código, las pruebas y la documentación, y prueba cada fase en emuladores. El plan preveía que los integrantes escribieran a mano algunas partes para estudiarlas (dos casos de uso del dominio, partes de Room y de Retrofit, y la pantalla de Estado); por los tiempos del cuatrimestre, Taiel le pidió al asistente que las escribiera también.
- Los commits y el push los hace el asistente, con el nombre del integrante a cargo de cada fase y nunca con el suyo: lo decidió Taiel el 12/10, y cada fase a nombre de Melinda se hizo con su acuerdo. Los pull requests los abre el asistente desde la cuenta de un integrante y los mergea el equipo.
- Cada pull request responde, para cada pieza importante, las seis preguntas de la consigna: qué problema resuelve, qué capa modifica, qué dependencias agrega, si hay una alternativa más simple, cómo se comprueba y qué pasa si falla.
- Por cada fase, el asistente deja al equipo una guía con qué hay, cómo se prueba y qué decisiones conviene saber explicar en la defensa. Las decisiones que cambian algo del informe están en `docs/decisiones.md`.
- Ningún secreto va al repositorio: los tokens están en `backend/.env` y la clave de firma, en la computadora que arma el APK.

## Registro

Un archivo por sesión de trabajo o por fase, con la fecha, qué se pidió, qué salió, qué corrigió o decidió el equipo y cómo se comprobó. Los pedidos están resumidos de las conversaciones con el asistente.

| Fecha | Archivo | Tema |
|---|---|---|
| 11/10/2026 | `2026-10-11-plan-etapa2.md` | Plan de implementación de la Etapa 2 y decisiones iniciales |
| 12/10/2026 | `2026-10-12-fase0-base.md` | Fase 0: proyecto base, enlace del aviso y esqueleto del backend |
| 12/10/2026 | `2026-10-12-fase1-dominio.md` | Fase 1: la capa de dominio |
| 12/10/2026 | `2026-10-12-fase2-datos-locales.md` | Fase 2: Room cifrada, Keystore y DataStore |
| 12/10/2026 | `2026-10-12-fase3-backend.md` | Fase 3: la API de alertas |
| 12/10/2026 | `2026-10-12-fase4-sincronizacion.md` | Fase 4: red y sincronización |
| 12/10/2026 | `2026-10-12-fase5-alertas-y-detalle.md` | Fase 5: pantallas Alertas y Detalle |
| 12/10/2026 | `2026-10-12-fase6-revision.md` | Fase 6: revisión de una alerta |
| 12/10/2026 | `2026-10-12-fase7-primer-inicio.md` | Fase 7: primer inicio y pareo |
| 12 y 13/10/2026 | `2026-10-12-fase8-segundo-plano.md` | Fase 8: segundo plano y Ajustes, y el plan para que la app quede como el Figma |
| 13/10/2026 | `2026-10-13-figma-pasos-3-a-5.md` | Primer inicio, Conexión, Alertas, Detalle y recorrido como el Figma |
| 13/10/2026 | `2026-10-13-fase9-resumen.md` | Fase 9: el Resumen |
| 13 y 14/10/2026 | `2026-10-14-fase10-telegram.md` | Fase 10: Telegram |
| 14/10/2026 | `2026-10-14-fase11-https.md` | Fase 11: HTTPS final |
| 14/10/2026 | `2026-10-14-estado.md` | La pantalla de Estado |
| 14/10/2026 | `2026-10-14-fase12-cierre.md` | Fase 12: APK firmado, guion de la demo, README y este registro, y después `zonda emitir` para la demo |
