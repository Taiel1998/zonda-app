# Fase 5, 12 de octubre de 2026: pantallas Alertas y Detalle

## Qué se pidió

Las pantallas de la lista de alertas, con sus filtros y estados, y del detalle con los tres veredictos, con ViewModel y `StateFlow`. La pantalla de Estado quedaba para que la escribiera Taiel después del 25/10.

Resumen del pedido: "A nombre de Melinda, dale con la fase 5."

## Qué salió

- Los componentes compartidos (etiquetas de severidad y de estado, tarjeta de alerta, estados de pantalla y hojas de opciones), el formato de fechas y números, y `viewModelDeZonda`, que crea cada ViewModel con el contenedor manual.
- `AlertasViewModel` y `DetalleViewModel`, con un estado por pantalla y las pantallas sin ViewModel adentro, para probarlas solas (decisión 14).
- 116 pruebas, entre ellas las primeras de pantallas con Compose sobre Robolectric.

## Qué corrigió o decidió el equipo

- Estado quedó como pantalla provisoria para Taiel.
- Los commits van a nombre de Melinda y el pull request salió de su cuenta.

## Cómo se comprobó

Las pruebas en local y en el CI, y el emulador: la lista sin red pareada y el enlace del aviso, que abre el detalle.
