# Decisiones de diseño y de tecnología

Registro corto de las decisiones que cambian algo respecto de la Etapa 1 o que conviene poder justificar en la defensa. Cada una dice qué se decidió, por qué y qué alternativa quedó descartada.

## 1. Kotlin y Jetpack Compose en lugar de React Native (11/10/2026)

**Decisión.** La app se desarrolla en Kotlin con Jetpack Compose. La Etapa 1 proponía React Native con Expo.

**Por qué.** Es el stack en el que está planteada la materia y su evaluación (Kotlin, Compose, ViewModel, Room, Retrofit). La app es solo para Android y depende de capacidades del sistema (sincronización en segundo plano, Keystore, base cifrada, cámara) que en Kotlin se resuelven de forma directa. El equipo prefiere poder explicar cada parte del código con el vocabulario de la cursada.

**Qué no cambia.** Problema, usuarios, alcance, requisitos, diseño en Figma, arquitectura MVVM + Clean, offline first, contrato de la API, Telegram y HTTPS. La tabla de equivalencias de la sección 15 del informe pasa a ser el stack real.

## 2. Contenedor manual de dependencias en lugar de Hilt

**Decisión.** Las dependencias compartidas (base, cliente HTTP, repositorios, casos de uso) se crean en `AppContainer`, dentro de `ZondaApp`, y los ViewModels las reciben por constructor.

**Por qué.** Se puede explicar línea por línea. Hilt resuelve lo mismo con anotaciones y generación de código, que esconde el cableado y agrega un plugin de Gradle; para una app de un módulo no aporta nada que justifique esa complejidad.

**Alternativa descartada.** Hilt (o Koin). Si la app creciera a varios módulos, sería el momento de reconsiderarlo.

## 3. Una alerta es un episodio, no un flujo de red

**Decisión.** La plataforma agrupa los flujos con el mismo origen y el mismo destino, con hasta 5 minutos sin flujos nuevos, en un episodio. La alerta lleva la cantidad de flujos, el inicio, el fin, el flujo de referencia, el puerto de ese flujo, el puerto más frecuente y la cantidad de puertos distintos.

**Por qué.** Un solo escaneo de puertos real son más de 160.000 flujos; como alertas individuales no se pueden revisar ni caben en los 1.000 registros de caché de RNF07. Agrupar por puerto partiría un escaneo en miles de alertas; agrupar por tipo partiría un mismo ataque si el clasificador etiqueta distinto a algunos flujos.

**Qué cambia.** La entidad Alerta de la sección 14.1 del informe.

## 4. Tipo de ataque y severidad que ordena, no que filtra

**Decisión.** Cada alerta trae un `tipoAtaque` (`fuerza_bruta`, `escaneo`, `ddos`, `probable_falsa_alarma`, `sin_tipificar` o vacío cuando la detectaron solo las reglas o los umbrales) y una `confianzaTipo`. El tipo define la severidad recomendada; ninguna alerta se oculta.

**Por qué.** Un ataque que el clasificador no conoce puede parecerle tráfico legítimo. Si una "probable falsa alarma" se escondiera, un ataque nuevo podría quedar sin ver. Por eso el tipo ordena la cola de atención y la severidad se puede ajustar por red, pero no hay filtro que esconda alertas por defecto.

**Severidad recomendada.** Fuerza bruta: alta. Sin tipo identificado: alta (puede ser un ataque nuevo). Escaneo: media. Denegación de servicio: media. Detectado solo por reglas o umbrales: media. Probable falsa alarma: baja. La pantalla de severidades del Figma de la Etapa 1 se actualiza a esta lista.

## 5. Sin fijación de certificado (pinning)

**Decisión.** La app confía en la verificación normal de Android del certificado de Let's Encrypt que publica Caddy. No se fija el certificado en la app.

**Por qué.** Let's Encrypt rota sus certificados intermedios; un pin mal mantenido deja la app sin poder conectarse. La verificación del sistema cumple RNF04 (TLS 1.2 o superior, certificado de una autoridad reconocida).

**Alternativa.** Fijar el certificado o su huella recibida en el pareo. Queda como mejora posterior.

## 6. JUnit 4 para las pruebas

**Decisión.** Los tests unitarios usan JUnit 4, con MockK y Turbine para ViewModels y Robolectric para Room.

**Por qué.** Es lo que trae la plantilla de Android Studio y lo que funciona directo con Robolectric y con las pruebas de Compose. JUnit 5 requiere un plugin adicional y no suma nada en este proyecto.

## 7. Datos de muestra y fechas corridas

**Decisión.** La API sirve alertas de muestra construidas a partir del dataset público CICIDS2017, procesado por la plataforma de detección del equipo y exportado a `backend/alertas_semilla.json`. Los identificadores de las alertas son estables (se calculan con los valores originales). Al cargar la semilla, el backend corre las fechas para que el episodio más reciente quede en el día de hoy.

**Por qué.** Los datos originales son de julio de 2017; sin correr las fechas, los períodos de 24 horas, 7 y 30 días de la app quedarían vacíos. El corrimiento se declara acá y en el README para que quede claro de dónde sale cada dato.

**Qué flujos entran en la muestra.** De los flujos que marca el modelo de IA, la semilla incluye solo los de la partición de prueba del clasificador de tipo: el tramo final de cada ataque, por orden temporal, más las falsas alarmas de los días evaluados. Sobre los flujos con los que ese clasificador se entrenó, la predicción no sería una prueba real y no coincidiría con los resultados documentados de la plataforma; por eso cada ataque aparece en la muestra como su tramo final y no completo. Los flujos que marcan solo las reglas o los umbrales entran completos, con tipo vacío. Los episodios se arman por origen y destino, sin el tipo ni el puerto en la clave (decisión 3).

## 8. Cifrado de la base local

**Decisión.** La base Room se cifra con SQLCipher (`sqlcipher-android`, `SupportOpenHelperFactory`). La frase de cifrado se genera al azar una sola vez y se guarda cifrada con una clave AES que vive en el Android Keystore.

**Por qué.** RNF03. La frase no puede vivir "adentro" del Keystore porque de ahí no se puede extraer una clave en crudo; lo que el Keystore guarda es la clave que cifra la frase.

**Pruebas.** Los tests de DAO corren con Room en memoria sin cifrar (Robolectric no carga las bibliotecas nativas de SQLCipher); el cifrado se prueba en el emulador.

## 9. Enlace del aviso de Telegram

**Decisión.** El aviso lleva un enlace `https://taiel1998.github.io/a/{id}`. Android lo verifica como App Link con el archivo `assetlinks.json` publicado en la raíz de ese dominio (repositorio `Taiel1998.github.io`). Si la app no está instalada, el navegador muestra una página que indica abrir Zonda.

**Por qué.** Los botones de un bot de Telegram solo aceptan enlaces http, https y tg, así que un esquema propio (`zonda://`) no sirve. El nombre de DuckDNS de la API apunta a una dirección privada y no sirve para alojar el archivo de verificación.

**Plan B.** Si la verificación falla en el teléfono de la demo, el aviso dice "Abrí Zonda" y la app sincroniza al abrirse.
