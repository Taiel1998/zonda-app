# Guion de la demo

La demo dura unos 12 minutos y sigue el orden que pide la consigna (sección 7.5): problema, escenario, acción del usuario, respuesta de la aplicación y valor generado. Se hace con un teléfono Android real y la API corriendo en la notebook del equipo, publicada con HTTPS. Cada paso dice qué se muestra, qué se dice y cuánto tiempo lleva. Los textos entre comillas son los que aparecen en la pantalla.

Lo recorrimos entero el 14/10/2026 en el emulador, con el APK de release firmado y la API publicada con `demo.cmd`. En el emulador Telegram no tiene sesión, así que el aviso se probó con un Telegram simulado; en el teléfono real se usa el de verdad.

## 1. Qué hace falta

- La notebook con el repositorio, `backend\.env` completo (`ZONDA_DOMINIO`, `DUCKDNS_TOKEN` y `TELEGRAM_BOT_TOKEN`) y `backend\caddy\caddy.exe`.
- Un teléfono con Android 8.0 o más nuevo, con el APK de release instalado (`zonda-1.0.2.apk`, firmado con la clave del equipo), Telegram con la sesión iniciada, datos móviles y el hotspot.
- Un cable USB de datos y la depuración USB activada, para mostrar la pantalla del teléfono en el proyector con Android Studio (ventana Running Devices).
- Nada fuera del repositorio: la alerta alta del paso 7 la larga `.\zonda.cmd emitir` y la red tranquila del paso 11 la deja `.\zonda.cmd tranquila`, que hace de otra persona que revisa desde su teléfono. Los dos son comandos del backend.

## 2. El día anterior

1. Ensayo completo con el teléfono de la demo y la notebook conectada a su hotspot. Es la prueba que todavía falta en un teléfono real: que la red del hotspot resuelva el nombre de DuckDNS (riesgo 2), el aviso de Telegram y el sin conexión con los datos móviles.
2. En `backend\.env`: `ZONDA_REVISAR_SEMILLA=1`, para que el Resumen tenga revisiones, y `ZONDA_EMITIR_CADA=0`, para que las alertas nuevas aparezcan cuando las larguemos nosotros con `.\zonda.cmd emitir`.
3. Instalar el APK firmado. Si el teléfono tenía la versión de debug, desinstalarla antes: tiene otra firma y Android no instala una encima de la otra.
4. Con el teléfono por USB, `adb shell pm get-app-links ar.edu.uade.zonda` tiene que decir `verified` para `taiel1998.github.io` (en Android 8, `adb shell dumpsys package d` dice `always`). Si no, el botón del aviso abre el navegador.

## 3. Media hora antes

1. La notebook se conecta al hotspot del teléfono, y no al revés: con el hotspot de Windows prendido, el nombre apuntaría a otra IP.
2. Borrar `backend\zonda.db`: la API vuelve a cargar la semilla con las fechas de hoy y sin teléfonos pareados.
3. En `backend`, `.\demo.cmd`. Tiene que terminar con "Listo: https://…/salud responde con HTTPS." y "El nombre apunta a esta notebook".
4. En otra terminal, también en `backend`: `powershell -ExecutionPolicy Bypass -File scripts\probar_tls.ps1`. Tienen que salir las siete en PASA.
5. En el teléfono, Ajustes de Android → Apps → Zonda → Almacenamiento → Borrar datos, para mostrar el primer inicio desde cero.
6. En el teléfono: no molestar apagado, volumen alto, las notificaciones de Telegram activas y batería cargada.
7. En el proyector, tres ventanas a mano: la de `demo.cmd`, la terminal en `backend` y el espejo del teléfono.

## 4. La demo, paso a paso

### Paso 1. El problema (1 minuto)

**Muestra.** Nada en la pantalla, o la portada de la presentación.

**Dice.** "En una PyME, el tráfico interno anómalo se detecta, pero nadie lo atiende a tiempo: la alerta queda en un correo o en una consola que el responsable de IT mira cuando está en su escritorio. En 2025, entre que un atacante entra y salta al primer servidor pasaron 29 minutos en promedio. Y en nuestra encuesta, el 64 % de las PyMEs no tiene a nadie a cargo de la seguridad. Zonda lleva esa alerta al teléfono, deja decidir en segundos y funciona aunque no haya señal."

### Paso 2. El escenario (30 segundos)

**Dice.** "Martín es el administrador de IT de una PyME. Domingo a la madrugada, el sistema detecta un acceso por SSH desde una PC de recursos humanos al servidor de sueldos. Vamos a hacer lo que hace él: configurar la app la primera vez, recibir el aviso, revisar la alerta, también sin señal, y ver el resumen que le pasa al dueño."

### Paso 3. La API y el HTTPS (1 minuto)

**Muestra.** La ventana de `demo.cmd`.

**Dice.** "Esta notebook hace de plataforma de detección: una API en FastAPI con alertas de muestra armadas con el dataset público CICIDS2017. Caddy la publica con HTTPS y un certificado de Let's Encrypt para un nombre de DuckDNS que apunta a la IP de la notebook en esta red. Nada queda abierto a internet: Let's Encrypt comprueba que el nombre es nuestro por DNS. Un solo comando levanta todo y comprueba el certificado."

### Paso 4. Primer inicio y pareo con el QR (2 minutos)

**Muestra.**

1. "Bienvenido a Zonda": tildar "Leí y acepto las condiciones de uso y privacidad" y tocar "Empezar".
2. El tutorial de tres pasos ("Mirá el estado de la red de un vistazo", "Los avisos llegan a tu teléfono", "Sin señal, seguís trabajando"), con "Siguiente" y al final "Configurar mis avisos".
3. "Antes de empezar": cómo avisa cada severidad por Telegram (alta con sonido, media con vibración, baja sin aviso) y "Permitir notificaciones". Desde Android 13, Android pregunta y se acepta; en las versiones anteriores ya están permitidas. "Continuar".
4. La severidad de cada tipo de ataque, con los valores recomendados ya elegidos: "Guardar y conectar con la red".
5. "Conectar con la red": tocar "Escanear código QR". En la terminal de la notebook, `.\zonda.cmd pair`: muestra el código, la dirección y el QR, y lo guarda en `pareo_<código>.png`. Abrir ese PNG en pantalla completa y escanearlo con el teléfono.
6. "Conexión segura" y "28 alertas de los últimos 30 días listas para descargar" (la cantidad depende de la semilla). Tocar "Detalles técnicos": "HTTPS · TLS 1.3" (TLS 1.2 en Android 8 y 9), "Let's Encrypt" y el vencimiento del acceso. Volver y "Continuar".

**Dice.** "El código es de un solo uso y vence a los 10 minutos. Antes de mandarlo, la app verifica el servidor: si la dirección no es HTTPS o el certificado no es válido para ese nombre, no manda nada. El servidor le da a este teléfono un token propio, que vence a los 90 días, se puede revocar y queda cifrado con el Keystore de Android."

### Paso 5. Telegram (45 segundos)

**Muestra.** "Recibí los avisos por Telegram": tocar "Abrir Telegram". Se abre el chat del bot: tocar "Iniciar" y volver a Zonda, que muestra "Telegram conectado". "Continuar".

**Dice.** "Por Telegram viajan solo la severidad, el servicio y la hora. Las direcciones IP y los nombres de los equipos se ven solo en la app, por el canal cifrado."

### Paso 6. El recorrido y la pantalla de Estado (1 minuto y medio)

**Muestra.**

1. El recorrido de once pasos arranca solo. Los pasos 1 a 3 con calma: el semáforo ("El estado de la red, de un vistazo"), los contadores ("Cuántas faltan revisar") y sincronizar. El resto con "Siguiente" hasta "Listo" (termina en Ajustes) y volver a Estado.
2. El semáforo en rojo con "ATENCIÓN" y las altas sin revisar. Tocar el contador de Alta: la lista muestra solo las altas. Tocarlo de nuevo: vuelven todas.

**Dice.** "Todo lo que se ve sale de la base local, una base Room cifrada con SQLCipher: por eso aparece al instante, también sin conexión. Rojo si hay altas sin revisar, amarillo si solo quedan medias, verde si no queda ninguna de las dos. Las bajas no cambian el color: esperan en la lista."

### Paso 7. El aviso abre el detalle (1 minuto y medio)

**Muestra.**

1. En la terminal de la notebook, en `backend`: `.\zonda.cmd emitir`. Responde "Alerta nueva …: fuerza_bruta por SSH, severidad alta." y a los 5 segundos suena el teléfono: "Alerta ALTA · SSH · " con la hora, "Tocá para ver el detalle en Zonda." y "Red: Oficina central".
2. Tocar "Ver el detalle en Zonda": se abre la app directo en el detalle, sin pasar por el navegador.
3. El detalle: con la semilla de ejemplo es "pc-rrhh → srv-sueldos", "SSH · TCP/22", 58 conexiones en 60 segundos y el veredicto de cada método: "Modelo de IA" y "Umbrales estadísticos" dicen "ANÓMALO" y "Reglas estáticas" dice "NORMAL".

**Dice.** "El botón abre un enlace https que Android verificó como de nuestra app: el dominio publica la huella del certificado con el que firmamos el APK. Los tres métodos miran cosas distintas: el modelo de IA compara el error de reconstrucción con su umbral, los umbrales cuentan cuántas conexiones hubo al mismo servicio en 60 segundos y las reglas miran si ese destino y ese puerto están en el catálogo autorizado. Acá las reglas dicen normal porque SSH a ese servidor está permitido, pero los otros dos marcan que la cantidad de intentos no es normal."

### Paso 8. Revisar (1 minuto)

**Muestra.** "Revisar", "Ataque real", el motivo "Contraseña cambiada" (la app propone un comentario que se puede cambiar) y "Confirmar revisión". Abajo aparece "Revisión guardada y enviada al servidor." con "Deshacer". Volver a Estado con la flecha de arriba: el contador de Alta bajó. (Para repetirlo, en el ensayo o si algo sale mal, largar otra alerta con `.\zonda.cmd emitir` y tocar su aviso: el botón de un aviso que ya se abrió puede traer Zonda como estaba, en Estado, sin abrir el detalle.)

**Dice.** "Dos pasos para no marcar una alerta sin querer, con el motivo y el comentario obligatorios. Durante 5 segundos se puede deshacer. La revisión queda en el historial con el teléfono que la hizo y la hora. Si otra persona la revisó antes, la app muestra las dos y pregunta con cuál quedarse."

### Paso 9. Sin conexión (1 minuto y medio)

**Muestra.**

1. Apagar los datos móviles del teléfono desde el panel rápido. **No el modo avión:** apaga el hotspot y la notebook se queda sin red.
2. Estado muestra "Sin conexión · mostrando datos de las …" y "Último estado conocido · puede haber alertas nuevas".
3. Abrir otra alerta y revisarla. Aparece "Revisión guardada. Se enviará cuando haya conexión.", el detalle dice "Sin conexión · tu revisión se enviará al reconectar" y la revisión, "pendiente de envío".
4. Prender los datos móviles: la app sincroniza sola y la revisión pasa a "enviada".

**Dice.** "La revisión quedó en una cola dentro de la base del teléfono, con un identificador propio. Cuando vuelve la red se manda sola, y si por un corte se manda dos veces, el servidor la reconoce y no la aplica dos veces. Lo mismo hace la sincronización en segundo plano cada 15 minutos, con WorkManager."

### Paso 10. El Resumen (1 minuto)

**Muestra.** El ícono de barras arriba en Estado. "7 días" y "30 días". Tocar un tipo de ataque: todas las cifras se recalculan sobre ese grupo, y otro toque vuelve a todo. "Compartir resumen" abre la hoja con las apps instaladas (WhatsApp, Telegram, Gmail, Drive) y "Copiar".

**Dice.** "Esto es para el dueño de la empresa, que no abre Zonda: cuántas alertas hubo, cuántas se revisaron y las dos métricas que propusimos en el informe, la mediana hasta la primera revisión y el porcentaje de altas revisadas en la primera hora. Se calcula en el teléfono con lo guardado, así que también anda sin conexión. La imagen que se comparte no tiene IP ni nombres de equipos."

### Paso 11. La red tranquila (45 segundos)

**Muestra.** En la terminal, en `backend`: `.\zonda.cmd tranquila`. Responde "Teléfono de Laura (…) revisó como falso positivo …" con cuántas altas y medias. En el teléfono, el ícono de sincronizar: aparece el aviso "Sincronizado" con la fecha y la hora, y el semáforo pasa a verde con "La red está tranquila". Si sobra tiempo, en Alertas con el estado de revisión en "Falsos positivos", abrir una de esas: en la revisión, "Quién" dice "Teléfono de Laura".

**Dice.** "Hicimos de Laura, otra persona del equipo, que revisó las altas y medias desde su teléfono. Al sincronizar, este teléfono se entera y el semáforo pasa a verde."

### Paso 12. El valor generado (45 segundos)

**Dice.** "Martín se enteró en segundos, decidió con el contexto de los tres detectores y aunque no tenía señal la revisión llegó sola. Cada decisión quedó registrada con su motivo, y el dueño tiene los números para saber si la inversión en seguridad sirve. Todo con herramientas gratuitas y una computadora que la empresa ya tiene. La app tiene más de 350 pruebas automáticas, el backend más de 50, y el CI las corre en cada pull request."

## 5. Versión corta (5 minutos)

Si hay menos tiempo: paso 1, en el paso 4 "Ya conozco Zonda: omitir la introducción" y directo al QR, pasos 5, 7, 8 y 9, y el paso 12. El Resumen se muestra solo si sobra un minuto.

## 6. Si algo falla

| Qué pasa | Qué hacemos |
|---|---|
| `demo.cmd` avisa que la red no resuelve el nombre (riesgo 2) | Revisar que la notebook esté en el hotspot del teléfono. En Android 9 o más nuevo, en el teléfono: Ajustes → Red → DNS privado → `dns.google`, que sí devuelve la IP privada |
| Caddy no responde con el certificado | Sin internet en la notebook, Caddy usa el certificado que guardó en el ensayo (vence el 12/01/2027). Si no hay ninguno, hace falta internet unos segundos para sacarlo |
| El QR no se lee (reflejo del proyector) | "o ingresalo a mano": la dirección y el código que muestra `zonda pair` |
| La app dice "No se pudo verificar el servidor" | Se escribió la IP en lugar del nombre, o el certificado no está: revisar la ventana de `demo.cmd` |
| El aviso de Telegram no llega | Abrir la alerta desde la lista de Estado: es el mismo detalle. Contar que el aviso depende de internet y que la app igual la trae al sincronizar |
| El botón del aviso abre la página en el navegador de Telegram | La página tiene "Abrir en Zonda", que abre la alerta en la app |
| El botón del aviso trae Zonda en Estado, sin abrir el detalle | Pasa con un aviso que ya se abrió antes. Largar otra alerta con `.\zonda.cmd emitir` y tocar su aviso, o abrir la alerta desde la lista de Estado |
| La API se cerró | `.\demo.cmd` de nuevo; mientras tanto la app sigue mostrando lo guardado |
| No anda el espejo del teléfono | Mostrar el teléfono a la cámara o pasarlo |

## 7. Para las preguntas: dónde está cada cosa

La consigna dice que en la defensa pueden preguntar a cada integrante por el código, la arquitectura, el estado, la navegación, la persistencia, la red, la sincronización, los sensores, las decisiones de UX y la organización del trabajo. Todos los archivos de la app están en `app/src/main/java/ar/edu/uade/zonda/`.

| Tema | Dónde está | Decisión |
|---|---|---|
| Capas y flujo de datos | `ui` → `domain` → `data`; el diagrama está en el README | 14 |
| Estado de una pantalla | `ui/estado/EstadoViewModel.kt` y `EstadoUiState.kt`: un `StateFlow` por pantalla | 14 y 27 |
| Navegación y enlace del aviso | `ui/navigation/ZondaNavHost.kt`, `Rutas.kt` y `ui/detalle/DetalleNavegacion.kt` | 9 y 16 |
| Persistencia | `data/local/ZondaDatabase.kt` (Room) y los DAO, con SQLCipher y la clave guardada con el Keystore (`data/seguridad`) | 8 |
| Red | `data/remote/ZondaApi.kt` (Retrofit), `InterceptorDeToken.kt`, `ErroresHttp.kt` y `res/xml/network_security_config.xml` | 12 y 26 |
| Sincronización | `data/sync/SincronizadorHttp.kt` (cola, cursor, catálogo y purga), `sync/SincronizacionWorker.kt` y `sync/ConectividadAndroid.kt` | 13 y 17 |
| Revisión y conflicto | `domain/usecase/RevisarAlerta.kt`, `ResolverEnvioRevision.kt` y `ui/detalle/HojaRevision.kt` | 15 y 21 |
| Capacidades del teléfono | Cámara: `ui/conexion/EscanerQr.kt` (CameraX y ML Kit). Notificaciones: `notificaciones/Notificador.kt`. Compartir: `ui/resumen/CompartirResumen.kt` | 16, 17 y 24 |
| Inyección de dependencias | `di/AppContainer.kt`, a mano y sin Hilt | 2 |
| Backend, bot y HTTPS | `backend/app/rutas.py`, `backend/app/telegram.py`, `backend/Caddyfile` y `backend/scripts/demo.ps1` | 25 y 26 |
| Firma del APK | `app/build.gradle.kts` | 28 |
| Pruebas | `app/src/test` (JUnit 4, Turbine, Robolectric y MockWebServer) y `backend/tests` (pytest) | 6 |
| Organización del trabajo | Una rama y un pull request por fase, con las seis preguntas de la consigna; el uso de IA está en `docs/prompts` | |

## 8. Al terminar

Ctrl+C en la ventana de `demo.cmd`: cierra la API y Caddy. Si Windows pregunta "¿Desea terminar el trabajo por lotes (S/N)?", contestar S.
