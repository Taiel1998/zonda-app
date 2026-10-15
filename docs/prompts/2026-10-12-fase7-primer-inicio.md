# Fase 7, 12 de octubre de 2026: primer inicio y pareo

## Qué se pidió

El RF05 menos Telegram: la bienvenida con las condiciones de uso, el tutorial que se puede omitir, las preferencias de avisos, la severidad de cada tipo de ataque, la pantalla Conexión (QR, ingreso a mano, errores, sin cámara y permiso rechazado) y el recorrido de once pasos.

## Qué salió

- `ArranqueViewModel`, que decide por dónde entra la app (Estado, primer inicio o Conexión), y el primer inicio como un grafo de navegación con un ViewModel compartido.
- `ParearRed`, que normaliza la dirección y el código, verifica el servidor con `GET /salud` antes de gastar el código y parea. La severidad elegida en el primer inicio se manda al servidor solo si la persona cambió algún valor recomendado (decisión 16).
- El escáner de QR con CameraX y ML Kit, con el modelo incluido en la app, y los permisos de cámara y de notificaciones.
- El recorrido de once pasos y 163 pruebas en total.

## Qué corrigió o decidió el equipo

Los commits van a nombre de Melinda y el pull request salió de su cuenta. No hubo cambios al plan de la fase.

## Cómo se comprobó

Las pruebas en local y en el CI, y en el emulador contra la API local: el primer inicio completo, el pareo a mano, las 28 alertas descargadas, el recorrido y una revisión enviada.
