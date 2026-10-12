# API de alertas de Zonda

API mínima en FastAPI con SQLite que hace de simulador de la plataforma de detección: sirve alertas de muestra armadas a partir del dataset público CICIDS2017 y recibe las revisiones que hace la app. Corre en la notebook del equipo, publicada con HTTPS por Caddy.

Estado: fase 0. Solo existe `GET /salud`. El resto del contrato llega en las fases 3 y 7.

## Cómo ejecutarla

Requiere Python 3.12.

```powershell
cd backend
python -m venv .venv
.venv\Scripts\pip install -r requirements.txt
.venv\Scripts\python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Probar: http://127.0.0.1:8000/salud (y la documentación automática en http://127.0.0.1:8000/docs).

Tests:

```powershell
.venv\Scripts\python -m pytest
```

## Publicación con HTTPS (Caddy + DuckDNS + Let's Encrypt)

1. Crear un subdominio en https://www.duckdns.org (por ejemplo `zonda-equipo`) y copiar el token.
2. Copiar `.env.ejemplo` a `.env` y completar `ZONDA_DOMINIO`, `DUCKDNS_TOKEN` y `CADDY_EMAIL`.
3. Descargar Caddy para Windows con el módulo de DuckDNS desde https://caddyserver.com/download (elegir `windows amd64` y marcar `caddy-dns/duckdns`). Dejar `caddy.exe` en `backend\caddy\` (carpeta ignorada por git).
4. Apuntar el nombre a la IP privada de la notebook: `scripts\actualizar_duckdns.ps1` (lo hace también `scripts\iniciar.ps1`).
5. Iniciar la API (`scripts\iniciar.ps1`) y, en otra terminal, Caddy:

```powershell
cd backend
caddy\caddy.exe run --envfile .env
```

La primera vez Caddy pide el certificado a Let's Encrypt (tarda un minuto). Después, desde un teléfono en la misma red: `https://zonda-equipo.duckdns.org/salud`.

**Prueba de la primera semana (riesgo 2 del plan):** si el router bloquea nombres públicos que resuelven a direcciones privadas, la página no carga aunque Caddy esté bien. Probar con el hotspot de un celular, que es lo que se usa en la demo.

## Contrato

Se documenta acá a medida que se implementa cada ruta (fase 3).
