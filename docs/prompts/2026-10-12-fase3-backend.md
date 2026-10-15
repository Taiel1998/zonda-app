# Fase 3, 12 de octubre de 2026: la API de alertas

## Qué se pidió

La API en FastAPI con SQLite y el contrato de la sección 13.3 del informe: estado, alertas por cursor, detalle, revisión con conflicto e idempotencia, severidades, pareo y catálogo, más la carga de las alertas de muestra con las fechas corridas, las pruebas y su job de CI. Taiel preguntó si la fase era independiente de las anteriores y dio el ok.

## Qué salió

- Las rutas del contrato sobre `sqlite3` de la biblioteca estándar, sin ORM; tokens de 90 días guardados como hash y códigos de pareo de un uso.
- La semilla con las fechas corridas, el emisor de alertas para la demo y los comandos `zonda pair`, `zonda revoke` y `zonda dispositivos` (el QR en la terminal y en PNG).
- 24 pruebas con pytest y el workflow del backend.
- Al probar, el asistente encontró un error propio: filtrar por severidad después de paginar dejaba páginas incompletas. El filtro pasó a SQL, con su prueba.

## Qué corrigió o decidió el equipo

- La semilla real sale de un script que el asistente escribió para correr en la máquina virtual de la plataforma, que Taiel lee antes de correr y donde él escribe la contraseña. Como la máquina virtual no respondía, se armó una semilla de ejemplo con la misma forma, que se reemplaza sin tocar nada más.
- Los commits van a nombre de Taiel.

## Cómo se comprobó

`pytest` con las 24 pruebas en verde, en local y en el CI, y la documentación automática de la API (`/docs`).
