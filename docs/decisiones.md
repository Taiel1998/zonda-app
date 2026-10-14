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

**Qué cambia (fase 10).** El enlace suma la red y lo mismo que dice el aviso: `https://taiel1998.github.io/a/{id}?red=&severidad=&servicio=&deteccion=&aviso=`. Si la alerta todavía no está en el teléfono, el detalle lo muestra como "Lo que trajo el aviso" hasta poder descargarla (RF03, criterio 3). Como un enlace lo puede armar cualquiera, cada valor se valida, uno que no cierra se descarta, y nunca reemplaza los datos que manda la API; un id de red que no es de ninguna red pareada no cambia la red activa. La página del dominio suma "Abrir en Zonda" para cuando el navegador interno de Telegram muestra la página en vez de abrir la app: en Android abre la alerta con un intent al paquete de la app.

## 10. Cómo representa el dominio el tipo vacío y los motivos de revisión

**Decisión.** Cuando la API manda `tipoAtaque: null` (la alerta la detectaron solo las reglas o los umbrales), el dominio usa el valor `SOLO_REGLAS_O_UMBRALES` del enum `TipoAtaque`. Los motivos de revisión son una lista fija (`Motivo`), cada uno atado a un resultado; el texto de cada motivo y el comentario que propone están en `strings.xml`.

**Por qué.** Con un valor explícito, la pantalla de severidades y los filtros tratan ese caso como uno más, sin un `null` que haya que contemplar aparte en cada lugar. Los motivos fijos evitan escribir el motivo a mano (heurística 6 de Nielsen) y hacen comparables las revisiones en el Resumen.

## 11. KSP 2.3 con el Kotlin integrado de AGP 9

**Decisión.** Room genera su código con KSP. Se usa la línea nueva de KSP (2.3.x), que tiene su propia numeración y funciona con el Kotlin integrado de Android Gradle Plugin 9, sin aplicar el plugin `org.jetbrains.kotlin.android`.

**Por qué.** La versión de KSP atada a Kotlin 2.2.21 (2.2.21-2.0.5) corta la configuración del proyecto con el mensaje "KSP is not compatible with Android Gradle Plugin's built-in Kotlin". Se probó el camino viejo (desactivar el Kotlin integrado con `android.builtInKotlin=false`, conservar el DSL anterior con `android.newDsl=false` y aplicar el plugin clásico de Kotlin): compila, pero depende de dos opciones que AGP 10 elimina. Con KSP 2.3.12 el proyecto queda igual que uno recién creado por Android Studio.

**Qué cambia.** Nada en el código; solo la versión de KSP en `gradle/libs.versions.toml`.

## 12. Retrofit con Gson, DTO aparte y errores traducidos en un solo lugar

**Decisión.** La app habla con la API con Retrofit sobre OkHttp y convierte el JSON con Gson. Los DTO (`data/remote/Dtos.kt`) copian el contrato tal cual (camelCase, enumeraciones en minúsculas, fechas ISO 8601 como texto) y los mappers (`data/mapper/MapeosRemotos.kt`) los pasan al dominio. Toda llamada pasa por `conErroresTraducidos`, que convierte `HttpException`, los errores de certificado y los de entrada y salida en `ErrorZonda`: 401 → NoAutorizado, 404 → NoEncontrado, 409 → Conflicto con la revisión ajena, 410 → CursorInvalido (sincronización) o CodigoVencido (pareo), 400 → CodigoInvalido (pareo), 5xx y tiempos de espera → ErrorDelServidor o SinConexión.

**Por qué.** Retrofit y Gson son lo que usan los ejemplos de la cátedra y lo que el equipo puede explicar. Separar DTO de dominio permite que la API cambie un nombre sin tocar las pantallas, y traducir los errores en un solo lugar evita que cada repositorio interprete códigos HTTP. Se descartó kotlinx.serialization porque exige anotar cada clase y sumar un plugin del compilador. En debug, un `network_security_config` permite texto claro solo hacia `10.0.2.2` (el emulador); en release no hay texto claro hacia ninguna dirección (decisión 26).

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

**Decisión.** La entrada depende de dos datos: si hay una red pareada (va a Estado) y si la introducción ya se hizo (si no, primer inicio; si sí, directo a Conexión). El primer inicio es un grafo anidado de Navigation (bienvenida, tutorial, avisos, severidades y pareo) con un ViewModel compartido; las condiciones se abren en una hoja desde la bienvenida. La introducción queda hecha al terminar las severidades, antes del pareo, para que no vuelva a aparecer aunque el pareo quede para después. La severidad elegida antes de parear se aplica a la red recién pareada solo si la persona cambió algo de lo recomendado. El pareo normaliza la dirección (sin esquema se usa `https://`) y el código (mayúsculas y guion), verifica el servidor con `GET /salud` y recién después canjea el código. El QR se lee con CameraX y ML Kit con el modelo incluido en la app. El recorrido de once pasos es una tarjeta sobre las pestañas que se muestra una vez después del pareo. Telegram se agrega en la fase 10, como paso que se puede posponer.

**Por qué.** El código es de un solo uso y vence a los 10 minutos: si se mandara a un servidor que no responde o con un certificado inválido, se perdería. Mandar las severidades recomendadas pisaría la configuración que otra persona ya eligió para la red; solo se mandan si la persona eligió otra cosa. El modelo de ML Kit incluido funciona sin descargar nada de Google Play, que puede faltar en un teléfono de la empresa. Sin cámara o sin permiso, la misma pantalla ofrece ingresar los datos a mano y abrir los ajustes (RNF06).

**Qué cambia.** El nombre con el que el teléfono aparece como autor de sus revisiones es el fabricante y el modelo (por ejemplo "Google Pixel 7"). La app pide los permisos de cámara y de notificaciones solo cuando hacen falta, y sigue funcionando si se rechazan.

## 17. Segundo plano, notificaciones, varias redes y acceso vencido

**Decisión.** Un `CoroutineWorker` periódico de WorkManager sincroniza cada 15 minutos (el mínimo que permite Android) solo con red, todas las redes pareadas, y reintenta con espera exponencial si no hay servidor; a los cinco intentos espera al período siguiente. WorkManager se inicializa desde `ZondaApp` con una fábrica de workers que toma las dependencias del `AppContainer` (sin Hilt). La notificación local sale solo cuando la sincronización trae alertas altas nuevas y el interruptor "Notificaciones del sistema" de Ajustes está prendido: un solo canal ("Alertas altas") con el sonido del sistema y una notificación por red, que reemplaza a la anterior. Tocarla abre Alertas de esa red filtrada en "Alta · Sin revisar", o el detalle si trajo una sola alerta, por dos enlaces internos (`zonda://alertas?red=…&estado=…&severidad=…` y `zonda://alerta/<id>?red=…`) que viajan en un intent explícito y no se publican a otras apps. Las notificaciones van en un grupo con un resumen propio que abre Alertas. Con sonido, con vibración o sin aviso son los modos de Telegram (fase 10) y se guardan en el teléfono. Ante un 401, la sincronización marca la red con `accesoVencido` y la app muestra "Conectar de nuevo" sin borrar nada; el pareo siguiente de la misma red conserva alertas, cursor y acciones. La migración de la versión 1 a la 2 de Room suma esa columna y las fechas de emisión y de vencimiento del acceso, que `POST /pareo` ahora devuelve y que Detalles técnicos muestra junto con la versión de TLS y el emisor del certificado, leídos de la conexión de la llamada a `/salud`. Cambiar de red activa es un solo `UPDATE`: todas las pantallas observan la red activa. "Borrar datos locales" borra alertas, revisiones y acciones de todas las redes y reinicia sus cursores, pero conserva los pareos; "Quitar este dispositivo" revoca el token y borra todo lo de esa red, y si no queda ninguna, la app vuelve a Conexión.

**Por qué.** WorkManager es lo que Android ofrece para trabajo diferido que sobrevive al cierre de la app y respeta la batería (RNF08); un servicio en primer plano consumiría más y molestaría con una notificación permanente. El aviso inmediato llega por Telegram (informe, sección 6); la app suma su notificación solo para lo urgente, así no repite el aviso de Telegram ni interrumpe con las medias y las bajas, que esperan en la lista. Un canal por severidad, para que el sistema aplique un sonido distinto a cada una, queda como mejora posterior. Abrir la lista ya filtrada muestra justo lo que avisó la notificación. Guardar `accesoVencido` en la base (y no en memoria) hace que el aviso sobreviva al cierre de la app y que la tarea en segundo plano no insista contra un token revocado. Separar "borrar datos" de "quitar el dispositivo" responde a dos necesidades distintas: liberar espacio o empezar de cero sin perder el acceso, y dejar de estar pareado.

**Qué cambia.** `ResultadoSincronizacion` trae los ids de las altas nuevas, para abrir el detalle cuando es una sola. El inicializador automático de WorkManager está desactivado en el manifiesto. La base pasa a la versión 2 con una migración explícita, probada en `MigracionesTest` sobre una base como la que deja la fase 7. WorkManager no deja adelantar un trabajo periódico, así que el build de debug trae un receptor (`SincronizarAhoraReceiver`) que corre el mismo worker una vez desde `adb`, para probar la notificación sin esperar; en release no existe.

## 18. La tipografía y las medidas del Figma

**Decisión.** Las pantallas siguen el Figma de la etapa 1 con las medidas de su inspector. La letra es Inter, sin espaciado entre letras y con el interlineado propio de la letra, que es el "Auto" del Figma. Cada estilo del Figma va en el rol de Material 3 que lo usa: el título de cada pantalla en Medium 22 (`titleLarge`), los títulos de fila y de tarjeta en Medium 15 (`titleSmall`), el texto de las hojas en Regular 14 (`bodyMedium`), los detalles en Regular 13 (`bodySmall`), los botones y enlaces en SemiBold 14 (`labelLarge`), los títulos chicos de sección en SemiBold 13 (`labelMedium`) y las etiquetas de severidad en SemiBold 11 (`labelSmall`). Los que no tienen rol propio son extensiones de `Typography`: las notas en Regular 12, el título de las hojas en SemiBold 20, las opciones en Medium 13, los valores en Medium 14 y la barra inferior en 12 (SemiBold la pestaña elegida y Medium las otras). Las piezas compartidas repiten las medidas del Figma: barra superior de 51 dp, tarjetas blancas con borde gris, esquinas de 12 y 10 entre piezas, botones de 41 dp con esquinas de 20, opciones de 28 dp, el interruptor con el círculo de 24 en los dos estados, hojas inferiores con esquinas de 20 y el aviso inferior oscuro de 40 dp. En Ajustes, "Condiciones de uso" es una pantalla con cuatro tarjetas (66-3558), distinta de la hoja de condiciones de la bienvenida (50-830).

**Por qué.** Material 3 trae espaciado entre letras y renglones más altos que el Figma; con Inter, la misma pantalla quedaba más grande y algunos textos se partían en dos renglones ("Borrar datos locales"). Poner cada estilo en su rol hace que los componentes del sistema (barra superior, botones, aviso inferior) tomen el estilo del Figma sin cambiarlos uno por uno.

**Qué cambia.** El primer inicio y Conexión ya siguen el Figma (decisión 20). Alertas y Detalle ya usan la letra nueva; sus medidas se ajustan en el paso siguiente.

## 19. Botones con el alto del Figma y área táctil de 48 dp

**Decisión.** Los botones ocupan en la pantalla su alto visible, 41 dp, como en el Figma. Material 3 reserva 48 dp de alto para el área táctil de cada botón y suma esa diferencia al diseño; los botones de la app se arman dentro de `SinReservaTactil`, que apaga esa reserva (`LocalMinimumInteractiveComponentSize`). El área táctil sigue siendo de 48 dp: Compose acepta un toque cerca de algo que se puede tocar si cae dentro de los 48 dp alrededor y no hay otra cosa debajo (`minimumTouchTargetSize` de la configuración de la vista). Los botones deshabilitados van en gris cálido con el texto gris, como "Conectar" sin datos en el Figma.

**Por qué.** Con la reserva, cada botón sumaba 7 dp y los espacios entre piezas quedaban más grandes que en el Figma: entre "Reintentar" y "Corregir la dirección" había 23 dp en lugar de 16, y la diferencia se notaba al poner la captura al lado del cuadro. Bajar la reserva en toda la app también achicaría casillas, interruptores e íconos que el Figma dibuja con su área completa; por eso se baja solo en los botones.

**Qué cambia.** El área táctil de los botones ya no se ve en el árbol de accesibilidad como un rectángulo de 48 dp, pero sigue respondiendo al toque en esos 48 dp. Los chips como "Pegar" usan lo mismo.

## 20. Conexión como el Figma y la primera descarga en Estado

**Decisión.** El QR que genera `zonda pair` suma cuándo vence el código (`zonda:<url>#<código>;vence=<fecha ISO>`), la respuesta de `POST /pareo` suma cuántas alertas de los últimos 30 días tiene la red (`alertas30Dias`) y la primera página de `GET /alertas` sin cursor suma el `total` de esa descarga, que además se limita a los últimos 30 días. El pareo termina en "Red conectada" con esa cantidad ("213 alertas de los últimos 30 días listas para descargar") y sin bajar nada; "Continuar" arranca la primera descarga en el alcance de toda la app y Estado la muestra con su barra ("120 de 213 · últimos 30 días · solo la primera vez"). El sincronizador publica cuántas van en un `StateFlow` mientras baja desde cero. Si el servidor no respondió y el código vino en el QR, el error dice hasta qué hora sigue valiendo el código. Al tocar un campo del formulario se oculta la parte de arriba; el código se escribe en ocho casillas y se puede pegar, también el contenido completo del QR.

**Por qué.** Así lo muestra el Figma (35-692 y 50-616): conectarse y bajar las alertas son dos momentos distintos, y con la barra en Estado la persona ya ve la pantalla principal mientras baja. Sin el total, la barra no puede decir "120 de 213"; sin el vencimiento en el QR, la línea "El código sigue siendo válido hasta las 02:19" no se puede mostrar. Los tres cambios del backend son campos nuevos que un cliente anterior ignora. La descarga inicial se limita a los 30 días porque es lo que el teléfono guarda (RNF03): bajar más sería tráfico que la purga borra enseguida.

**Qué cambia.** El paso "Descargando las alertas" sale de Conexión. Mientras Estado sea provisoria, la tarjeta de la descarga va arriba de esa pantalla, con un ViewModel chico (`DescargaInicialViewModel`) que pasa al de Estado cuando se escriba. Con el ingreso a mano no hay hora de vencimiento y esa línea no aparece. "Revisada" sigue en verde, como en la lista del Figma (2-3) y en los cuadros "revisada con éxito"; la página 3 del tutorial y el detalle 51-2379 la muestran en gris, una diferencia del propio Figma que queda anotada para corregirla ahí.

## 21. La revisión guarda cuándo se hizo en el teléfono

**Decisión.** Cada revisión tiene dos horas: `revisadaEn`, cuándo se revisó en el teléfono, y `fecha`, cuándo llegó al servidor. La app manda `revisadaEn` al enviar la revisión (es la hora de la acción pendiente) y el servidor la devuelve en el historial. Si no viene, vale la hora de llegada, y una hora posterior a la llegada (un reloj adelantado) también se toma como la de llegada. La base del teléfono pasa a la versión 3 con esa hora, el protocolo de cada alerta y los motivos del Figma.

**Por qué.** El historial del Figma (51-2379) muestra "02:13 Revisada: ataque real · Escalado al proveedor · sin conexión, quedó pendiente" y "02:16 Enviada al servidor". Sin conexión la revisión sale bastante después de hacerse, y con una sola hora el historial diría que se revisó cuando llegó. Guardar la hora en el servidor, y no solo en el teléfono, hace que el historial sea el mismo en todos los teléfonos de la red.

**Qué cambia.** El detalle arma la línea de tiempo con la detección, cada revisión y, si llegó más de un minuto después de hacerse, el envío al servidor. El aviso de Telegram se suma en la fase 10, cuando el servidor informe cuándo lo mandó. Los motivos anteriores se pasan a los nuevos en las dos bases (el backend al arrancar y la app en la migración 2 a 3) y `Motivo.desdeNombre` los acepta si llega alguno.

## 22. Filtros de la lista y estados del detalle como el Figma

**Decisión.** El filtro de estado tiene cuatro opciones y Revisadas incluye los falsos positivos, como el cuadro "filtro Revisadas aplicado" del Figma. Las severidades se combinan con casillas y, sin filtro, la hoja las muestra todas marcadas. El período por defecto es "Últimos 30 días", que es todo lo que guarda el teléfono (respuesta 1 del plan). Más filtros ofrece los servicios y los equipos destino de las alertas guardadas, del más frecuente al menos: los cinco primeros servicios tienen chip propio y el resto entra en "Otros". El chip de estado va siempre elegido, como en el Figma, y los demás cuando no están en su valor de siempre. La línea de resumen dice qué se filtró y solo sin filtros suma la hora de los datos. En el detalle, la revisión recién hecha va abajo con "enviada", como los cuadros "revisada con éxito", y al volver a abrir la alerta va arriba con el historial (51-2379). En un conflicto, la etiqueta muestra la revisión que quedó en el servidor.

**Por qué.** Son los cuadros del Figma: la lista con cada filtro aplicado, las hojas 51-1805 a 51-2202 y el detalle en cada estado. La nota de Más filtros dice que los servicios y los equipos los informa el servidor y que un equipo sin nombre se muestra con su dirección, así que la lista sale de las alertas que mandó el servidor y no de una lista fija en la app. Con cinco chips y "Otros" la hoja entra en la pantalla aunque la red tenga muchos servicios.

**Qué cambia.** El filtro del dominio pasa a `FiltroEstado`, un conjunto de severidades y `FiltroServicio` (uno o "Otros"); la consulta de Room los resuelve en una sola sentencia. El vacío con filtros ofrece "Limpiar filtros" y, sin nada guardado y con la sincronización fallida, la lista muestra "No se pudo cargar" con Reintentar. El botón "Mantener la de <nombre>" usa el nombre del teléfono si entra en medio ancho; si no, dice "Mantener la otra".

## 23. Recorrido con el resaltado y la tarjeta al lado

**Decisión.** El recorrido de once pasos se dibuja en una capa encima de toda la app: el velo negro al 50 %, el elemento del paso a la vista con un borde verde claro (de 3, 5 por fuera del elemento y con esquinas de 14) y la tarjeta del Figma al lado, debajo si entra y si no arriba. Cada pantalla marca sus elementos con `objetivoDelRecorrido`, que guarda dónde quedaron en un registro compartido, y el recorrido navega solo hasta la pantalla de cada paso: Estado, Alertas, el detalle de la primera alerta de la lista y Ajustes.

**Por qué.** Así lo muestra el Figma (107-3321 a 107-4731) y lo pide el RF05 con el "recorrido de once pasos". Medir la posición real de cada elemento hace que el resaltado siga a la pantalla en cualquier tamaño y con la letra agrandada; si el elemento no se ve entero, la pantalla se desplaza hasta él. Sin animaciones, como quedó en el plan.

**Qué cambia.** El Resumen marca las cifras y el período, y sus dos pasos lo abren encima de Estado (decisión 24). La barra de Estado marca el ícono del Resumen y el de sincronizar, y Estado marca el semáforo y los contadores (decisión 27): los pasos 1 a 4 resaltan cada pieza como los cuadros 107-3321 a 107-3758. La tarjeta sola, en el medio, queda para cuando el elemento no está en la pantalla, por ejemplo los contadores mientras la red no tiene datos.

## 24. El Resumen se calcula en el teléfono y se comparte como imagen

**Decisión.** El Resumen (RF07) se calcula en el teléfono con las alertas guardadas y se vuelve a calcular cada vez que cambia la base; no hay consulta al servidor ni tabla aparte. Los períodos se cuentan por días del calendario: 7 días son hoy y los 6 anteriores, con una barra por día, y 30 días arranca 30 días atrás, con cuatro barras (las tres semanas que terminan hoy y una primera con lo que queda), como el Figma (52-2629 y 100-7024). Tocar una cifra (revisadas, sin revisar, la métrica de altas, un día, una semana, un tipo de ataque, reales, falsos positivos o un modelo) recalcula todo sobre las alertas de ese grupo; otro toque, el total de alertas o la cruz del chip vuelven a todas. El tiempo hasta la revisión y las altas revisadas en la primera hora usan la primera revisión con la hora del teléfono (decisión 21), y una alta cuenta para la métrica cuando ya pasó su primera hora o cuando se revisó dentro de ella. "Compartir resumen" arma una imagen como el cuadro 107-4862 (totales, métricas y gráfico, sin IP ni nombres de equipos), la guarda en la caché y abre la hoja del Figma (107-5511) con las apps instaladas entre WhatsApp, Telegram, Gmail y Drive, más Copiar.

**Por qué.** El informe pide que el Resumen funcione sin conexión (RNF01) y que la imagen lleve solo cantidades (RNF02). Calcular sobre la lista que ya está en la base alcanza para 30 días: son unos cientos de alertas y el cálculo tarda milisegundos (RNF07). Filtrar y volver a calcular hace que, con un día elegido, los demás días queden en cero, igual que los cuadros filtrados del Figma. Contar por días del calendario hace que cada barra sea un día entero y que la línea diga "del 21/09 al 27/09". La hoja propia es la del Figma y nombra justo las apps del RF07; con la hoja de Android no se puede saber a qué app se mandó la imagen ni mostrar el aviso de "Imagen copiada".

**Qué cambia.** `CalcularResumen` pasa a `ObservarResumen`, que observa la base, y se va la consulta de una sola vez que usaba. El dominio suma `PeriodoResumen`, `Tramo`, `GrupoTipo` (los cuatro grupos del Figma; Otros junta sin tipificar, probable falsa alarma y solo reglas o umbrales) y `GrupoResumen`. La frase de la precisión, que en el Figma es fija, sale del modelo con la precisión más alta y avisa que la comparación todavía no es confiable si ese modelo tiene menos de 30 revisiones. El FileProvider expone solo la carpeta de la caché donde se guarda la imagen. Mientras Estado sea provisoria, el Resumen se abre desde su barra (`BarraEstado`, la del Figma 1-3), que queda como pieza para la Estado definitiva. Para la demo y las capturas, `ZONDA_REVISAR_SEMILLA=1` deja revisadas tres de cada cuatro alertas de la semilla con más de dos horas.

## 25. Telegram: el bot dentro de la API y un aviso con lo mínimo

**Decisión.** El bot de Telegram corre dentro de la API, en dos tareas de asyncio, y habla con la API de bots por HTTP con httpx: `getUpdates` con long polling para recibir "/start <código>" y `sendMessage` para los avisos. Arranca solo si `TELEGRAM_BOT_TOKEN` está en `.env`. Para vincular, la app pide un código de un uso (10 minutos, `POST /telegram/vinculo`) y abre `t.me/<bot>?start=<código>`; la persona toca Iniciar y el bot asocia ese chat, solo si es privado, con el teléfono que pidió el código. La app no se entera sola: pregunta con `GET /telegram` al volver de Telegram y cada 3 segundos mientras espera. El aviso dice la severidad, el servicio y la hora, y de qué red viene ("Alerta ALTA · SSH · 02:09", "Tocá para ver el detalle en Zonda.", "Red: Oficina central"), con el botón "Ver el detalle en Zonda". Cómo avisa cada severidad lo elige el teléfono y el servidor lo guarda por teléfono: con sonido, con vibración o sin aviso. Sale un aviso por alerta y por chat, solo de las alertas creadas después del vínculo.

**Por qué.** httpx ya estaba en el backend y son dos llamadas, así que no hace falta `python-telegram-bot`, que era la idea del plan. Lo que viaja por Telegram queda guardado en sus servidores y se lee en la pantalla bloqueada: un nombre de equipo le dice a un extraño cuál es el servidor importante y que lo están atacando, y en una PyME muchos equipos llevan el nombre de una persona. Por eso el aviso dice solo lo que prometen el RNF02, las Condiciones (50-830) y Conectar Telegram (50-942); el cuadro del aviso (8-3) tenía "servidor de sueldos", "estación de RRHH" y la cantidad de conexiones, y cuando dos cuadros se contradicen decide el informe. La red sí va porque lo promete Redes (51-1583) y una consultora atiende varias. "Con vibración" es un mensaje sin sonido: un bot no puede pedir solo vibración. El código de un uso es el mismo mecanismo del pareo; en un grupo, los avisos los leería cualquiera del grupo.

**Qué cambia.** El backend suma `app/telegram.py`, las rutas `GET /telegram`, `DELETE /telegram/vinculo`, `POST /telegram/prueba` y `PUT /telegram/avisos`, los avisos en `POST /telegram/vinculo` (503 si el servidor no tiene el bot), `avisoTelegram` en la alerta para el historial del detalle y la tabla `avisos_telegram`; el token no va a los registros ni a los errores. La app pasa a la versión 4 de la base (la hora del aviso de cada alerta y el usuario del bot de cada red), suma Conectar Telegram después de la red conectada (50-942 y 50-1003) y en Ajustes (66-4205 y 66-3484), y la sincronización trae el estado de Telegram y le manda al servidor los avisos del teléfono si tiene otros. En debug, la app también habla sin cifrar con 127.0.0.1 para probar en un teléfono por USB con `adb reverse`.

## 26. HTTPS final: release sin http en ningún Android y la demo con un solo comando

**Decisión.** La app tiene su propia configuración de red (`res/xml/network_security_config.xml`, declarada en el manifiesto principal): nada sin cifrar, hacia ninguna dirección, y solo las autoridades de certificados del sistema. El build de debug reemplaza ese archivo por uno igual con una excepción, la API local (`10.0.2.2`, `127.0.0.1` y `localhost`). Una dirección http que Android no deja usar se informa como "No se pudo verificar el servidor", en el mismo cuadro del Figma (50-729) con su propio texto, y no sale ningún pedido. Del lado del servidor, Caddy acepta TLS 1.2 y 1.3, le pide el certificado solo a Let's Encrypt, no escucha en el puerto 80 y a quien entra por la IP le muestra el certificado del nombre. `demo.cmd` levanta todo con un solo comando: apunta el nombre de DuckDNS a la IP de la interfaz que tiene la ruta por defecto, arranca la API con el bot en un solo proceso y Caddy, y comprueba el certificado y el nombre. La dirección del QR sale de `ZONDA_DOMINIO`.

**Por qué.** Desde Android 9, una app nueva no puede usar http salvo que lo pida, pero la app soporta Android 8 (RNF11) y ahí Android deja pasar http si la app no tiene configuración de red: lo que decía la decisión 12 ("en release Android exige HTTPS") no valía en Android 8. Con el archivo propio vale lo mismo en todas las versiones, y `ConfiguracionDeRedTest` falla si alguien abre una excepción. Con solo las autoridades del sistema, un certificado que instale la persona (el de un proxy, por ejemplo) no alcanza para hacerse pasar por el servidor. Sin el puerto 80, un pedido sin cifrar no encuentra nada que lo atienda, ni siquiera una redirección: un cliente mal configurado no llega a mandar el token en claro. Mostrar el certificado del nombre a quien entra por la IP hace que el caso malo del RNF04 falle por la razón que explica la pantalla: el certificado no es válido para esa dirección y Android corta antes de mandar el código. Elegir la IP por la ruta por defecto evita apuntar el nombre a la red interna de VirtualBox o al hotspot de Windows, que en la notebook también tienen IP privada. Un solo comando evita olvidarse un paso el día de la defensa. Caddy no pide mail porque Let's Encrypt dejó de mandar avisos de vencimiento y Caddy renueva solo.

**Alternativa descartada.** `android:usesCleartextTraffic="false"` en el manifiesto: en Android 8 hace lo mismo, pero Android lo ignora cuando la app tiene configuración de red, y el debug la necesita para la API local; con un solo mecanismo queda más claro. Para Caddy, instalarlo como servicio de Windows: arrancaría solo, pero para la demo alcanza con el script y así se ve en pantalla lo que pasa.

**Qué cambia.** La decisión 12 ya no dice que Android exige HTTPS en release. `scripts/iniciar.ps1` queda para desarrollo (la API sola, con `--reload`) y la demo es `demo.cmd`. `scripts/probar_tls.ps1` prueba el HTTPS con `openssl s_client`. `CADDY_EMAIL` sale de `.env.ejemplo` y `ZONDA_URL_PUBLICA` hace falta solo para otra dirección.

## 27. Estado: un ViewModel con todo lo de la pantalla y los contadores que filtran en memoria

**Decisión.** La pantalla Estado (RF01) tiene un solo ViewModel que junta en un `StateFlow` el estado de la red activa (`ObtenerEstadoRed`: semáforo, contadores y hora de los datos), la lista de las alertas sin revisar, la primera descarga y la conexión. La lista se lee una sola vez con todas las sin revisar guardadas, sin cortar por período, que son las mismas que cuentan los contadores, y el contador elegido se aplica sobre esa lista en memoria: un toque deja solo esa severidad y otro toque muestra todas (criterio 4). Lo que se ve es uno de cinco estados: cargando (1-256), sin red (1-222), descargando (50-616), sin datos (1-222, semáforo gris y nunca verde, criterio 3) y con datos (1-3). "Última detección hace 5 min" es la hora de la sin revisar más reciente de la severidad que pone el color: las altas en rojo y las medias en amarillo. La sincronización automática, al abrir y al volver la conexión, no se nota. La que pide la persona, con el ícono o deslizando la lista, hace girar el ícono y al terminar muestra el aviso del Figma con la fecha y la hora (99-2, criterio 5); si la pide mientras corre una automática, no se lanza otra y esa pasa a ser la suya. Un error se avisa abajo con Reintentar, sin tapar lo que ya se ve (informe, 11.4), y la automática no avisa la falta de conexión porque ya está el banner.

**Por qué.** Con la lista en memoria, tocar un contador cambia la pantalla al instante y la última detección sale de la misma lista, sin otra consulta. Como todo sale de la base local, el estado aparece enseguida también sin conexión: con 500 alertas guardadas tarda unos 40 ms en la prueba con Room, y el criterio 1 pide menos de 1 segundo. Separar la sincronización automática de la pedida evita mostrar lo que el Figma no tiene: el círculo de deslizar o un aviso cada vez que se abre la app, o el ícono girando sobre la barra de la primera descarga (50-616). Contar la última detección por la severidad del color evita que una baja nueva haga parecer reciente un semáforo rojo.

**Alternativa descartada.** Una consulta a la base por cada contador elegido, como hace la lista de Alertas con sus filtros: andaría igual, pero cada toque esperaría a la base y la última detección necesitaría otra consulta.

**Qué cambia.** `EstadoViewModel` reemplaza a `DescargaInicialViewModel` y a la Estado provisoria de las decisiones 20 y 24. Las piezas del semáforo quedan como lo medido en el Figma: el contador elegido lleva un borde de 2 del color del punto por fuera de su contenido, así que crece 4 de ancho y de alto mientras los otros dos se achican (99-122), y la leyenda usa círculos de 14 con 10 entre filas (99-49). "Ver todas →" abre Alertas sin filtros, como el prototipo (de 1-3 a 2-3), y no aparece con la red tranquila y sin contador elegido (1-178). Las tarjetas abren el detalle. El aviso de sincronizado va en el medio de la pantalla y se va solo a los 2 segundos. Si los datos no son de hoy, el semáforo dice el día ("actualizado ayer 23:50"). Estado marca el semáforo y los contadores para los pasos 1 y 2 del recorrido (decisión 23). `ConectividadAndroid` da sin conexión apenas se pierde la red por defecto: antes volvía a preguntar la red activa, Android todavía devolvía la perdida y con el modo avión el banner no aparecía.

**Diferencias con el Figma.** Con la red ya pareada pero sin datos, el cuadro 1-222 dice "Necesitás el código de pareo" y ofrece Configurar conexión, que es para quien todavía no pareó: en la app ese texto queda para el caso sin red, y con la red pareada el bloque dice que todavía no llegaron las alertas y ofrece "Sincronizar ahora". En ese cuadro el ícono del Resumen quedó al lado del logo; en la app va a la derecha, como en los demás. El criterio 2 dice "actualizado hace X", y el Figma (1-87) muestra la hora de los datos en el banner y "Último estado conocido" en el semáforo: se siguió el Figma. Los textos que el Figma no tiene (la lista vacía, las notas de Alta y Media vacías, sin datos con la red pareada y las alertas nuevas en el aviso) quedan para que Meli los revise.
