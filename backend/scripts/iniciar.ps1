# Inicia la API en modo desarrollo: actualiza DuckDNS (si hay .env) y levanta uvicorn en 127.0.0.1:8000.
# Caddy se inicia aparte con: caddy run --envfile .env   (desde la carpeta backend)
# Para el emulador, la app llega a la API por http://10.0.2.2:8000 sin pasar por Caddy.

$ErrorActionPreference = "Stop"
$carpeta = Split-Path -Parent $PSScriptRoot
Set-Location $carpeta

if (Test-Path (Join-Path $carpeta ".env")) {
    try { & (Join-Path $PSScriptRoot "actualizar_duckdns.ps1") } catch { Write-Warning "DuckDNS no se actualizó: $_" }
}

$python = Join-Path $carpeta ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) { throw "Falta el entorno virtual: python -m venv .venv; .venv\Scripts\pip install -r requirements.txt" }

& $python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
