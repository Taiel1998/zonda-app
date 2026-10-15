# Inicia la API sola para desarrollo: uvicorn en 127.0.0.1:8000 con --reload, sin HTTPS.
# El emulador llega por http://10.0.2.2:8000 y un teléfono por USB por http://127.0.0.1:8000 con
# adb reverse tcp:8000 tcp:8000 (el build de debug permite esas dos direcciones sin cifrar).
# Para la demo con HTTPS, DuckDNS y Caddy está demo.cmd.

$ErrorActionPreference = "Stop"
$carpeta = Split-Path -Parent $PSScriptRoot
Set-Location $carpeta

$python = Join-Path $carpeta ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) { throw "Falta el entorno virtual: python -m venv .venv; .venv\Scripts\pip install -r requirements.txt" }

& $python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
