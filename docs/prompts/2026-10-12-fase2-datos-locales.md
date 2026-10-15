# Fase 2, 12 de octubre de 2026: datos locales

## Qué se pidió

La base local como fuente de verdad: Room con sus tablas y DAO, cifrada con SQLCipher; el almacenamiento seguro con el Keystore; DataStore para las preferencias; los conversores y los repositorios sobre Room. Taiel preguntó si la fase era independiente de lo que faltaba de la máquina virtual y dio el ok para seguir.

## Qué salió

- Room 2.8 con siete tablas y tres DAO, cifrada con SQLCipher (la frase de la base, cifrada con una clave AES del Keystore).
- DataStore, los conversores, los repositorios y el `AppContainer` completo.
- 64 pruebas con Robolectric y Room en memoria.
- KSP no funcionaba con el Kotlin integrado de AGP 9. El asistente probó dos salidas: volver al plugin clásico de Kotlin y pasar a KSP 2.3. Las dos anduvieron y quedó KSP 2.3, que deja la configuración más limpia (decisión 11).

## Qué corrigió o decidió el equipo

Los commits van a nombre de Taiel. No hubo cambios al plan de la fase.

## Cómo se comprobó

`./gradlew testDebugUnitTest` con las 64 pruebas en verde; el cifrado de la base, en el emulador.
