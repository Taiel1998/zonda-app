# Fase 1, 12 de octubre de 2026: la capa de dominio

## Qué se pidió

Los modelos, las reglas y los casos de uso en Kotlin puro, sin Android, con las interfaces de los repositorios para probarlos sin base ni red. Según el plan, Taiel iba a escribir dos casos de uso para estudiarlos (`ObtenerEstadoRed` y `RevisarAlerta`) y el asistente dejaba la estructura y un ejemplo completo.

Resumen del pedido: "Arrancá con la fase 1. Los commits hacelos vos también, pero con el nombre de Taiel y de Meli cuando corresponda."

## Qué salió

- Los modelos (alerta como episodio, revisión, red, severidad por tipo de ataque, veredictos, resumen), las reglas del semáforo y de la revisión, y las interfaces de los repositorios.
- Once casos de uso con sus pruebas, con repositorios en memoria.

## Qué corrigió o decidió el equipo

- Taiel le pidió al asistente que escribiera también los dos casos de uso que le tocaban a él, con sus pruebas.
- Las pruebas de dominio que iba a escribir Rodrigo pasaron a las fases siguientes.

## Cómo se comprobó

39 pruebas con JUnit 4 en verde, ninguna ignorada, en local y en el CI.
