# API de alertas de Zonda

API mínima en FastAPI con SQLite que hace de simulador de la plataforma de detección: sirve alertas de muestra armadas a partir del dataset público CICIDS2017 y recibe las revisiones que hace la app. Corre en la notebook del equipo, publicada con HTTPS por Caddy. El contrato es el definitivo: después se conecta a la plataforma real sin tocar la app.

## Cómo ejecutarla

Requiere Python 3.12.

```powershell
cd backend
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Probar: http://127.0.0.1:8000/salud y la documentación automática en http://127.0.0.1:8000/docs. Desde el emulador de Android la API se alcanza en `http://10.0.2.2:8000`. Lo mismo hace `scripts\iniciar.ps1`. Así, sin HTTPS, la usa solo el build de debug; para la demo con HTTPS está `demo.cmd` (ver "Publicación con HTTPS").

Tests:

```powershell
.venv\Scripts\python -m pytest
```

Configuración: copiar `.env.ejemplo` a `.env` (no se commitea). Variables que lee la API:

| Variable | Para qué | Por defecto |
|---|---|---|
| `ZONDA_RED_ID`, `ZONDA_RED_NOMBRE` | Identidad de la red que sirve este servidor (una API por red) | `red-oficina`, `Oficina central` |
| `ZONDA_URL_PUBLICA` | Dirección que va en el QR de pareo. Hace falta solo para una dirección distinta de la de `ZONDA_DOMINIO` | `https://<ZONDA_DOMINIO>`, o `http://127.0.0.1:8000` sin nombre |
| `ZONDA_DOMINIO` | Nombre de la API en DuckDNS, por ejemplo `zonda-equipo.duckdns.org`. Lo usan Caddy, los scripts y la dirección del QR | vacío |
| `DUCKDNS_TOKEN` | Token de la cuenta de DuckDNS, para apuntar el nombre a la notebook y para el desafío de Let's Encrypt | vacío |
| `ZONDA_DB_PATH` | Archivo SQLite | `backend/zonda.db` |
| `ZONDA_SEMILLA` | JSON con las alertas de muestra | `backend/alertas_semilla.json` |
| `ZONDA_EMITIR_CADA` | Segundos entre alertas nuevas que emite el servidor durante la demo (0 apaga). Para una alerta de un tipo en el momento justo, `zonda emitir` | `0` |
| `ZONDA_MULTIPLICAR` | Copias de la semilla, solo para probar la lista con 1.000 alertas | `1` |
| `ZONDA_REVISAR_SEMILLA` | Con `1`, la semilla arranca con tres de cada cuatro alertas de más de dos horas ya revisadas, para la demo y las capturas del Resumen; vale al cargar la semilla en una base nueva | `0` |
| `ZONDA_TAMANIO_PAGINA` | Alertas por página de `GET /alertas`; con un número chico se ve la barra de la primera descarga | `200` |
| `TELEGRAM_BOT_TOKEN` | Token del bot de Telegram (lo da BotFather). Sin token la API anda igual, sin bot | vacío |
| `ZONDA_TELEGRAM_BOT` | Usuario del bot, por si Telegram no responde al arrancar; si responde, vale el que dice Telegram | `zonda_alertas_bot` |
| `ZONDA_ENLACE_AVISO` | Comienzo del enlace de cada aviso, el App Link que abre la alerta en la app | `https://taiel1998.github.io/a/` |
| `ZONDA_TELEGRAM_API` | Dirección de la API de bots. Solo para probar con un Telegram simulado | `https://api.telegram.org` |
| `ZONDA_INGESTA_TOKEN` | Token con el que la plataforma de detección manda episodios a `POST /ingesta/alertas` (cabecera `X-Token-Ingesta`); sin él la ruta responde `503`. Es una contraseña: no va al repositorio | vacío |
| `ZONDA_LATIDO_LIMITE` | Segundos sin latido del análisis en vivo para darlo por detenido (tarjeta Estado del sistema y aviso de Telegram) | `120` |
| `ZONDA_LATIDO_REVISAR_CADA` | Cada cuántos segundos el vigía revisa la edad del latido | `15` |
| `ZONDA_PERDIDA_LIMITE` | Porcentaje de pérdida de captura de Zeek desde el que el sensor ve parcialmente (tarjeta en amarillo y aviso de Telegram) | `1.0` |
| `ZONDA_FIREWALL_TOKEN` | Token con el que el agente del firewall lee los bloqueos aprobados (cabecera `X-Token-Firewall`). Sin él, los bloqueos están apagados y la red queda en modo de observación. Es una contraseña: no va al repositorio | vacío |
| `ZONDA_FIREWALL_LIMITE` | Segundos sin que el firewall pregunte para darlo por desconectado | `60` |
| `ZONDA_MAX_BLOQUEOS`, `ZONDA_BLOQUEOS_POR_HORA` | Topes de los bloqueos: activos a la vez en la red y pedidos por dispositivo en una hora | `20`, `10` |
| `ZONDA_ORIGENES_LLAVE` | Direcciones del tablero, además de `ZONDA_URL_PUBLICA`, desde las que valen las llaves de acceso (decisión 51), separadas por comas; por ejemplo `http://localhost:8001` para probar. Una llave se ata al nombre: por la IP no funciona | vacío |

## Datos de muestra y fechas corridas

Al arrancar con la base vacía, la API carga `alertas_semilla.json` y **corre todas las fechas** para que el episodio más reciente quede una hora antes del momento del arranque: los datos originales son de julio de 2017 y, sin correrlos, los períodos de 24 horas, 7 y 30 días de la app no tendrían nada. Los identificadores no cambian.

**Qué semilla está cargada hoy:** la exportación real de la plataforma de detección sobre CICIDS2017 (516 episodios con 82.878 flujos: 2 de fuerza bruta, 1 escaneo, 1 DDoS, 227 sin tipo marcadas solo por reglas o umbrales y 285 probables falsas alarmas; por severidad, 2 altas, 229 medias y 285 bajas). Se generó con `herramientas/semilla_real/exportar_semilla_real.py` en zeek-lab y se verificó contra los resultados documentados de la plataforma de detección (evidencia en `herramientas/semilla_real/evidencias_semilla_real.txt`). La semilla de ejemplo de 28 episodios (la genera `herramientas/generar_semilla_de_ejemplo.py`) quedó como fixture de los tests en `tests/semilla_de_prueba.json`. Para volver a cargar una semilla nueva, borrar `zonda.db`.

## Comandos de administración

No hay interfaz web de administración; se usan comandos en la computadora de la API:

```powershell
.\zonda.cmd pair              # código de pareo de administrador, de un uso (vence a los 10 minutos), con el QR en pantalla y en PNG
.\zonda.cmd pair --nombre Taiel  # lo mismo, con el nombre de la persona que lo va a usar
.\zonda.cmd dispositivos      # teléfonos pareados, con su persona, su perfil, estado y vencimiento del token
.\zonda.cmd revoke d-xxxxxxxx # revoca el token de un teléfono (la app pide un pareo nuevo)
.\zonda.cmd emitir            # crea ya una alerta alta de fuerza bruta por SSH, para la demo
.\zonda.cmd emitir escaneo    # una de otro tipo; con el servicio: .\zonda.cmd emitir fuerza_bruta ftp
.\zonda.cmd tranquila         # otra persona revisa desde su teléfono las altas y medias sin revisar, para la demo
```

Los códigos de `zonda pair` son de administrador: el primero sale de la terminal y los demás los suma un administrador desde la app (Ajustes → Personas → Agregar persona, que pide un código a `POST /personas/codigo` y muestra el mismo QR en su pantalla; decisión 39). `zonda revoke` quita a cualquiera, también al último administrador, y en ese caso avisa que hay que generar otro código con `zonda pair`.

El QR contiene `zonda:<ZONDA_URL_PUBLICA>#<código>;vence=<fecha ISO>`, por ejemplo `zonda:https://zonda-empresa.duckdns.org#K7M2-QX9P;vence=2026-10-12T02:19:00+00:00`. En Linux o macOS: `python -m app.cli pair`.

`zonda emitir [tipo] [servicio]` crea una alerta nueva en el momento: copia una existente de ese tipo con la hora de ahora, con la misma función que el emisor de `ZONDA_EMITIR_CADA`, pero sin azar. El emisor copia cualquiera y, con la semilla real, casi ninguna sale alta (solo 2 de las 516); en la demo hace falta una alta cuando la pedimos (paso 7 del guion). Si hay un teléfono con Telegram vinculado, el aviso sale en unos 5 segundos, y la app la trae en la sincronización siguiente. Los tipos son los de la tabla de severidades: `fuerza_bruta`, `escaneo`, `ddos`, `probable_falsa_alarma`, `sin_tipificar`, `servicio_no_autorizado` (las que la IA no vio y marcaron las reglas) y `volumen_inusual` (las que marcaron los umbrales). Sin argumentos es fuerza bruta por SSH; con un tipo y sin servicio, cualquiera de ese tipo. Si no hay ninguna alerta para copiar, lo dice y termina con error. Como los otros comandos, escribe directo en la base de la API (`ZONDA_DB_PATH` o `backend/zonda.db`); para recibir alertas desde la plataforma de detección está `POST /ingesta/alertas` (prueba de punta a punta).

`zonda tranquila [--nombre "Teléfono de Laura"]` hace de otra persona del equipo que revisa desde su teléfono todas las altas y medias sin revisar, como falso positivo con el motivo tarea programada. Es el final de la demo (paso 11 del guion): al sincronizar, el teléfono de la demo las trae revisadas y Estado pasa a verde. Las bajas no se tocan, porque no cambian el color. La revisión se guarda con la misma función que `POST /alertas/{id}/revision` (`app/revisiones.py`): queda en el historial con el nombre de ese teléfono, sube la versión y cambia la secuencia del cursor. Si después alguien manda una revisión de lo que vio antes, la API responde el `409` de siempre, con la revisión de la otra persona. El teléfono se parea solo la primera vez, como analista (decisión 39), con un código de un uso que se canjea en el momento, y después se vuelve a usar: aparece en `zonda dispositivos` y se puede revocar como cualquier otro. Si no queda ninguna alta ni media sin revisar, lo dice y no cambia nada. Escribe en la misma base que la API, así que anda con la API levantada o sin ella.

## Contrato

Convenciones: campos en camelCase; valores de enumeraciones en minúsculas con guion bajo; fechas en ISO 8601 con zona (UTC). Autenticación con `Authorization: Bearer <token>`; sin token o con token vencido o revocado la API responde `401` y la app pide un pareo nuevo sin borrar su caché.

| Ruta | Recibe → devuelve | Errores |
|---|---|---|
| `GET /salud` | nada → `version`, `hora`, `alertas` (cantidad), `red`; con token, también `tokenVence`, `sistema` (cómo están el sensor, el análisis en vivo y el servidor) y `firewall` (si está habilitado y conectado, su última consulta y su lista de «nunca bloquear»; decisión 51); sin token, `null` | 401 si el token venció |
| `GET /estado` | token → `sinRevisar {alta, media, baja}`, `ultimoFlujoProcesado`, `horaServidor` | 401 |
| `GET /alertas?desde=&severidad=&servicio=&destino=&metodo=&estado=` | cursor → `alertas` nuevas o modificadas, `cursorSiguiente`, `hayMas`; sin cursor trae solo los últimos 30 días y suma `total` | 401, 410 cursor de otra instalación |
| `GET /alertas/{id}` | id → la alerta con sus veredictos e `historial` | 401, 404 |
| `POST /alertas/{id}/revision` | `{accionId, resultado, motivo, comentario, versionVista, forzar, revisadaEn}` → la alerta actualizada | 401, 404, 409 con `revisionAjena` y la alerta, 422 motivo que no corresponde o comentario vacío |
| `GET /severidades` | token → `{tipo: severidad}`, con los tipos de `tipoAtaque` más `servicio_no_autorizado` y `volumen_inusual` | 401 |
| `PUT /severidades` | `{severidades: {tipo: severidad}, motivo}` → la tabla completa. Solo un administrador y con motivo: cada tipo que cambia queda en el registro de cambios; marca todas las alertas como modificadas | 401, 403 analista, 422 tipo desconocido o sin motivo |
| `POST /pareo` | `{codigo, nombreDispositivo, tipo}` → `{token, red, emitido, vence, dispositivoId, alertas30Dias, perfil, persona}`; el perfil y el nombre son los del código. `tipo` es `telefono` o `navegador` (el tablero web); sin él, un nombre que empieza con «Tablero web» es un navegador | 400 inválido, 410 vencido o usado |
| `GET /personas` | token → la gente de la red: `[{id, nombre, telefono, perfil, ultimoUso, vos, tipo, puedeBloquear}]`; `tipo` es `telefono` o `navegador` (el tablero web) y `puedeBloquear` dice si bloquea en el acto: un administrador siempre, un analista si un administrador le dio permiso (decisión 51) | 401 |
| `POST /personas/codigo` | `{nombre, perfil}` → `{codigo, vence, contenido, nombre, perfil}`: un código de un uso para esa persona y lo que lleva su QR; desde un navegador, con la llave (`X-Llave`, decisión 51) | 401, 403 analista o navegador sin llave, 422 sin nombre |
| `PUT /personas/{id}/perfil` | `{perfil}` → la persona; el permiso de bloquear vuelve a cero; desde un navegador, con la llave | 401, 403 analista o navegador sin llave, 404, 409 si deja al equipo sin administrador |
| `PUT /personas/{id}/bloqueos` | `{puedeBloquear}` → la persona: un analista bloquea en el acto (el de guardia) o pide aprobación; queda en el registro como `permiso_bloqueo`; desde un navegador, con la llave (decisión 51) | 401, 403 analista o navegador sin llave, 404, 409 si es administrador |
| `DELETE /personas/{id}` | token → 204; revoca el token de ese teléfono; desde un navegador, con la llave | 401, 403 analista o navegador sin llave, 404, 409 si deja gente sin administrador |
| `GET /equipos` | token → `[{ip, nombre, importancia}]`, los equipos con nombre en el catálogo | 401 |
| `PUT /equipos/{ip}` | `{importancia, motivo}` → el equipo; queda en el registro y sus alertas vuelven a bajar con la severidad nueva | 401, 403 analista, 404 fuera del catálogo, 422 sin motivo |
| `GET /registro?bloqueos=` | token → `[{id, fecha, tipo, autorId, autor, objeto, nombre, antes, despues, motivo}]`, de lo más nuevo a lo más viejo; con `bloqueos=true`, también los bloqueos y desbloqueos del firewall y los cambios del permiso de bloquear | 401 |
| `PUT /alertas/{id}/asignacion` | `{accionId, responsableId}` (null deja la alerta sin asignar) → la alerta; idempotente por `accionId` | 401, 403 lo que el perfil no permite, 404, 409 un analista se asigna una que ya tiene otra persona, 422 persona fuera del equipo |
| `GET /telegram` | token → `{vinculado, bot, vinculadoEn, avisos}` de este teléfono | 401 |
| `POST /telegram/vinculo` | `{avisos}` opcional → `{codigo, enlaceBot, vence}`; guarda los avisos | 401, 503 sin bot |
| `DELETE /telegram/vinculo` | token → 204; el chat deja de recibir los avisos y el bot se despide | 401 |
| `POST /telegram/prueba` | token → 204; el bot manda un aviso de prueba al chat | 401, 409 sin Telegram vinculado, 502 Telegram no respondió, 503 sin bot |
| `PUT /telegram/avisos` | `{alta, media, baja}` con `sonido`, `vibracion` o `sin_aviso` → lo guardado | 401, 422 valor desconocido |
| `DELETE /dispositivos/actual` | token → 204 | 401 |
| `GET /catalogo` | token → `{equipos {ip: nombre}, servicios []}` | 401 |
| `POST /ingesta/alertas` | `{alertas: [episodios con el formato de la semilla], catalogo?}`, con la cabecera `X-Token-Ingesta` (no el token de los teléfonos) → `{recibidas, creadas, actualizadas}`; crea o actualiza cada episodio por id. La usa la plataforma de detección (prueba de punta a punta) | 401 token inválido, 422 formato, 503 sin token configurado |
| `POST /ingesta/latido` | `{hora, flujos, ultimoFlujo, zeekActivo, episodios, intervalo, perdidaCaptura, perdidaCapturaEn, acksCaptura, perdidaSimulada}`, con la misma cabecera `X-Token-Ingesta` → `{recibidoEn}`; el análisis en vivo lo manda al final de cada ciclo, aunque no haya episodios, y se guarda el último | 401 token inválido, 422 formato, 503 sin token configurado |
| `GET /bloqueos?alerta=` | token → `{bloqueos, firewall {habilitado, conectado, ultimaConsulta, nuncaBloquear}}`, los bloqueos de lo más nuevo a lo más viejo | 401 |
| `POST /bloqueos` | `{accionId, alertaId, duracion, motivo}` → el bloqueo (201): `aprobado` si lo hace alguien que bloquea (un administrador o un analista con permiso) desde su teléfono o con la llave de su computadora, `pedido` si lo hace un analista que pide aprobación o un tablero sin llave; idempotente por `accionId` | 401, 403 llave que no confirma este bloqueo, 404 sin la alerta, 409 equipo ya bloqueado o tope, 422 sin motivo, duración no permitida, origen que no es un equipo o en «nunca bloquear», 429 demasiados en una hora, 503 sin firewall |
| `POST /bloqueos/{id}/aprobacion` | token de alguien que bloquea, en su teléfono o con la llave de su computadora → el bloqueo aprobado; la duración corre desde ahora | 401, 403 a quien pide aprobación o al tablero sin llave, 404, 409 ya no está pendiente, 422 en «nunca bloquear», 503 |
| `POST /bloqueos/{id}/rechazo` | `{motivo}` → el pedido rechazado | 401, 403 a quien pide aprobación, 404, 409 ya no está pendiente, 422 sin motivo, 503 |
| `POST /bloqueos/{id}/desbloqueo` | `{motivo}` → el bloqueo desbloqueado; si ya terminó, sin cambios; desde un navegador, con la llave | 401, 403 a quien pide aprobación o al tablero sin llave, 404, 409 si es un pedido, 422 sin motivo, 503 |
| `PUT /bloqueos/{id}/duracion` | `{duracion, motivo}` → el bloqueo con el vencimiento nuevo, desde ahora; `null` lo deja hasta desbloquear; como bloquear, desde el teléfono o con la llave de alguien que bloquea | 401, 403 a quien pide aprobación o al tablero sin llave, 404, 409 no vigente, 422 sin motivo o duración no permitida, 503 |
| `GET /llaves` | token → `{tiene, puedeCrear}`: si este navegador tiene su llave de acceso y si todavía la puede crear (en los 10 minutos después de parearse; decisión 51) | 401 |
| `POST /llaves/opciones` | token de un navegador, desde la dirección del tablero → `{desafioId, opciones}` para `navigator.credentials.create`: la llave pide la huella, la cara o el PIN y se ata al nombre del tablero | 401, 403 teléfono, fuera de los 10 minutos o por la IP, 409 ya tiene llave, 503 sin la librería `webauthn` |
| `POST /llaves` | `{desafioId, credencial}` → 201 `{tiene, puedeCrear}`: guarda la parte pública de la llave si la firma, el desafío, la dirección y la verificación de la persona están bien | 401, 403 si algo no coincide, 409 ya tiene llave o pasaron los 10 minutos, 503 |
| `POST /llaves/desafio` | `{accion}` (lo mismo que se va a mandar, por ejemplo `{"tipo": "bloquear", "alertaId": "a-1", "duracion": 3600}`) → `{desafioId, opciones}` para `navigator.credentials.get`; la firma vuelve con la acción en la cabecera `X-Llave` (base64url de `{desafio, credencial}`), sirve una sola vez y vence a los dos minutos | 401, 403 sin llave para este tablero, 422 acción mal armada, 503 |
| `GET /firewall/bloqueos` | cabecera `X-Token-Firewall` (no el token de un dispositivo) → `{bloqueos: [{id, ip, segundos}], horaServidor}`, lo que el firewall tiene que tener bloqueado ahora; `segundos` null es sin vencimiento | 401 token inválido, 503 sin token configurado |
| `POST /firewall/informe` | `{aplicados, rechazados: [{id, motivo}], nuncaBloquear}`, con `X-Token-Firewall` → `{aplicados, rechazados}` | 401, 422 formato, 503 |

### Una alerta

```json
{
  "id": "a-5f2c9e1b7a44", "creadaEn": "2026-10-11T14:25:10+00:00",
  "inicio": "2026-10-11T14:02:00+00:00", "fin": "2026-10-11T14:20:10+00:00",
  "cantidadFlujos": 1198, "uidReferencia": "CxYz1...",
  "origenIp": "172.16.0.1", "origenNombre": "gw-borde", "destinoIp": "192.168.10.50", "destinoNombre": "srv-web",
  "servicio": "ftp", "protocolo": "tcp", "puerto": 21, "puertoReferencia": 21, "puertosDistintos": 1,
  "tipoAtaque": "fuerza_bruta", "confianzaTipo": 1.0, "severidad": "alta",
  "veredictos": {
    "ia": true, "errorReconstruccion": 0.0412, "umbralIa": 0.00306383,
    "umbrales": true, "contexto60s": 65, "umbralServicio": 1, "servicioSinLineaBase": false,
    "reglas": false, "parDestino": "192.168.10.50:21", "parEnCatalogo": true
  },
  "estadoRevision": "sin_revisar", "historial": [], "version": 1, "actualizadaEn": "2026-10-11T14:25:10+00:00",
  "avisoTelegram": null,
  "asignacion": {"responsableId": "d-2", "responsable": "Laura", "porId": "d-1", "por": "Taiel", "fecha": "2026-10-11T14:30:00+00:00"},
  "asignaciones": [{"responsableId": "d-2", "responsable": "Laura", "porId": "d-1", "por": "Taiel", "fecha": "2026-10-11T14:30:00+00:00"}]
}
```

- `protocolo`: el transporte del episodio, `tcp` o `udp`. El detalle lo muestra junto al puerto ("SSH · TCP/22").
- `tipoAtaque`: `fuerza_bruta`, `escaneo`, `ddos`, `probable_falsa_alarma`, `sin_tipificar` o `null` (la detectaron solo las reglas o los umbrales).
- `severidad` sale del tipo según la tabla de la red (`PUT /severidades`). Con `tipoAtaque` en `null`, sale del detector que la marcó: `servicio_no_autorizado` si fueron las reglas, `volumen_inusual` si fueron los umbrales y, si fueron los dos, la más alta de las dos (decisión 32). Por defecto: fuerza bruta y sin tipificar en alta; escaneo, denegación de servicio, servicio no autorizado y volumen inusual en media; probable falsa alarma en baja. Ninguna alerta se oculta: el tipo ordena. Una base anterior a la 1.0.3 pasa lo que tenía en `solo_reglas_o_umbrales` a las dos claves nuevas al iniciar.
- Desde la API 0.6.0 la severidad también depende del equipo destino (decisión 41): un nivel más si es crítico y uno menos si es poco importante, con tope en alta y piso en baja (`ajustar` en `app/semilla.py`, con la misma tabla que la app). Todos los equipos arrancan normales, así que hasta que alguien marca uno la severidad es la del tipo. Todo usa la severidad final: `/estado`, los filtros, los avisos de Telegram y el Resumen.
- `asignacion`: el responsable de ahora, o `null` si nadie la tiene; `asignaciones`: todos los cambios, para el historial del detalle (decisión 42).
- `avisoTelegram`: cuándo salió el primer aviso de Telegram de la alerta, o `null`. Cuando sale, la alerta cuenta como modificada y los teléfonos la vuelven a bajar; el historial del detalle lo muestra.
- `estadoRevision`: `sin_revisar`, `ataque_real`, `falso_positivo`. `resultado` y `motivo` de la revisión: ataque real admite `escalado_al_proveedor`, `bloqueado_manualmente`, `contrasena_cambiada`, `investigando`; falso positivo admite `tarea_programada`, `backup`, `equipo_nuevo`, `usuario_autorizado`, `otro`. El comentario es obligatorio. Las revisiones guardadas con los motivos anteriores se pasan a los nuevos al arrancar.

### Sincronización por cursor

Cada cambio en una alerta (alta nueva, revisión, cambio de severidades) le asigna un número de secuencia creciente. `GET /alertas` devuelve las alertas con secuencia mayor que la del cursor, de a 200, en orden de cambio, y el cursor siguiente (`<instalación>:<secuencia>`). La app guarda el último cursor por red; si el servidor se reinstaló, la instalación no coincide y responde `410`: la app sincroniza desde cero conservando sus acciones pendientes.

### Revisiones

`accionId` es el identificador único que genera el teléfono por cada acción: si la misma acción se reenvía, la API devuelve la alerta sin aplicarla dos veces. `versionVista` es la versión de la alerta que la persona vio al revisar; si otra persona la revisó después (la versión del servidor es mayor), la API responde `409` con esa revisión y la app pregunta si aplica la propia igual (`forzar: true`).

Cada revisión del `historial` trae el teléfono (`autor`) y, desde la 1.0.5, la persona (`persona`, "Laura"); la app muestra "Laura · Teléfono de Laura". Trae también dos horas: `revisadaEn`, cuándo se revisó en el teléfono (la manda la app), y `fecha`, cuándo llegó al servidor. Sin conexión, la revisión queda en el teléfono y sale después, así que las dos pueden diferir; el historial del detalle muestra las dos. Si la app no manda `revisadaEn`, vale la hora de llegada, y una hora posterior a la llegada (reloj adelantado) también se toma como la de llegada. Las revisiones guardadas antes de este campo lo traen en `null`.

### Perfiles, registro de cambios e importancia (decisiones 39 a 41)

Cada teléfono pareado es una persona con su nombre y su perfil. El analista revisa alertas y elige sus avisos; el administrador, además, cambia las severidades y la importancia de los equipos, asigna alertas a cualquiera y suma o quita gente. Lo controla la API: lo que no le toca a un analista responde `403`, y la app además esconde los botones. Siempre queda al menos un administrador: cambiar el perfil o quitar a alguien que deje gente sin administrador responde `409`. Los teléfonos pareados antes de la API 0.6.0 quedan como administradores, porque antes todos podían cambiar las severidades.

El registro de cambios (`app/registro.py`) anota los cambios de severidad y de importancia, con su motivo obligatorio, y los de la gente (sumar, cambiar el perfil, quitar), sin motivo. Lo guarda la API y desde la app no se puede borrar ni editar.

### Asignaciones (decisión 42)

`PUT /alertas/{id}/asignacion` guarda quién, a quién y cuándo, y mueve la secuencia de la alerta para que todos los teléfonos la vuelvan a bajar. El administrador asigna a cualquiera o saca la asignación; el analista se asigna una sin asignar y suelta la suya, y no le puede sacar una alerta a otra persona. Si dos asignan a la vez, queda la última que llega. Asignar no cambia la versión de la alerta, así que no genera el `409` de las revisiones: cualquiera la puede revisar. Si la persona a la que se la asignaron tiene Telegram vinculado, el bot le avisa ("Taiel te asignó una alerta ALTA · SSH · 09:51", con el botón al detalle); asignarse una uno mismo no avisa.

### Estado del sistema y latido del análisis en vivo

Si el análisis en vivo del laboratorio se cae, no se toman los registros, así que el servidor lo vigila. El pipeline (`pipeline_vivo_latido.py` en zeek-lab, servicio systemd con reinicio automático) manda `POST /ingesta/latido` al final de cada ciclo de 20 segundos, aunque no haya episodios, con los flujos que procesó, la hora del flujo más nuevo del `conn.log` y si el proceso de Zeek está vivo. Se guarda el último en la tabla `meta` (`latido` y `latido_recibido_en`, sin tablas nuevas); la frescura se mide con la hora de llegada al servidor, no con el reloj de la VM (`app/latido.py`).

`GET /salud` con token suma `sistema`, que la app muestra en la tarjeta "Estado del sistema" de Estado:

```json
{"servidor": "funciona", "analisis": "funciona", "sensor": "funciona",
 "ultimaCorrida": "2026-10-24T21:30:12+00:00", "segundosDesdeUltimaCorrida": 7,
 "ultimoFlujo": "2026-10-24T21:29:50+00:00", "flujosUltimaCorrida": 42, "limiteSegundos": 120}
```

Sin ningún latido, `analisis` y `sensor` son `sin_datos`. Con el último latido de hasta `ZONDA_LATIDO_LIMITE` segundos (2 minutos), el análisis `funciona` y el sensor `funciona` o `detenido` según lo que dijo ese latido; con uno más viejo, el análisis está `detenido` y del sensor no se sabe (`sin_datos`). Sin token, `sistema` es `null`: lo del laboratorio lo ve solo un teléfono pareado.

### Tráfico del sensor y tablero web (decisión 49)

El tablero web se sirve en `/panel` desde el mismo proceso (`backend/web`): misma dirección, mismo HTTPS de Caddy y el mismo token por dispositivo. Se entra con un código de pareo de un solo uso (`zonda pair` o Ajustes → Personas en la app) y el navegador queda como un dispositivo más («Tablero web · Chrome en Windows»), con su perfil, su vencimiento y el botón Salir, que lo revoca. Hace lo mismo que la app (decisión 50): revisar en dos pasos con Deshacer y el conflicto, asignar, el Resumen con descarga y los ajustes de administrador, con las mismas reglas de perfil, que controla el servidor. En el teléfono se arma como la app, con la barra de abajo. Se maneja como Grafana (decisión 53): el horario va en la dirección de la página (`?desde=now-15m&hasta=now&actualizar=5s`), cada panel tiene su menú (en grande, datos, CSV, imagen y enlace), En vivo se arma en filas plegables, las conexiones por servicio llevan la leyenda en tabla y el mapa es un grafo. Desde la decisión 54, el detalle y el Resumen son los de la app (la imagen en claro se arma en el navegador; el PDF es un reporte armado para la hoja A4, decisión 55), Alertas tiene el mismo horario y baja su lista en CSV, los contadores de sin revisar abren esa lista, la barra de arriba queda fija al bajar y, con el permiso del navegador, el tablero avisa las altas cuando no está a la vista. Desde la decisión 56, «Alertas en el tiempo» cuenta las alertas de cada barra por severidad (tocar una barra abre esa lista) y los «?» abren un globito. `POST /personas/codigo` devuelve además el QR dibujado (`qrSvg`) para mostrarlo sin librerías. Las respuestas de `/panel` llevan una política de contenido que solo deja cargar lo que sirve este servidor (ECharts 5.6.0, la letra Inter y el logo están en `backend/web`), y no se puede meter en un marco de otra página. Las áreas del mapa (una por VLAN o subred) están en `web/areas.json`.

El sensor manda el tráfico con el mismo token de la ingesta. `herramientas/sensor/metricas_trafico.py` corre en zeek-lab al lado del análisis en vivo, sin tocarlo: sigue el `conn.log`, suma cada conexión y cada 5 segundos manda `POST /ingesta/trafico`:

```json
{"tramos": [{"t": 1793397600, "servicio": "ssh", "conexiones": 20, "fallidas": 0, "bytes": 61234, "paquetes": 410}],
 "pares": [{"minuto": 1793397600, "origen": "192.168.30.50", "destino": "192.168.20.20", "servicio": "ssh",
            "conexiones": 20, "fallidas": 0, "bytes": 61234}]}
```

`t` es el comienzo de un tramo de 5 segundos (segundos desde 1970, reloj del sensor); `fallidas` son las conexiones que no llegaron a establecerse (estados `S0`, `REJ`, `RSTOS0`, `RSTRH`, `SH` y `SHR` de Zeek); los bytes y paquetes se reparten entre los tramos que duró cada conexión. El servidor suma, nunca pisa: Zeek escribe una conexión cuando termina, y una larga completa tramos que ya habían llegado. Guarda `ZONDA_DIAS_TRAFICO` días (7) en las tablas `trafico` y `trafico_pares`. Si el servidor no contesta, el sensor junta lo que no pudo mandar (hasta un día) y recuerda hasta dónde leyó, así un reinicio no cuenta dos veces. Con `--rellenar-horas 168` manda antes la última semana de los archivos viejos de Zeek.

`GET /trafico?desde=&hasta=&paso=` (token de un dispositivo) devuelve el total y cada servicio en tramos de `paso` segundos (5, 10, 15, 30, 60, 300, 900 o 3600; hasta 5.000 puntos), solo los tramos con tráfico, y `ultimoTramo`. `GET /trafico/pares?desde=&hasta=` devuelve quién habló con quién, con el nombre de cada equipo del catálogo y sus servicios de más a menos conexiones. `GET /latidos?desde=&hasta=` devuelve los latidos del análisis en vivo por minuto (decisión 52), para los gráficos del estado del sistema; se guardan los mismos días que el tráfico.

**Pérdida de captura.** Si el puerto espejo se satura, Zeek deja de ver parte del tráfico y Zonda no se entera de lo que no ve. Zeek lo mide con `misc/capture-loss` (ya cargado en zeek-lab): cada 15 minutos escribe en `capture_loss.log` qué porcentaje de lo que confirmaron los ACK de TCP no llegó a verse (`percent_lost`; sin tráfico TCP da 0 ACK y 0 %). El latido manda la última medición (`perdidaCaptura`, `perdidaCapturaEn`, `acksCaptura`) y `sistema` la devuelve con `perdidaLimite`. Si pasa de `ZONDA_PERDIDA_LIMITE` (1 %) con Zeek vivo y el latido al día, el sensor queda `parcial` ("viendo parcialmente", en amarillo en la app). Para probarlo sin saturar nada, el script del laboratorio toma el porcentaje de `vivo/.perdida_simulada` si existe y lo marca con `perdidaSimulada: true`.

### Bloqueos en el firewall (decisión 51)

Viene apagado: sin `ZONDA_FIREWALL_TOKEN`, las rutas de los bloqueos responden `503` y la red queda en modo de observación. Con el token, un administrador bloquea desde su teléfono el origen de una alerta, con un motivo obligatorio y una duración de 1 o 24 horas, o hasta desbloquear (`duracion: null`), que siempre se puede soltar antes. La IP sale de la alerta, nunca del pedido, y tiene que ser la de un equipo (IPv4, sin difusión ni multidifusión). Un analista o el tablero web (un dispositivo `navegador`) lo piden, y lo aprueba un administrador desde su teléfono; un pedido que nadie aprueba en una hora vence.

El servidor no guarda ninguna clave del firewall. El agente del firewall pregunta cada pocos segundos con `GET /firewall/bloqueos`, valida cada IP contra su lista de «nunca bloquear» y sus topes, la aplica con su vencimiento y cuenta qué hizo con `POST /firewall/informe`: la app dice «Aplicado» recién ahí. El vencimiento lo cumple el propio firewall, aunque el servidor no responda. La lista de «nunca bloquear» que manda el firewall también la usa el servidor para no aceptar esos equipos. El agente para Linux con nftables está en `herramientas/firewall/`, con su tabla propia, su servicio de systemd y cómo se instala.

Cada bloqueo y cada desbloqueo quedan en el registro de cambios, con su motivo; `GET /registro` los trae solo con `?bloqueos=true`, porque la app 1.0.8 no conoce esos tipos. El bot avisa a quienes bloquean (los administradores y los analistas con permiso), si tienen Telegram, cuando un bloqueo se pide, se aprueba, se desbloquea o el firewall no lo aplica, también a quien lo hizo, sin IP ni nombres de equipos («Taiel bloqueó el origen de una alerta ALTA · SSH · 09:51. Por 1 h.»).

## Bot de Telegram

El aviso inmediato de cada alerta llega por un bot de Telegram (informe, sección 6). El bot corre dentro de la API: si `TELEGRAM_BOT_TOKEN` está en `.env`, al arrancar uvicorn arrancan también dos tareas, una que lee los mensajes del bot (long polling con `getUpdates`) y otra que cada 5 segundos busca alertas nuevas y manda los avisos (`sendMessage`). Habla con la API de bots por HTTP con httpx, sin una biblioteca aparte.

**Crear el bot (una vez).** En Telegram, abrir @BotFather y mandar `/newbot`: nombre "Zonda alertas" y usuario `zonda_alertas_bot` (si está tomado, otro que termine en `bot`). BotFather responde con el token: va en `.env` como `TELEGRAM_BOT_TOKEN=...`. El token es una contraseña: no va al repositorio, ni a los registros, ni a un chat. Al arrancar, la API le pregunta a Telegram el usuario del bot, así que el enlace de vinculación sale bien aunque se haya elegido otro.

**Vincular el chat.** La app pide `POST /telegram/vinculo`, que devuelve un código de un uso (10 minutos) y el enlace `https://t.me/<bot>?start=<código>`. La app abre el enlace, la persona toca Iniciar y Telegram le manda al bot `/start <código>`: el bot asocia ese chat con el teléfono que pidió el código y responde qué viaja por Telegram. Solo en chats privados, porque en un grupo los avisos los leería cualquiera. La app se entera con `GET /telegram`.

**Los avisos.** Cada alerta creada después del vínculo genera un aviso por chat, según cómo avisa esa severidad en ese teléfono (`PUT /telegram/avisos`): con sonido, en silencio (`vibracion` en la API: un mensaje sin sonido, porque un bot no puede pedir solo vibración) o sin aviso. La tabla `avisos_telegram` guarda uno por alerta y por chat, así nunca sale dos veces. Si Telegram no responde, se reintenta en la vuelta siguiente; si la persona bloqueó el bot, el teléfono queda sin Telegram.

```
Alerta ALTA · SSH · 02:09
Tocá para ver el detalle en Zonda.
Red: Oficina central
[Ver el detalle en Zonda]
```

**El análisis en vivo detenido.** El vigía del latido (`app/latido.py`) corre siempre, con bot o sin él: cada 15 segundos mira la edad del último latido. Si pasa los 2 minutos, avisa a cada chat vinculado, una sola vez, y cuando vuelve a llegar un latido avisa que volvió; sin bot solo lleva la cuenta. El estado queda en `meta` (`latido_aviso`), así que reiniciar la API no repite el aviso. Si Telegram no responde, se reintenta en la vuelta siguiente. Los dos avisos, con sonido y sin botón:

```
Análisis en vivo detenido
Zonda no recibe noticias del análisis en vivo desde las 18:41: las alertas nuevas no llegan hasta que vuelva.
Red: Oficina central

Análisis en vivo funcionando de nuevo
Zonda volvió a recibir el análisis en vivo a las 18:47.
Red: Oficina central
```

**El sensor viendo parcialmente.** Con el mismo criterio, el vigía avisa una vez cuando la pérdida de captura pasa del 1 % y otra cuando vuelve a quedar debajo (estado en `meta`, `perdida_aviso`). Solo mira un latido al día con Zeek vivo: si el latido venció, avisa la caída del análisis. Con la pérdida simulada, el aviso suma "(Prueba: pérdida simulada en el laboratorio.)":

```
Sensor viendo parcialmente
Zeek perdió el 3,2 % de lo que pasó por el puerto espejo en la medición de las 18:45: puede haber tráfico, y ataques, que Zonda no ve.
Red: Oficina central

Sensor viendo todo de nuevo
La pérdida de captura bajó al 0,0 % en la medición de las 19:00.
Red: Oficina central
```

Por Telegram viajan solo la severidad, el servicio y la hora, y de qué red viene (RNF02): nunca direcciones IP ni nombres de equipos, que se ven en la app por el canal cifrado. El botón abre `https://taiel1998.github.io/a/<id>?red=&severidad=&servicio=&deteccion=&aviso=`: además del id lleva lo mismo que dice el aviso, para que la app lo muestre si todavía no tiene la alerta (RF03, criterio 3).

**Cómo se prueba.** `tests/test_telegram.py` usa un Telegram falso (`tests/conftest.py`): vínculo, códigos vencidos o usados, grupos, cada modo de aviso, avisos que no se repiten, el bot bloqueado y que ningún aviso ni enlace tenga una IP o un nombre de equipo de la semilla. El latido, `sistema` y los avisos del vigía, en `tests/test_latido.py`. Con el bot de verdad: vincular Telegram desde la app y correr `.\zonda.cmd emitir`, o poner `ZONDA_EMITIR_CADA=60` en `.env` para que aparezca una alerta por minuto, y esperar el aviso.

La API tiene que correr en un solo proceso: con dos, los dos leerían los mensajes del mismo bot y Telegram rechaza al segundo.

## Publicación con HTTPS (Caddy + DuckDNS + Let's Encrypt)

El build de release de la app solo habla HTTPS, en todas las versiones de Android (RNF04, decisión 26). La API no cambia: uvicorn escucha solo dentro de la notebook (`127.0.0.1:8000`) y Caddy la publica en el puerto 443 con un certificado de Let's Encrypt para un nombre de DuckDNS que apunta a la IP privada de la notebook. Como esa IP no se ve desde internet, Let's Encrypt no puede entrar a comprobar que el nombre es nuestro: lo comprueba por DNS, con un registro que Caddy escribe en DuckDNS con el token. Así la API no queda expuesta a internet y el teléfono la encuentra con el mismo nombre aunque cambie la IP.

**Una sola vez.**

1. Crear la cuenta en https://www.duckdns.org y un subdominio, por ejemplo `zonda-equipo`. El token está arriba de todo de la página.
2. En `.env`: `ZONDA_DOMINIO=zonda-equipo.duckdns.org` y `DUCKDNS_TOKEN=<token>`. No hace falta `ZONDA_URL_PUBLICA`: con el nombre, el QR lleva `https://zonda-equipo.duckdns.org`.
3. Caddy para Windows con el módulo de DuckDNS: en https://caddyserver.com/download elegir `windows amd64` y marcar `caddy-dns/duckdns`. Va en `backend\caddy\caddy.exe` (carpeta ignorada por git). Lo probamos con la versión 2.11.7.

**Cada vez, para la demo.**

```powershell
cd backend
.\demo.cmd
```

`demo.cmd` hace todo en una ventana: actualiza DuckDNS con la IP de la notebook en esa red, arranca la API con el bot de Telegram (un solo proceso y sin `--reload`, como pide el bot) y después Caddy, y espera hasta que `https://<nombre>/salud` responde con el certificado. Al final dice si el nombre apunta a la notebook. La primera vez Caddy le pide el certificado a Let's Encrypt (tarda un par de minutos); después lo guarda en `%AppData%\Caddy` y lo renueva solo. Lo que escribe Caddy queda en `caddy\caddy.log`; en la ventana se ven los pedidos a la API. Ctrl+C cierra la API y Caddy (si cmd pregunta si termina el trabajo por lotes, S).

Para parear un teléfono, en otra terminal: `.\zonda.cmd pair`. Si la IP de la notebook cambia en medio de la demo: `powershell -ExecutionPolicy Bypass -File scripts\actualizar_duckdns.ps1`.

La primera vez que arranca Caddy, Windows pregunta si puede usar la red: hay que aceptar también en las redes públicas, porque el hotspot de un teléfono suele quedar como pública. Si se rechazó, se cambia en Seguridad de Windows → Firewall y protección de red → Permitir una aplicación a través del firewall.

**Qué dice el Caddyfile.**

- TLS 1.2 y 1.3 (`protocols tls1.2 tls1.3`): un cliente que ofrece solo 1.0 o 1.1 recibe la alerta "protocol version".
- El certificado lo emite Let's Encrypt (`issuer acme` con `dns duckdns`), sin mail: Let's Encrypt ya no manda avisos de vencimiento y Caddy lo renueva solo.
- Sin el puerto 80 (`auto_https disable_redirects`): la app nunca usa http, así que no hace falta redirigir, y un pedido sin cifrar no encuentra nada que lo atienda.
- Quien entra por la IP en lugar del nombre recibe igual el certificado del nombre (`default_sni`). Android ve que no coincide con la dirección y corta antes de mandar nada: en la app es "No se pudo verificar el servidor" (el caso malo del RNF04).

**Cómo se prueba.** Con la demo levantada, en otra terminal:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\probar_tls.ps1
```

Con el `openssl` de Git para Windows comprueba que Caddy acepte TLS 1.3 y 1.2 y rechace 1.1 y 1.0, que el certificado sea de Let's Encrypt y valga para el nombre, y que por la IP el certificado no coincida. Para 1.0 y 1.1 baja el nivel de seguridad del propio openssl (`-cipher DEFAULT@SECLEVEL=0`): sin eso, openssl 3 ni siquiera las ofrece y la prueba no le preguntaría nada al servidor. Si la red no resuelve el nombre, se le pasa la IP con `-Ip`.

**Riesgo 2 del plan.** Algunos routers bloquean los nombres públicos que apuntan a una IP privada: la página no carga aunque Caddy esté bien, y `demo.cmd` lo avisa al final. En la demo se usa el hotspot de un teléfono, con la notebook conectada a ese hotspot.

**En una PyME** es el mismo esquema con un nombre del dominio propio (por ejemplo `zonda.empresa.com.ar`) y el módulo de Caddy del proveedor de DNS de la empresa, en una computadora que ya tienen.

## Cómo está organizado

```
app/config.py      variables de entorno y .env
app/db.py          SQLite con la biblioteca estándar: esquema, conexión por hilo, secuencia del cursor
app/modelos.py     esquemas de entrada y salida (pydantic), el contrato con la app
app/auth.py        tokens por dispositivo (hash SHA-256, 90 días), códigos de un uso
app/semilla.py     carga de alertas_semilla.json con las fechas corridas
app/rutas.py       las rutas del contrato
app/revisiones.py  cómo se guarda una revisión: la usan la ruta y zonda tranquila
app/emisor.py      alertas nuevas para la demo, cada N segundos o una a pedido con zonda emitir
app/telegram.py    bot de Telegram: vínculo con /start y aviso de cada alerta nueva
app/latido.py      latido del análisis en vivo: el estado del sistema y el vigía que avisa si se detiene
app/bloqueos.py    bloqueos del origen de una alerta en el firewall: las rutas de la gente y las del agente
app/cli.py         zonda pair, revoke, dispositivos, emitir, tranquila
herramientas/firewall/  el agente del firewall (Linux con nftables): programa, tabla, servicio e instalador
tests/             pytest con TestClient y una base nueva por test
Caddyfile          HTTPS delante de la API: TLS 1.2 y 1.3, Let's Encrypt por DNS, sin el puerto 80
demo.cmd           la demo con HTTPS en una ventana (arranca scripts/demo.ps1)
scripts/demo.ps1                DuckDNS, la API con el bot y Caddy, y las comprobaciones
scripts/actualizar_duckdns.ps1  apunta el nombre a la IP de la notebook en la red actual
scripts/probar_tls.ps1          las pruebas de HTTPS con openssl s_client
scripts/iniciar.ps1             la API sola, para desarrollo
```
