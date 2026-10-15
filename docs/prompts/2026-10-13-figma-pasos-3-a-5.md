# 13 de octubre de 2026: pasos 3 a 5 del Figma (primer inicio, Conexión, Alertas, Detalle y recorrido)

## Qué se pidió

Seguir el plan "antes y después" que Taiel respondió: el primer inicio y Conexión (paso 3), Alertas con el Detalle y la revisión (paso 4) y el recorrido (paso 5), todo fiel al Figma.

Para el recorrido, Taiel eligió la versión del Figma, con el elemento resaltado y la tarjeta al lado, con un tope: si en un día de trabajo no salía el resaltado, quedaba la tarjeta sola y le avisaba.

## Qué salió

- Conexión como el Figma, con la barra de la primera descarga; el QR suma el vencimiento del código y el backend devuelve cuántas alertas hay para descargar.
- Alertas con los filtros y estados del Figma, el detalle con los estados de la revisión (pendiente, conflicto, pendiente de descarga), los motivos y comentarios del Figma con el comentario obligatorio también en el backend, y el protocolo junto al puerto.
- El recorrido con el resaltado: un velo al 50 % y un borde verde alrededor del elemento, con la tarjeta arriba o abajo. Salió en el día.
- 235 pruebas en la app y 33 en el backend. Decisiones 19 a 23.
- Como el servicio de Figma para leer el diseño llegó a su límite de uso, las medidas se tomaron de capturas del prototipo, con herramientas que el asistente armó para medir.
- Probando en el emulador, el asistente encontró dos errores de los campos de texto (letras que se perdían mientras el teclado escribía) y los corrigió con su prueba.

## Qué corrigió o decidió el equipo

- La versión del recorrido con el resaltado y el tope de un día, como se cuenta arriba.
- Lo que el Figma no tiene, o tiene distinto, quedó anotado para que Meli lo revise en el Figma.
- Los commits van a nombre de Taiel.

## Cómo se comprobó

Las pruebas en local y en el CI, y capturas del emulador al lado de cada cuadro del Figma.
