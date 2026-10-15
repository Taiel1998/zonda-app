# Actualiza el registro de DuckDNS con la IP privada actual de la notebook, para que el nombre de la
# API siga apuntando a esta máquina aunque el router o el hotspot le den otra dirección.
# Lo llama demo.cmd al arrancar; si la IP cambia en medio de la demo, se puede correr solo:
#     powershell -ExecutionPolicy Bypass -File scripts\actualizar_duckdns.ps1
# Lee ZONDA_DOMINIO y DUCKDNS_TOKEN de .env y devuelve la IP que quedó registrada.

$ErrorActionPreference = "Stop"
$carpeta = Split-Path -Parent $PSScriptRoot
$archivoEnv = Join-Path $carpeta ".env"
if (-not (Test-Path $archivoEnv)) { throw "Falta backend\.env (copiá .env.ejemplo como .env y completalo)" }

$vars = @{}
Get-Content $archivoEnv -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$') { $vars[$matches[1]] = $matches[2] }
}
$dominio = $vars["ZONDA_DOMINIO"]
$token = $vars["DUCKDNS_TOKEN"]
if (-not $dominio -or -not $token) { throw "Faltan ZONDA_DOMINIO o DUCKDNS_TOKEN en .env" }
$subdominio = $dominio -replace '\.duckdns\.org$', ''

# La IP de la interfaz por la que sale el tráfico a internet (el wifi o el hotspot), elegida por la
# ruta por defecto. Así no se toma la red interna de VirtualBox (192.168.56.1) ni un adaptador
# desconectado, que también tienen IP.
$ruta = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix "0.0.0.0/0" -ErrorAction SilentlyContinue |
    Sort-Object { $_.RouteMetric + (Get-NetIPInterface -InterfaceIndex $_.InterfaceIndex -AddressFamily IPv4).InterfaceMetric } |
    Select-Object -First 1
if (-not $ruta) { throw "La notebook no tiene salida a internet: conectala al wifi o al hotspot" }
$ip = (Get-NetIPAddress -InterfaceIndex $ruta.InterfaceIndex -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "169.254.*" } | Select-Object -First 1).IPAddress
if (-not $ip) { throw "La interfaz $($ruta.InterfaceAlias) no tiene una IP privada" }

# El token va en la dirección (así es la API de DuckDNS), por eso el error se arma sin ella.
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
try {
    $respuesta = (Invoke-RestMethod -TimeoutSec 15 "https://www.duckdns.org/update?domains=$subdominio&token=$token&ip=$ip").ToString().Trim()
} catch {
    throw "DuckDNS no respondió: $($_.Exception.GetType().Name)"
}
if ($respuesta -ne "OK") { throw "DuckDNS respondió ${respuesta}: revisá ZONDA_DOMINIO y DUCKDNS_TOKEN en .env" }
Write-Host "DuckDNS: $dominio -> $ip ($($ruta.InterfaceAlias))"
$ip
