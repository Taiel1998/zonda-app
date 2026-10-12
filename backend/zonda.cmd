@echo off
rem Comandos de administracion del servidor: zonda pair | zonda revoke <id> | zonda dispositivos
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" (
  echo Falta el entorno virtual: python -m venv .venv ^&^& .venv\Scripts\pip install -r requirements.txt
  exit /b 1
)
".venv\Scripts\python.exe" -m app.cli %*
