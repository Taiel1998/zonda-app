# Levanta todo lo de la demo con un solo comando (docs/decisiones.md, decisión 26):
#   1. Actualiza DuckDNS con la IP de la notebook en esta red (scripts\actualizar_duckdns.ps1).
#   2. Arranca la API con el bot de Telegram: uvicorn en 127.0.0.1:8000, un solo proceso y sin
#      --reload, porque con dos procesos los dos leerían los mensajes del bot.
#   3. Arranca Caddy, que la publica con HTTPS en https://<ZONDA_DOMINIO>. La primera vez pide el
#      certificado a Let's Encrypt; después lo renueva solo.
#   4. Comprueba que Caddy responda con un certificado válido y que el nombre apunte a esta notebook.
# Se arranca con demo.cmd desde la carpeta backend y se corta con Ctrl+C, que cierra la API y Caddy.

$ErrorActionPreference = "Stop"
# Un error corta la demo con un mensaje corto, sin el detalle técnico de PowerShell. Antes corre el
# finally de abajo, que cierra la API y Caddy.
trap { Write-Host "No se pudo levantar la demo: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
$carpeta = Split-Path -Parent $PSScriptRoot
Set-Location $carpeta

# --- Lo que hace falta antes de arrancar ---
$archivoEnv = Join-Path $carpeta ".env"
if (-not (Test-Path $archivoEnv)) { throw "Falta backend\.env (copiá .env.ejemplo como .env y completalo)" }
$vars = @{}
# Como Caddy y python-dotenv: KEY=valor, sin las líneas de comentario ni lo que sigue a " #".
Get-Content $archivoEnv -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$') { $vars[$matches[1]] = $matches[2] -replace '\s+#.*$', '' }
}
$dominio = $vars["ZONDA_DOMINIO"]
if (-not $dominio -or -not $vars["DUCKDNS_TOKEN"]) { throw "Faltan ZONDA_DOMINIO o DUCKDNS_TOKEN en backend\.env (ver .env.ejemplo)" }
if ($dominio -notmatch '^[a-z0-9-]+\.duckdns\.org$') { throw "ZONDA_DOMINIO tiene que ser el nombre completo, por ejemplo zonda-equipo.duckdns.org" }
# El QR lleva esta dirección: si no está en .env, la API la arma con el nombre (app/config.py).
$url = $vars["ZONDA_URL_PUBLICA"]
if ($url -and $url -ne "https://$dominio") { throw "En .env, ZONDA_URL_PUBLICA dice ${url}: para la demo tiene que ser https://$dominio o no estar" }
if (-not $vars["TELEGRAM_BOT_TOKEN"]) { Write-Warning "Sin TELEGRAM_BOT_TOKEN en .env: la API anda, pero sin el bot ni los avisos de Telegram" }

$python = Join-Path $carpeta ".venv\Scripts\python.exe"
if (-not (Test-Path $python)) { throw "Falta el entorno virtual: python -m venv .venv; .venv\Scripts\pip install -r requirements.txt" }
$caddyExe = Join-Path $carpeta "caddy\caddy.exe"
if (-not (Test-Path $caddyExe)) { throw "Falta caddy\caddy.exe (ver la publicación con HTTPS en backend\README.md)" }

foreach ($puerto in 8000, 443) {
    $ocupado = Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($ocupado) {
        $proceso = (Get-Process -Id $ocupado.OwningProcess -ErrorAction SilentlyContinue).ProcessName
        throw "El puerto $puerto ya está en uso ($proceso, PID $($ocupado.OwningProcess)): cerralo y volvé a correr demo.cmd"
    }
}

# --- 1. DuckDNS ---
try {
    $ip = & (Join-Path $PSScriptRoot "actualizar_duckdns.ps1")
} catch {
    Write-Warning "DuckDNS no se actualizó ($($_.Exception.Message)). Si la IP de la notebook no cambió, el nombre sigue sirviendo."
    $ip = $null
}

# --- 2 y 3. La API y Caddy, en esta misma ventana ---
Write-Host "Arrancando la API (http://127.0.0.1:8000, solo dentro de la notebook)..."
$api = Start-Process -FilePath $python -ArgumentList "-m uvicorn app.main:app --host 127.0.0.1 --port 8000" -WorkingDirectory $carpeta -NoNewWindow -PassThru
# Lo que escribe Caddy queda en caddy\caddy.log, para que en la ventana se lean los pedidos a la API.
$registroCaddy = Join-Path $carpeta "caddy\caddy.log"
Write-Host "Arrancando Caddy (https://$dominio). Si Windows pregunta si Caddy puede usar la red, aceptá también en redes públicas: el hotspot suele quedar como pública."
$caddy = Start-Process -FilePath $caddyExe -ArgumentList "run --config Caddyfile --adapter caddyfile --envfile .env" -WorkingDirectory $carpeta -NoNewWindow -PassThru -RedirectStandardError $registroCaddy

try {
    # --- 4. Comprobaciones ---
    # Primero Caddy, sin depender del DNS: curl se conecta a esta misma notebook pero pide el nombre,
    # así se ve si el certificado ya está y es válido. La primera vez Let's Encrypt tarda un par de minutos.
    Write-Host "Esperando el certificado y la respuesta de la API..."
    $listo = $false
    for ($i = 0; $i -lt 90 -and -not $listo; $i++) {
        if ($api.HasExited -or $caddy.HasExited) { break }
        Start-Sleep -Seconds 2
        $salud = & curl.exe -s --max-time 5 --ssl-revoke-best-effort --resolve "${dominio}:443:127.0.0.1" "https://$dominio/salud"
        $listo = ($LASTEXITCODE -eq 0) -and ("$salud" -match '"version"')
    }
    if (-not $listo) {
        if ($api.HasExited) { throw "La API se cerró al arrancar (¿quedó otra abierta, o falta algo en .env?)" }
        Write-Warning "Caddy todavía no responde con el certificado. Las últimas líneas de caddy\caddy.log:"
        Get-Content $registroCaddy -Tail 5 | ForEach-Object { Write-Host "    $_" }
        throw "Sin HTTPS no hay demo: revisá DUCKDNS_TOKEN y la conexión a internet, y volvé a correr demo.cmd"
    }

    # Después el nombre: el teléfono lo resuelve con el DNS de la red, así que tiene que dar esta notebook.
    # DuckDNS tarda unos segundos en cambiar la dirección.
    $resuelta = $null
    for ($i = 0; $i -lt 10; $i++) {
        try {
            $resuelta = ([System.Net.Dns]::GetHostAddresses($dominio) | Where-Object { $_.AddressFamily -eq "InterNetwork" } | Select-Object -First 1).IPAddressToString
        } catch {
            $resuelta = $null
        }
        if (-not $ip -or $resuelta -eq $ip) { break }
        Start-Sleep -Seconds 2
    }
    Write-Host ""
    Write-Host "Listo: https://$dominio/salud responde con HTTPS." -ForegroundColor Green
    if (-not $resuelta) {
        Write-Warning "Esta red no resuelve ${dominio}: puede ser que bloquee los nombres que apuntan a una IP privada (riesgo 2). Probá con el hotspot del teléfono."
    } elseif ($ip -and $resuelta -ne $ip) {
        Write-Warning "$dominio apunta a $resuelta y esta notebook es $ip. DuckDNS tarda unos segundos: si sigue así, corré scripts\actualizar_duckdns.ps1."
    } else {
        Write-Host "El nombre apunta a esta notebook ($resuelta)."
    }
    Write-Host "Para parear un teléfono, en otra terminal: .\zonda.cmd pair"
    Write-Host "Para cortar todo: Ctrl+C"
    Write-Host ""

    while (-not $api.HasExited -and -not $caddy.HasExited) { Start-Sleep -Seconds 1 }
    if ($api.HasExited) { Write-Warning "La API se cerró." } else { Write-Warning "Caddy se cerró: ver caddy\caddy.log." }
} finally {
    # Con Ctrl+C la API y Caddy también reciben el corte y se cierran solos; lo que siga abierto a los
    # 5 segundos se cierra a la fuerza. El python del entorno virtual lanza otro proceso por debajo,
    # por eso se cierra el árbol entero.
    foreach ($proceso in @($api, $caddy)) {
        if ($proceso -and -not $proceso.WaitForExit(5000)) { & taskkill.exe /PID $proceso.Id /T /F | Out-Null }
    }
    Write-Host "La API y Caddy quedaron cerrados."
}
# Se llega acá solo si la API o Caddy se cerraron solos (Ctrl+C corta antes).
exit 1
