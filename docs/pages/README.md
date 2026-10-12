# Archivos para el dominio público del enlace del aviso

El enlace que manda el bot de Telegram es `https://taiel1998.github.io/a/{id}`. Para que Android lo abra directo en la app (App Link), el dominio tiene que publicar `/.well-known/assetlinks.json` con la huella del certificado con el que se firma el APK.

Estos archivos van en la raíz del repositorio `Taiel1998/Taiel1998.github.io` (GitHub Pages de usuario, que sirve desde la raíz del dominio; una página de proyecto no sirve porque cuelga de una subcarpeta):

| Archivo | Para qué |
|---|---|
| `.nojekyll` | Para que GitHub Pages publique la carpeta `.well-known` (Jekyll ignora las carpetas que empiezan con punto) |
| `.well-known/assetlinks.json` | Verificación del App Link: paquete de la app y huellas SHA-256 de los certificados de firma |
| `404.html` | Atiende cualquier `/a/{id}` cuando la app no está instalada: indica abrir Zonda |
| `index.html` | Página mínima del dominio |

## Huellas que tienen que estar en `assetlinks.json`

Una por cada máquina que compile el APK que se instala en el teléfono de la demo, porque el certificado de debug es distinto en cada máquina. Para obtener la huella de una máquina:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -J-Duser.language=en -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android
```

Copiar la línea `SHA256:` al arreglo `sha256_cert_fingerprints`. Cuando haya un APK firmado para la entrega, agregar también la huella de ese keystore.

## Cómo comprobar la verificación en el teléfono

```powershell
adb shell pm get-app-links ar.edu.uade.zonda
```

Tiene que decir `verified` para `taiel1998.github.io`. Si dice `none` o `legacy_failure`, revisar que el archivo se sirva con `Content-Type: application/json` y que la huella coincida.
