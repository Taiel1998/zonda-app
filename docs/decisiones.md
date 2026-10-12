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

## 10. Cómo representa el dominio el tipo vacío y los motivos de revisión

**Decisión.** Cuando la API manda `tipoAtaque: null` (la alerta la detectaron solo las reglas o los umbrales), el dominio usa el valor `SOLO_REGLAS_O_UMBRALES` del enum `TipoAtaque`. Los motivos de revisión son una lista fija (`Motivo`), cada uno atado a un resultado; el texto de cada motivo y el comentario que propone están en `strings.xml`.

**Por qué.** Con un valor explícito, la pantalla de severidades y los filtros tratan ese caso como uno más, sin un `null` que haya que contemplar aparte en cada lugar. Los motivos fijos evitan escribir el motivo a mano (heurística 6 de Nielsen) y hacen comparables las revisiones en el Resumen.

## 11. KSP 2.3 con el Kotlin integrado de AGP 9

**Decisión.** Room genera su código con KSP. Se usa la línea nueva de KSP (2.3.x), que tiene su propia numeración y funciona con el Kotlin integrado de Android Gradle Plugin 9, sin aplicar el plugin `org.jetbrains.kotlin.android`.

**Por qué.** La versión de KSP atada a Kotlin 2.2.21 (2.2.21-2.0.5) corta la configuración del proyecto con el mensaje "KSP is not compatible with Android Gradle Plugin's built-in Kotlin". Se probó el camino viejo (desactivar el Kotlin integrado con `android.builtInKotlin=false`, conservar el DSL anterior con `android.newDsl=false` y aplicar el plugin clásico de Kotlin): compila, pero depende de dos opciones que AGP 10 elimina. Con KSP 2.3.12 el proyecto queda igual que uno recién creado por Android Studio.

**Qué cambia.** Nada en el código; solo la versión de KSP en `gradle/libs.versions.toml`.

## 12. Retrofit con Gson, DTO aparte y errores traducidos en un solo lugar

**Decisión.** La app habla con la API con Retrofit sobre OkHttp y convierte el JSON con Gson. Los DTO (`data/remote/Dtos.kt`) copian el contrato tal cual (camelCase, enumeraciones en minúsculas, fechas ISO 8601 como texto) y los mappers (`data/mapper/MapeosRemotos.kt`) los pasan al dominio. Toda llamada pasa por `conErroresTraducidos`, que convierte `HttpException`, los errores de certificado y los de entrada y salida en `ErrorZonda`: 401 → NoAutorizado, 404 → NoEncontrado, 409 → Conflicto con la revisión ajena, 410 → CursorInvalido (sincronización) o CodigoVencido (pareo), 400 → CodigoInvalido (pareo), 5xx y tiempos de espera → ErrorDelServidor o SinConexión.

**Por qué.** Retrofit y Gson son lo que usan los ejemplos de la cátedra y lo que el equipo puede explicar. Separar DTO de dominio permite que la API cambie un nombre sin tocar las pantallas, y traducir los errores en un solo lugar evita que cada repositorio interprete códigos HTTP. Se descartó kotlinx.serialization porque exige anotar cada clase y sumar un plugin del compilador. En debug, un `network_security_config` permite texto claro solo hacia `10.0.2.2` (el emulador); en release Android exige HTTPS.

**Qué cambia.** Las fechas se leen con `OffsetDateTime.parse`, porque la API manda `+00:00` y el `Instant.parse` de Android 8 no lo acepta. El tipo vacío (`null`) se vuelve `SOLO_REGLAS_O_UMBRALES` al entrar. Un valor que el dominio no conoce se informa como error del servidor, no como caída de la app.

## 13. Orden de la sincronización y reintentos

**Decisión.** `SincronizadorHttp` envía primero la cola de acciones pendientes (en orden de creación) y después baja las alertas por cursor, de a 200, guardando el cursor después de cada página. Cada sincronización es un intento por acción: a los cinco fallos la acción pasa a `ERROR` y espera un reintento manual; un 409 la deja en `CONFLICTO` con la revisión ajena, sin frenar al resto; un 401 o la falta de conexión detienen todo y la acción vuelve a `PENDIENTE`. Un 410 dispara una sincronización completa que conserva alertas y acciones pendientes. Al final se actualizan catálogo y severidades, se purgan las alertas de más de 30 días y se registra la hora.

**Por qué.** Enviar antes de bajar evita que el servidor devuelva "sin revisar" una alerta que el teléfono ya revisó. Guardar el cursor por página hace que un corte a mitad de camino no obligue a bajar todo de nuevo. La espera exponencial entre intentos la aporta WorkManager (fase 8); hacerla dentro de "Sincronizar ahora" dejaría a la persona esperando con la pantalla bloqueada.

**Qué cambia.** `ResultadoSincronizacion` cuenta acciones enviadas y con error; los conflictos no se cuentan como error porque esperan una decisión de la persona.

## 14. Un estado por pantalla y pantallas sin ViewModel adentro

**Decisión.** Cada pantalla de datos tiene un ViewModel que expone un único `StateFlow` con una clase de estado (`AlertasUiState`, `DetalleUiState`): el contenido como clase sellada (cargando, sin red, vacío, lista o contenido), el filtro, si hay conexión, si se está sincronizando y un aviso de un solo uso para el snackbar. La pantalla se divide en dos funciones: `AlertasScreen` crea el ViewModel y `AlertasContenido` recibe el estado y devuelve las acciones. Los ViewModels se crean con `viewModelDeZonda { contenedor -> ... }`, que toma las dependencias del `AppContainer`.

**Por qué.** Con un solo estado, lo que se ve es siempre una función de un valor y no hay combinaciones imposibles (una lista y un error a la vez). Separar el contenido del ViewModel permite probar la pantalla con Compose sobre Robolectric pasándole un estado armado a mano, sin base ni red. El aviso de un solo uso (la pantalla lo muestra y avisa `avisoMostrado()`) evita que un snackbar reaparezca al rotar el teléfono.

**Qué cambia.** Los textos siguen en `strings.xml`; la traducción de cada valor del dominio a su texto está en un solo archivo (`ui/Textos.kt`), igual que el formato de fechas y números (`ui/Formato.kt`).

## 15. Deshacer antes de enviar y conflicto resuelto con la versión vista

**Decisión.** Al confirmar una revisión, se guarda en el teléfono y queda en la cola, pero no se envía durante cinco segundos: mientras el aviso está a la vista, Deshacer la descarta sin que haya salido. Cuando termina la ventana se sincroniza en un alcance de corrutinas de toda la app (`alcanceDeLaApp`), así sale aunque la persona ya haya cerrado el detalle; sin conexión queda pendiente y sale cuando vuelve la red (Alertas y Detalle sincronizan solos al reconectarse). Ante un 409, "Aplicar la mía igual" vuelve a encolar la misma acción con la versión que ahora tiene la alerta, la que ya incluye la revisión de la otra persona; "Quedarme con la otra" la descarta. Una acción en error vuelve a la cola con Reintentar. Las sincronizaciones se ponen en fila con un `Mutex`.

**Por qué.** Si la revisión saliera en el momento, Deshacer tendría que mandar otra revisión al servidor y el historial mostraría una marca que la persona nunca quiso dejar. Para el conflicto, mandar `forzar: true` pisaría también a una tercera persona que revisara mientras tanto; con la versión vista, el servidor acepta la revisión porque la persona ya vio lo último, y si apareció algo nuevo vuelve a avisar. `forzar` sigue en el contrato de la API. El `Mutex` evita que dos sincronizaciones a la vez (al volver la red desde dos pantallas) manden la misma acción dos veces.

**Qué cambia.** La revisión que muestra el detalle es la de este teléfono si hay una pendiente, aunque la del servidor tenga fecha posterior, porque es la que define el estado. La espera exponencial entre intentos automáticos llega con WorkManager en la fase 8.

## 16. Primer inicio y pareo

**Decisión.** La entrada depende de dos datos: si hay una red pareada (va a Estado) y si la introducción ya se hizo (si no, primer inicio; si sí, directo a Conexión). El primer inicio es un grafo anidado de Navigation (bienvenida, condiciones, tutorial, avisos, severidades y pareo) con un ViewModel compartido. La introducción queda hecha al terminar las severidades, antes del pareo, para que no vuelva a aparecer aunque el pareo quede para después. La severidad elegida antes de parear se aplica a la red recién pareada solo si la persona cambió algo de lo recomendado. El pareo normaliza la dirección (sin esquema se usa `https://`) y el código (mayúsculas y guion), verifica el servidor con `GET /salud` y recién después canjea el código. El QR se lee con CameraX y ML Kit con el modelo incluido en la app. El recorrido de once pasos es una tarjeta sobre las pestañas que se muestra una vez después del pareo. Telegram se agrega en la fase 10, como paso que se puede posponer.

**Por qué.** El código es de un solo uso y vence a los 10 minutos: si se mandara a un servidor que no responde o con un certificado inválido, se perdería. Mandar las severidades recomendadas pisaría la configuración que otra persona ya eligió para la red; solo se mandan si la persona eligió otra cosa. El modelo de ML Kit incluido funciona sin descargar nada de Google Play, que puede faltar en un teléfono de la empresa. Sin cámara o sin permiso, la misma pantalla ofrece ingresar los datos a mano y abrir los ajustes (RNF06).

**Qué cambia.** El nombre con el que el teléfono aparece como autor de sus revisiones es el fabricante y el modelo (por ejemplo "Google Pixel 7"). La app pide los permisos de cámara y de notificaciones solo cuando hacen falta, y sigue funcionando si se rechazan.

## 17. Segundo plano, notificaciones, varias redes y acceso vencido

**Decisión.** Un `CoroutineWorker` periódico de WorkManager sincroniza cada 15 minutos (el mínimo que permite Android) solo con red, todas las redes pareadas, y reintenta con espera exponencial si no hay servidor; a los cinco intentos espera al período siguiente. WorkManager se inicializa desde `ZondaApp` con una fábrica de workers que toma las dependencias del `AppContainer` (sin Hilt). La notificación local tiene un canal por modo de aviso (sonido, vibración, silencio) y cada severidad va al canal que eligió la persona; una por red y severidad, que reemplaza a la anterior; tocarla abre la pestaña Alertas por un enlace interno (`zonda://alertas`) que no se publica a otras apps. Ante un 401, la sincronización marca la red con `accesoVencido` (columna nueva, migración 1 → 2 de Room) y la app muestra "Conectar de nuevo" sin borrar nada; el pareo siguiente de la misma red conserva alertas, cursor y acciones. Cambiar de red activa es un solo `UPDATE`: todas las pantallas observan la red activa. "Borrar datos locales" borra alertas, revisiones y acciones de todas las redes y reinicia sus cursores, pero conserva los pareos; "Quitar este dispositivo" revoca el token y borra todo lo de esa red, y si no queda ninguna, la app vuelve a Conexión.

**Por qué.** WorkManager es lo que Android ofrece para trabajo diferido que sobrevive al cierre de la app y respeta la batería (RNF08); un servicio en primer plano consumiría más y molestaría con una notificación permanente. Los canales por modo de aviso dejan que el sistema aplique sonido o vibración aunque la app esté cerrada, y que la persona los ajuste desde Android. Guardar `accesoVencido` en la base (y no en memoria) hace que el aviso sobreviva al cierre de la app y que la tarea en segundo plano no insista contra un token revocado. Separar "borrar datos" de "quitar el dispositivo" responde a dos necesidades distintas: liberar espacio o empezar de cero sin perder el acceso, y dejar de estar pareado.

**Qué cambia.** `ResultadoSincronizacion` cuenta las nuevas por severidad para la notificación. El inicializador automático de WorkManager está desactivado en el manifiesto. La base pasa a la versión 2 con una migración explícita; en desarrollo también funciona desinstalar la app. WorkManager no deja adelantar un trabajo periódico, así que el build de debug trae un receptor (`SincronizarAhoraReceiver`) que corre el mismo worker una vez desde `adb`, para probar la notificación sin esperar; en release no existe.
