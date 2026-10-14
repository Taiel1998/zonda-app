# Fase 4, 12 de octubre de 2026: red y sincronización

## Qué se pidió

El cliente de la API con Retrofit, el interceptor del token, la traducción de errores, la sincronización por cursor con la cola de acciones pendientes y la conectividad, según el plan. Taiel pidió que esta fase saliera a nombre de Melinda, que estaba de acuerdo.

Resumen del pedido: "Estaría bueno que la fase 4 sea a nombre de Melinda. Melinda está de acuerdo."

## Qué salió

- `ZondaApi` (Retrofit), los DTO, `InterceptorDeToken`, `ErroresHttp` y un proveedor de API con un cliente por red.
- `SincronizadorHttp`: primero la cola de acciones, después las alertas por cursor, el catálogo y las severidades, y la purga de 30 días.
- `ApiFalsa`, un servidor en memoria con las reglas de la API real, y pruebas con MockWebServer. En total, 99 pruebas, una de ellas contra la API real.
- OkHttp 5 trae una dependencia que pide una versión de Android más nueva que la del proyecto: quedó OkHttp 4.12.

## Qué corrigió o decidió el equipo

- Los commits van a nombre de Melinda, como pidió Taiel con el acuerdo de ella.

## Cómo se comprobó

Las pruebas en local y en el CI, y la prueba contra la API real con un código de `zonda pair`: bajó las 28 alertas y envió una revisión.
