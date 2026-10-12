# Actualiza el registro de DuckDNS con la IP privada actual de la notebook, para que el nombre
# público de la API siga apuntando a esta máquina aunque el router le cambie la dirección.
# Se corre antes de iniciar la API (lo llama iniciar.ps1). Lee ZONDA_DOMINIO y DUCKDNS_TOKEN de .env.

$ErrorActionPreference = "Stop"
$carpeta = Split-Path -Parent $PSScriptRoot
$env_path = Join-Path $carpeta ".env"
if (-not (Test-Path $env_path)) { throw "Falta backend\.env (copiar .env.ejemplo y completarlo)" }

$vars = @{}
Get-Content $env_path | ForEach-Object {
    if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*)\s*$') { $vars[$matches[1]] = $matches[2] }
}
$dominio = $vars["ZONDA_DOMINIO"]
$token = $vars["DUCKDNS_TOKEN"]
if (-not $dominio -or -not $token) { throw "ZONDA_DOMINIO o DUCKDNS_TOKEN vacíos en .env" }
$subdominio = $dominio -replace '\.duckdns\.org$', ''

# IP privada de la interfaz que tiene salida (wifi o hotspot), sin loopback ni direcciones automáticas.
$ip = (Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.PrefixOrigin -ne "WellKnown" } |
    Sort-Object InterfaceMetric | Select-Object -First 1).IPAddress
if (-not $ip) { throw "No se encontró una IP privada" }

$respuesta = Invoke-RestMethod "https://www.duckdns.org/update?domains=$subdominio&token=$token&ip=$ip"
Write-Host "DuckDNS: $dominio -> $ip ($respuesta)"
