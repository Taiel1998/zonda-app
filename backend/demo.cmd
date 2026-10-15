@echo off
rem Levanta la demo con HTTPS: DuckDNS, la API con el bot de Telegram y Caddy (scripts\demo.ps1).
rem Se corta con Ctrl+C. ExecutionPolicy Bypass porque Windows no deja correr scripts .ps1 por defecto.
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo.ps1"
