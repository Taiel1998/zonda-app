# Prueba el HTTPS de la API con openssl s_client (RNF04; docs/decisiones.md, decisión 26). Con la
# demo levantada (demo.cmd), en otra terminal desde la carpeta backend:
#     powershell -ExecutionPolicy Bypass -File scripts\probar_tls.ps1
# Comprueba que Caddy acepte TLS 1.3 y 1.2 y rechace 1.1 y 1.0, que el certificado sea de Let's
# Encrypt y valga para el nombre, y que quien entra por la IP reciba un certificado que no coincide.
# Usa el nombre de ZONDA_DOMINIO en .env. Si la red no resuelve el nombre (riesgo 2), pasale la IP
# de la notebook: -Ip 192.168.1.48. Termina con código 1 si alguna prueba falla.

param(
    [string]$Dominio,
    [string]$Ip
)

$ErrorActionPreference = "Stop"
trap { Write-Host "No se pudo probar: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
$carpeta = Split-Path -Parent $PSScriptRoot

if (-not $Dominio) {
    $archivoEnv = Join-Path $carpeta ".env"
    if (Test-Path $archivoEnv) {
        $linea = Get-Content $archivoEnv -Encoding UTF8 | Where-Object { $_ -match '^\s*ZONDA_DOMINIO\s*=\s*(\S+)' } | Select-Object -First 1
        if ($linea -match '=\s*(\S+)') { $Dominio = $matches[1] }
    }
    if (-not $Dominio) { throw "Falta ZONDA_DOMINIO en backend\.env (o pasalo con -Dominio)" }
}
if (-not $Ip) {
    try {
        $Ip = ([System.Net.Dns]::GetHostAddresses($Dominio) | Where-Object { $_.AddressFamily -eq "InterNetwork" } | Select-Object -First 1).IPAddressToString
    } catch {
        throw "Esta red no resuelve $Dominio (¿riesgo 2?). Pasale la IP de la notebook con -Ip"
    }
}

# El openssl que viene con Git para Windows, si no hay otro en el PATH.
$openssl = (Get-Command openssl.exe -ErrorAction SilentlyContinue).Source
if (-not $openssl) {
    $openssl = @("$env:ProgramFiles\Git\mingw64\bin\openssl.exe", "$env:ProgramFiles\Git\usr\bin\openssl.exe") |
        Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $openssl) { throw "No encontré openssl: viene con Git para Windows" }

# Corre openssl con la entrada cerrada (s_client termina sin mandar nada) y devuelve todo lo que escribió.
function Ejecutar([string]$argumentos, [string]$entrada = "") {
    $info = New-Object System.Diagnostics.ProcessStartInfo $openssl, $argumentos
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $proceso = [System.Diagnostics.Process]::Start($info)
    $salida = $proceso.StandardOutput.ReadToEndAsync()
    $errores = $proceso.StandardError.ReadToEndAsync()
    $proceso.StandardInput.Write($entrada)
    $proceso.StandardInput.Close()
    if (-not $proceso.WaitForExit(20000)) { $proceso.Kill() }
    return $salida.Result + $errores.Result
}

# Emisor, vencimiento y nombres del certificado que mandó el servidor (s_client lo escribe en PEM).
# Se leen los nombres alternativos y no el sujeto: hay certificados que traen el nombre solo ahí.
function DatosDelCertificado([string]$salida) {
    $pem = [regex]::Match($salida, "-----BEGIN CERTIFICATE-----[\s\S]+?-----END CERTIFICATE-----").Value
    if (-not $pem) { return $null }
    $texto = Ejecutar "x509 -noout -issuer -enddate -ext subjectAltName" $pem
    return @{
        Emisor = if ($texto -match "issuer=(.+)") { $matches[1].Trim() } else { "?" }
        Vence = if ($texto -match "notAfter=(.+)") { $matches[1].Trim() } else { "?" }
        Nombres = if ($texto -match "(DNS|IP Address):[^\r\n]+") { ($matches[0] -replace "DNS:", "").Trim() } else { "?" }
    }
}

$fallas = 0
function Anotar([string]$prueba, [bool]$paso, [string]$detalle) {
    if ($paso) { Write-Host ("  PASA   {0,-37} {1}" -f $prueba, $detalle) -ForegroundColor Green }
    else { Write-Host ("  FALLA  {0,-37} {1}" -f $prueba, $detalle) -ForegroundColor Red; $script:fallas++ }
}

$version = (Ejecutar "version").Trim()
Write-Host "Probando https://$Dominio en $Ip con $version"
$destino = "-connect ${Ip}:443 -servername $Dominio"

# 1 y 2. TLS 1.3 y 1.2 se aceptan.
foreach ($prueba in @(@{ Opcion = "-tls1_3"; Nombre = "TLSv1.3" }, @{ Opcion = "-tls1_2"; Nombre = "TLSv1.2" })) {
    $salida = Ejecutar "s_client $destino $($prueba.Opcion) -brief"
    $acepto = $salida -match "Protocol version: $([regex]::Escape($prueba.Nombre))"
    $suite = if ($salida -match "Ciphersuite: (\S+)") { $matches[1] } else { "sin conexión" }
    Anotar "$($prueba.Nombre -replace 'v', ' ') se acepta" $acepto $suite
}

# 3 y 4. TLS 1.1 y 1.0 se rechazan. Con el nivel de seguridad de siempre, openssl 3 ni siquiera las
# ofrece ("no protocols available") y la prueba no le preguntaría nada al servidor: el nivel 0 hace
# que las ofrezca, y entonces el que dice que no es Caddy, con la alerta "protocol version" (70).
foreach ($prueba in @(@{ Opcion = "-tls1_1"; Nombre = "TLS 1.1" }, @{ Opcion = "-tls1"; Nombre = "TLS 1.0" })) {
    $salida = Ejecutar "s_client $destino $($prueba.Opcion) -cipher DEFAULT@SECLEVEL=0 -brief"
    if ($salida -match "no protocols available") {
        Anotar "$($prueba.Nombre) se rechaza" $false "no se pudo probar: este openssl no ofrece $($prueba.Nombre)"
    } elseif ($salida -match "alert protocol version|alert number 70") {
        Anotar "$($prueba.Nombre) se rechaza" $true "el servidor respondió con la alerta protocol version (70)"
    } elseif ($salida -match "CONNECTION ESTABLISHED") {
        Anotar "$($prueba.Nombre) se rechaza" $false "el servidor aceptó la conexión"
    } else {
        Anotar "$($prueba.Nombre) se rechaza" $false "respuesta inesperada: $(($salida -split "`n" | Select-Object -Last 1).Trim())"
    }
}

# 5. El certificado: de Let's Encrypt, válido para el nombre y con la cadena completa.
$salida = Ejecutar "s_client $destino -verify_hostname $Dominio"
$cert = DatosDelCertificado $salida
if ($cert) {
    $verificado = $salida -match "Verify return code: 0 \(ok\)"
    $motivo = if ($salida -match "Verify return code: (.+)") { $matches[1].Trim() } else { "?" }
    Anotar "Certificado de Let's Encrypt" ($cert.Emisor -match "O\s*=\s*Let's Encrypt") "$($cert.Emisor), vence $($cert.Vence)"
    Anotar "Válido para $Dominio" $verificado $(if ($verificado) { "cadena y nombre verificados ($($cert.Nombres))" } else { $motivo })
} else {
    Anotar "Certificado de Let's Encrypt" $false "el servidor no mandó un certificado"
}

# 6. Por la IP, sin el nombre: Caddy manda el certificado del nombre (default_sni) y no coincide con
# la dirección, así que un cliente que verifica, como Android, corta antes de mandar nada (RNF04).
$salida = Ejecutar "s_client -connect ${Ip}:443 -verify_ip $Ip"
$cert = DatosDelCertificado $salida
$para = if ($cert) { $cert.Nombres } else { "nadie (el servidor no mandó certificado)" }
Anotar "Por la IP el certificado no coincide" ($salida -match "IP address mismatch") "es para $para, no para $Ip"

Write-Host ""
if ($fallas -eq 0) { Write-Host "Todas las pruebas pasaron." -ForegroundColor Green; exit 0 }
Write-Host "$fallas prueba(s) fallaron." -ForegroundColor Red
exit 1
