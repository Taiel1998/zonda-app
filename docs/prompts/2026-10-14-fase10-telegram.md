# Fase 10, 13 y 14 de octubre de 2026: Telegram

## Qué se pidió

El aviso inmediato por Telegram: el bot que vincula el chat y manda un aviso por cada alerta nueva, las pantallas del Figma para conectarlo y lo que muestra el detalle cuando se abre desde un aviso.

## Qué salió

- El bot dentro de la API, sin una biblioteca aparte: lee los mensajes con `getUpdates` y manda los avisos con `sendMessage`. Un aviso por alerta y por chat, según cómo avisa cada severidad en ese teléfono.
- Conectar Telegram en el primer inicio y en Ajustes, el aviso de prueba y desvincular.
- El enlace del aviso lleva, además del id, lo mismo que dice el aviso; si la alerta todavía no está en el teléfono, el detalle muestra eso hasta poder descargarla. La página del dominio suma "Abrir en Zonda" para cuando el navegador interno de Telegram no abre la app.
- Un Telegram simulado para probar en el emulador sin cuenta. 308 pruebas en la app y 49 en el backend. Decisión 25.

## Qué corrigió o decidió el equipo

- El texto del aviso lo decidió Taiel con el asistente: "Alerta ALTA · SSH · 02:09", "Tocá para ver el detalle en Zonda." y la red. Sin nombres de equipos ni cantidad de conexiones, porque lo prometen el RNF02 y las condiciones de uso; "no incluye direcciones IP" va una sola vez, en la respuesta del bot al vincular.
- La estrategia de prueba la propuso Taiel: probar en el emulador y repetir todo después en un teléfono real. En el emulador, iniciar sesión en Telegram pedía un pago; Taiel eligió hacer esa parte en su teléfono, donde ya tiene la sesión.
- Los commits van a nombre de Taiel.

## Cómo se comprobó

Las pruebas del bot con un Telegram falso (vínculo, códigos vencidos o usados, grupos, cada modo de aviso y que ningún aviso tenga una IP o un nombre de equipo) y el emulador con el Telegram simulado. Falta la prueba con Telegram de verdad en un teléfono.
