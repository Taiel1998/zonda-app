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

Probar: http://127.0.0.1:8000/salud y la documentación automática en http://127.0.0.1:8000/docs. Desde el emulador de Android la API se alcanza en `http://10.0.2.2:8000`.

Tests:

```powershell
.venv\Scripts\python -m pytest
```

Configuración: copiar `.env.ejemplo` a `.env` (no se commitea). Variables que lee la API:

| Variable | Para qué | Por defecto |
|---|---|---|
| `ZONDA_RED_ID`, `ZONDA_RED_NOMBRE` | Identidad de la red que sirve este servidor (una API por red) | `red-oficina`, `Oficina central` |
| `ZONDA_URL_PUBLICA` | Dirección que va en el QR de pareo | `http://127.0.0.1:8000` |
| `ZONDA_DB_PATH` | Archivo SQLite | `backend/zonda.db` |
| `ZONDA_SEMILLA` | JSON con las alertas de muestra | `backend/alertas_semilla.json` |
| `ZONDA_EMITIR_CADA` | Segundos entre alertas nuevas que emite el servidor durante la demo (0 apaga) | `0` |
| `ZONDA_MULTIPLICAR` | Copias de la semilla, solo para probar la lista con 1.000 alertas | `1` |
| `ZONDA_REVISAR_SEMILLA` | Con `1`, la semilla arranca con tres de cada cuatro alertas de más de dos horas ya revisadas, para la demo y las capturas del Resumen; vale al cargar la semilla en una base nueva | `0` |
| `ZONDA_TAMANIO_PAGINA` | Alertas por página de `GET /alertas`; con un número chico se ve la barra de la primera descarga | `200` |
| `TELEGRAM_BOT_TOKEN` | Token del bot de Telegram (lo da BotFather). Sin token la API anda igual, sin bot | vacío |
| `ZONDA_TELEGRAM_BOT` | Usuario del bot, por si Telegram no responde al arrancar; si responde, vale el que dice Telegram | `zonda_alertas_bot` |
| `ZONDA_ENLACE_AVISO` | Comienzo del enlace de cada aviso, el App Link que abre la alerta en la app | `https://taiel1998.github.io/a/` |
| `ZONDA_TELEGRAM_API` | Dirección de la API de bots. Solo para probar con un Telegram simulado | `https://api.telegram.org` |

## Datos de muestra y fechas corridas

Al arrancar con la base vacía, la API carga `alertas_semilla.json` y **corre todas las fechas** para que el episodio más reciente quede una hora antes del momento del arranque: los datos originales son de julio de 2017 y, sin correrlos, los períodos de 24 horas, 7 y 30 días de la app no tendrían nada. Los identificadores no cambian.

**Qué semilla está cargada hoy:** una de ejemplo, armada a mano con `herramientas/generar_semilla_de_ejemplo.py` (28 episodios con la forma de CICIDS2017). Se reemplaza por la exportación real de la plataforma de detección sin tocar nada más. Para volver a cargar una semilla nueva, borrar `zonda.db`.

## Comandos de administración

No hay interfaz web de administración; se usan comandos en la computadora de la API:

```powershell
.\zonda.cmd pair              # código de pareo de un uso (vence a los 10 minutos), con el QR en pantalla y en PNG
.\zonda.cmd dispositivos      # teléfonos pareados, con estado y vencimiento del token
.\zonda.cmd revoke d-xxxxxxxx # revoca el token de un teléfono (la app pide un pareo nuevo)
```

El QR contiene `zonda:<ZONDA_URL_PUBLICA>#<código>;vence=<fecha ISO>`, por ejemplo `zonda:https://zonda-empresa.duckdns.org#K7M2-QX9P;vence=2026-10-12T02:19:00+00:00`. En Linux o macOS: `python -m app.cli pair`.

## Contrato

Convenciones: campos en camelCase; valores de enumeraciones en minúsculas con guion bajo; fechas en ISO 8601 con zona (UTC). Autenticación con `Authorization: Bearer <token>`; sin token o con token vencido o revocado la API responde `401` y la app pide un pareo nuevo sin borrar su caché.

| Ruta | Recibe → devuelve | Errores |
|---|---|---|
| `GET /salud` | nada → `version`, `hora`, `alertas` (cantidad), `red`, `tokenVence` (si mandó token) | 401 si el token venció |
| `GET /estado` | token → `sinRevisar {alta, media, baja}`, `ultimoFlujoProcesado`, `horaServidor` | 401 |
| `GET /alertas?desde=&severidad=&servicio=&destino=&metodo=&estado=` | cursor → `alertas` nuevas o modificadas, `cursorSiguiente`, `hayMas`; sin cursor trae solo los últimos 30 días y suma `total` | 401, 410 cursor de otra instalación |
| `GET /alertas/{id}` | id → la alerta con sus veredictos e `historial` | 401, 404 |
| `POST /alertas/{id}/revision` | `{accionId, resultado, motivo, comentario, versionVista, forzar, revisadaEn}` → la alerta actualizada | 401, 404, 409 con `revisionAjena` y la alerta, 422 motivo que no corresponde o comentario vacío |
| `GET /severidades`, `PUT /severidades` | `{tipo: severidad}` → la tabla completa; el PUT marca todas las alertas como modificadas | 401, 422 tipo desconocido |
| `POST /pareo` | `{codigo, nombreDispositivo}` → `{token, red, emitido, vence, dispositivoId, alertas30Dias}` | 400 inválido, 410 vencido o usado |
| `GET /telegram` | token → `{vinculado, bot, vinculadoEn, avisos}` de este teléfono | 401 |
| `POST /telegram/vinculo` | `{avisos}` opcional → `{codigo, enlaceBot, vence}`; guarda los avisos | 401, 503 sin bot |
| `DELETE /telegram/vinculo` | token → 204; el chat deja de recibir los avisos y el bot se despide | 401 |
| `POST /telegram/prueba` | token → 204; el bot manda un aviso de prueba al chat | 401, 409 sin Telegram vinculado, 502 Telegram no respondió, 503 sin bot |
| `PUT /telegram/avisos` | `{alta, media, baja}` con `sonido`, `vibracion` o `sin_aviso` → lo guardado | 401, 422 valor desconocido |
| `DELETE /dispositivos/actual` | token → 204 | 401 |
| `GET /catalogo` | token → `{equipos {ip: nombre}, servicios []}` | 401 |

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
  "avisoTelegram": null
}
```

- `protocolo`: el transporte del episodio, `tcp` o `udp`. El detalle lo muestra junto al puerto ("SSH · TCP/22").
- `tipoAtaque`: `fuerza_bruta`, `escaneo`, `ddos`, `probable_falsa_alarma`, `sin_tipificar` o `null` (la detectaron solo las reglas o los umbrales).
- `severidad` sale del tipo según la tabla de la red (`PUT /severidades`). Por defecto: fuerza bruta y sin tipificar en alta; escaneo, denegación de servicio y tipo vacío en media; probable falsa alarma en baja. Ninguna alerta se oculta: el tipo ordena.
- `avisoTelegram`: cuándo salió el primer aviso de Telegram de la alerta, o `null`. Cuando sale, la alerta cuenta como modificada y los teléfonos la vuelven a bajar; el historial del detalle lo muestra.
- `estadoRevision`: `sin_revisar`, `ataque_real`, `falso_positivo`. `resultado` y `motivo` de la revisión: ataque real admite `escalado_al_proveedor`, `bloqueado_manualmente`, `contrasena_cambiada`, `investigando`; falso positivo admite `tarea_programada`, `backup`, `equipo_nuevo`, `usuario_autorizado`, `otro`. El comentario es obligatorio. Las revisiones guardadas con los motivos anteriores se pasan a los nuevos al arrancar.

### Sincronización por cursor

Cada cambio en una alerta (alta nueva, revisión, cambio de severidades) le asigna un número de secuencia creciente. `GET /alertas` devuelve las alertas con secuencia mayor que la del cursor, de a 200, en orden de cambio, y el cursor siguiente (`<instalación>:<secuencia>`). La app guarda el último cursor por red; si el servidor se reinstaló, la instalación no coincide y responde `410`: la app sincroniza desde cero conservando sus acciones pendientes.

### Revisiones

`accionId` es el identificador único que genera el teléfono por cada acción: si la misma acción se reenvía, la API devuelve la alerta sin aplicarla dos veces. `versionVista` es la versión de la alerta que la persona vio al revisar; si otra persona la revisó después (la versión del servidor es mayor), la API responde `409` con esa revisión y la app pregunta si aplica la propia igual (`forzar: true`).

Cada revisión del `historial` trae dos horas: `revisadaEn`, cuándo se revisó en el teléfono (la manda la app), y `fecha`, cuándo llegó al servidor. Sin conexión, la revisión queda en el teléfono y sale después, así que las dos pueden diferir; el historial del detalle muestra las dos. Si la app no manda `revisadaEn`, vale la hora de llegada, y una hora posterior a la llegada (reloj adelantado) también se toma como la de llegada. Las revisiones guardadas antes de este campo lo traen en `null`.

## Bot de Telegram

El aviso inmediato de cada alerta llega por un bot de Telegram (informe, sección 6). El bot corre dentro de la API: si `TELEGRAM_BOT_TOKEN` está en `.env`, al arrancar uvicorn arrancan también dos tareas, una que lee los mensajes del bot (long polling con `getUpdates`) y otra que cada 5 segundos busca alertas nuevas y manda los avisos (`sendMessage`). Habla con la API de bots por HTTP con httpx, sin una biblioteca aparte.

**Crear el bot (una vez).** En Telegram, abrir @BotFather y mandar `/newbot`: nombre "Zonda alertas" y usuario `zonda_alertas_bot` (si está tomado, otro que termine en `bot`). BotFather responde con el token: va en `.env` como `TELEGRAM_BOT_TOKEN=...`. El token es una contraseña: no va al repositorio, ni a los registros, ni a un chat. Al arrancar, la API le pregunta a Telegram el usuario del bot, así que el enlace de vinculación sale bien aunque se haya elegido otro.

**Vincular el chat.** La app pide `POST /telegram/vinculo`, que devuelve un código de un uso (10 minutos) y el enlace `https://t.me/<bot>?start=<código>`. La app abre el enlace, la persona toca Iniciar y Telegram le manda al bot `/start <código>`: el bot asocia ese chat con el teléfono que pidió el código y responde qué viaja por Telegram. Solo en chats privados, porque en un grupo los avisos los leería cualquiera. La app se entera con `GET /telegram`.

**Los avisos.** Cada alerta creada después del vínculo genera un aviso por chat, según cómo avisa esa severidad en ese teléfono (`PUT /telegram/avisos`): con sonido, con vibración (un mensaje sin sonido, porque un bot no puede pedir solo vibración) o sin aviso. La tabla `avisos_telegram` guarda uno por alerta y por chat, así nunca sale dos veces. Si Telegram no responde, se reintenta en la vuelta siguiente; si la persona bloqueó el bot, el teléfono queda sin Telegram.

```
Alerta ALTA · SSH · 02:09
Tocá para ver el detalle en Zonda.
Red: Oficina central
[Ver el detalle en Zonda]
```

Por Telegram viajan solo la severidad, el servicio y la hora, y de qué red viene (RNF02): nunca direcciones IP ni nombres de equipos, que se ven en la app por el canal cifrado. El botón abre `https://taiel1998.github.io/a/<id>?red=&severidad=&servicio=&deteccion=&aviso=`: además del id lleva lo mismo que dice el aviso, para que la app lo muestre si todavía no tiene la alerta (RF03, criterio 3).

**Cómo se prueba.** `tests/test_telegram.py` usa un Telegram falso (`tests/conftest.py`): vínculo, códigos vencidos o usados, grupos, cada modo de aviso, avisos que no se repiten, el bot bloqueado y que ningún aviso ni enlace tenga una IP o un nombre de equipo de la semilla. Con el bot de verdad: `ZONDA_EMITIR_CADA=60` en `.env` para que aparezca una alerta por minuto, vincular Telegram desde la app y esperar el aviso.

La API tiene que correr en un solo proceso: con dos, los dos leerían los mensajes del mismo bot y Telegram rechaza al segundo.

## Publicación con HTTPS (Caddy + DuckDNS + Let's Encrypt)

1. Crear un subdominio en https://www.duckdns.org (por ejemplo `zonda-equipo`) y copiar el token.
2. Copiar `.env.ejemplo` a `.env` y completar `ZONDA_DOMINIO`, `DUCKDNS_TOKEN`, `CADDY_EMAIL` y `ZONDA_URL_PUBLICA=https://<subdominio>.duckdns.org`.
3. Descargar Caddy para Windows con el módulo de DuckDNS desde https://caddyserver.com/download (elegir `windows amd64` y marcar `caddy-dns/duckdns`). Dejar `caddy.exe` en `backend\caddy\` (carpeta ignorada por git).
4. Apuntar el nombre a la IP privada de la notebook: `scripts\actualizar_duckdns.ps1` (lo hace también `scripts\iniciar.ps1`).
5. Iniciar la API (`scripts\iniciar.ps1`) y, en otra terminal, Caddy:

```powershell
cd backend
caddy\caddy.exe run --envfile .env
```

La primera vez Caddy pide el certificado a Let's Encrypt (tarda un minuto). Después, desde un teléfono en la misma red: `https://zonda-equipo.duckdns.org/salud`.

**Prueba de la primera semana (riesgo 2 del plan):** si el router bloquea nombres públicos que resuelven a direcciones privadas, la página no carga aunque Caddy esté bien. Probar con el hotspot de un celular, que es lo que se usa en la demo.

## Cómo está organizado

```
app/config.py      variables de entorno y .env
app/db.py          SQLite con la biblioteca estándar: esquema, conexión por hilo, secuencia del cursor
app/modelos.py     esquemas de entrada y salida (pydantic), el contrato con la app
app/auth.py        tokens por dispositivo (hash SHA-256, 90 días), códigos de un uso
app/semilla.py     carga de alertas_semilla.json con las fechas corridas
app/rutas.py       las rutas del contrato
app/emisor.py      alertas nuevas cada N segundos para la demo
app/telegram.py    bot de Telegram: vínculo con /start y aviso de cada alerta nueva
app/cli.py         zonda pair, revoke, dispositivos
tests/             pytest con TestClient y una base nueva por test
```
