# Fase 6, 12 de octubre de 2026: revisión de una alerta

## Qué se pidió

El RF04 según el plan: revisar en dos pasos (ataque real o falso positivo, después el motivo y el comentario), Deshacer durante 5 segundos, cambiar la revisión, la revisión pendiente sin conexión, el conflicto cuando otra persona la revisó antes (409) y Reintentar si el envío falla. Taiel dio el ok para seguir, también a nombre de Melinda.

## Qué salió

- `ResolverEnvioRevision`, con las tres salidas de una revisión que no llegó: aplicar la propia igual, quedarse con la otra o reintentar. Aplicar la propia vuelve a mandar la misma acción con la versión que se vio, en lugar de forzarla sin mirar (decisión 15).
- La hoja Revisar en dos pasos con un comentario propuesto según el motivo, la ventana de Deshacer y el envío desde un alcance de toda la app, para que la revisión salga aunque se cierre el detalle.
- 137 pruebas en total.

## Qué corrigió o decidió el equipo

- Desde esta fase, Taiel eligió el modelo Claude Opus 5.5 en lugar de Claude Fable 5.1, para cuidar el cupo de uso semanal.
- Los commits van a nombre de Melinda.

## Cómo se comprobó

Las pruebas de los ViewModels con Turbine, de la base con Room en memoria y del cliente con la API falsa; en el emulador, la revisión con y sin conexión.
