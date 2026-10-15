# Fase 8, 12 y 13 de octubre de 2026: segundo plano, Ajustes y la app como el Figma

## Qué se pidió

La sincronización en segundo plano con WorkManager, la notificación local de alertas altas, varias redes, la severidad desde Ajustes, Quitar este dispositivo y el acceso vencido (401).

## Qué salió primero, y qué corrigió el equipo

Al presentar la fase, el asistente avisó dónde se había apartado del diseño: un canal de notificación por cada modo de aviso (el informe pide uno solo), Ajustes en una sola pantalla (en el Figma es una lista que abre otras) y diálogos en lugar de las hojas inferiores con botón rojo del Figma.

Resumen de la respuesta de Taiel: "Cambialo, hacé como mostró el Figma. Hacé la app como está en el Figma, repasá todo el contexto y dame un plan de antes y después para que se acerque al Figma."

El asistente armó un plan pantalla por pantalla (cómo estaba cada una y cómo quedaba como el Figma), Taiel lo respondió y pidió: "Hacelo todo fiel al Figma, ante la duda". Desde ahí es una regla del equipo: si hay dos formas técnicas, se hace la que replica el Figma y se cuenta la contra.

## Qué salió al final

- Los pasos 1 y 2 de ese plan en esta fase: el tema y los componentes del Figma (colores, Inter con los tamaños del informe, barra inferior, hojas de confirmación con botón rojo) y Ajustes con sus pantallas.
- WorkManager cada 15 minutos con red, un solo canal de notificación con su interruptor en Ajustes, enlaces internos para abrir la lista o una alerta desde la notificación y la migración de la base para el acceso vencido.
- 193 pruebas en total. Decisiones 17 y 18.

## Qué más decidió el equipo

- Los commits de esta fase en adelante van a nombre de Taiel.
- Los pasos 3 a 5 del plan van en otra rama con su propio pull request.

## Cómo se comprobó

Las pruebas en local y en el CI; en el emulador, la notificación con el worker corrido a mano desde la terminal (solo en debug), Ajustes contra cada cuadro del Figma y Quitar este dispositivo.
