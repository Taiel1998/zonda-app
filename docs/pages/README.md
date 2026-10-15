# Archivos para el dominio público del enlace del aviso

El enlace que manda el bot de Telegram es `https://taiel1998.github.io/a/{id}?red=&severidad=&servicio=&deteccion=&aviso=`: el id y lo mismo que dice el aviso. Para que Android lo abra directo en la app (App Link), el dominio tiene que publicar `/.well-known/assetlinks.json` con la huella del certificado con el que se firma el APK.

Estos archivos van en la raíz del repositorio `Taiel1998/Taiel1998.github.io` (GitHub Pages de usuario, que sirve desde la raíz del dominio; una página de proyecto no sirve porque cuelga de una subcarpeta):

| Archivo | Para qué |
|---|---|
| `.nojekyll` | Para que GitHub Pages publique la carpeta `.well-known` (Jekyll ignora las carpetas que empiezan con punto) |
| `.well-known/assetlinks.json` | Verificación del App Link: paquete de la app y huellas SHA-256 de los certificados de firma |
| `404.html` | Atiende cualquier `/a/{id}` cuando el enlace no abre la app (no está instalada, o lo abrió el navegador interno de Telegram): indica abrir Zonda y, en Android, muestra "Abrir en Zonda", que abre la alerta con un intent al paquete de la app |
| `index.html` | Página mínima del dominio |

## Huellas que tienen que estar en `assetlinks.json`

Hoy hay dos:

| Huella | Certificado |
|---|---|
| `E1:C5:9A:59:…:14:EF:F5` | El de debug de la máquina donde se compila la app (cada máquina tiene el suyo) |
| `D0:77:0B:5A:…:B6:10:4F` | La clave de release del equipo (decisión 28), con la que se firma el APK de la prueba con usuarios y de la demo |

Si otra máquina compila el APK de debug que se instala en un teléfono, hay que sumar su huella. Para obtenerla:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -J-Duser.language=en -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android
```

Copiar la línea `SHA256:` al arreglo `sha256_cert_fingerprints`. La huella de un APK ya firmado, sin abrir el keystore, sale de `apksigner verify --print-certs app-release.apk` (la línea `certificate SHA-256 digest`, en minúsculas y sin los dos puntos).

## Cómo comprobar la verificación en el teléfono

```powershell
adb shell pm get-app-links ar.edu.uade.zonda
```

Tiene que decir `verified` para `taiel1998.github.io`. Si dice `none` o `legacy_failure`, revisar que el archivo se sirva con `Content-Type: application/json` y que la huella coincida.
