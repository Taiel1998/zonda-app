# Zonda

App Android de alertas de anomalías de red para PyMEs. Es el cliente móvil de una plataforma de detección desarrollada por el equipo: recibe las alertas de tráfico interno anómalo, muestra el estado de la red con un semáforo, permite recorrer las alertas, ver el detalle con el veredicto de tres métodos de detección y registrar la revisión (ataque real o falso positivo), todo con soporte sin conexión. El aviso inmediato llega por un bot de Telegram.

Trabajo Práctico Obligatorio de Desarrollo de Aplicaciones I (UADE, 2.º cuatrimestre 2026). Integrantes: Melinda Sellés, Rodrigo Nicolás Larrart y Taiel Vinograd.

**Estado:** Etapa 2 en construcción. Este README se completa fase a fase; lo que todavía no está dice "pendiente".

## Qué hace (requisitos comprometidos)

| Requisito | Descripción | Estado |
|---|---|---|
| RF01 | Estado de la red: semáforo, alertas sin revisar por severidad, hora de la última sincronización | Pendiente |
| RF02 | Listar y filtrar alertas (estado de revisión, severidad, período, servicio, equipo destino, método) | Pendiente |
| RF03 | Detalle de una alerta con el veredicto de cada método, también desde el enlace del aviso | Pendiente (enlace en prueba) |
| RF04 | Revisar una alerta: ataque real o falso positivo, con motivo y comentario, aun sin conexión | Pendiente |
| RF05 | Primer inicio guiado y pareo con la red por código QR | Pendiente |
| RF06 | Varias redes y severidad por tipo de ataque | Pendiente |
| RF07 | Resumen de actividad de 7 o 30 días, compartible como imagen | Pendiente |

## Cómo está organizado el repositorio

```
app/        La app Android (Kotlin, Jetpack Compose)
backend/    La API de alertas (FastAPI + SQLite), el bot de Telegram y la configuración de Caddy
docs/       Decisiones de diseño, registro de uso de IA y archivos del dominio del enlace del aviso
.github/    Integración continua y plantillas de issue y pull request
```

### La app

Arquitectura MVVM con las tres capas de Clean Architecture, en un solo módulo con un paquete por capa:

```
ar.edu.uade.zonda
├── ZondaApp.kt          Application: crea el contenedor de dependencias
├── MainActivity.kt      única Activity; arma el tema y la navegación
├── di/                  AppContainer, contenedor manual de dependencias
├── domain/              modelos, reglas y casos de uso en Kotlin puro (sin Android)
├── data/                Room, Retrofit, Keystore, DataStore, mappers y repositorios
├── sync/                sincronización en segundo plano y cola de acciones pendientes
└── ui/                  pantallas en Compose, ViewModels, navegación, tema y componentes
```

Los datos circulan así: la pantalla emite eventos, el ViewModel llama a un caso de uso, el caso de uso habla con la interfaz del repositorio, y la implementación decide entre la base local (Room) y la API (Retrofit). La base local es la fuente de verdad de la interfaz: la red solo la actualiza.

## Cómo ejecutarla

### App

1. Abrir la carpeta del repositorio con Android Studio (2026.1 o más nuevo) y esperar la sincronización de Gradle.
2. Elegir un emulador o un teléfono con Android 8.0 o superior y tocar Run.

Desde la terminal:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
```

### Backend

Ver [backend/README.md](backend/README.md). En resumen: crear un entorno virtual de Python 3.12, instalar `requirements.txt` y levantar `uvicorn`. Para publicarlo con HTTPS se usa Caddy con un nombre de DuckDNS.

## Tecnologías

| Tecnología | Para qué | Por qué |
|---|---|---|
| Kotlin + Jetpack Compose (Material 3) | Toda la interfaz | Stack de la materia; una sola Activity y pantallas declarativas |
| Navigation Compose | Barra inferior, pila de pantallas y enlace del aviso | Resuelve el App Link sin código propio |
| ViewModel + StateFlow | Estado de cada pantalla | Sobrevive a la rotación y separa la lógica de la pantalla |
| Room | Caché local de alertas, redes y acciones pendientes | Base del funcionamiento sin conexión; consultas reactivas con Flow |
| Retrofit + OkHttp | Cliente de la API | Interfaz declarativa, interceptor para el token |
| DataStore | Preferencias | Reemplazo moderno de SharedPreferences |
| Android Keystore | Token de cada red y clave de la base | El secreto no sale del hardware seguro del teléfono |
| WorkManager | Sincronización cada 15 minutos | Respeta las restricciones de batería de Android |
| CameraX + ML Kit | Lectura del QR de pareo | Lectura de códigos sin servidor |
| FastAPI + SQLite | API de alertas | Mínima, en Python |
| Caddy + Let's Encrypt + DuckDNS | HTTPS del servidor | Certificado gratuito que Android reconoce |
| API de bots de Telegram | Aviso inmediato | Gratuita, recomendada por la cátedra |

Las dependencias y sus versiones están en `gradle/libs.versions.toml`.

## Decisiones relevantes

Están en [docs/decisiones.md](docs/decisiones.md). Las principales:

1. Kotlin y Jetpack Compose en lugar de React Native (la Etapa 1 proponía React Native).
2. Contenedor manual de dependencias en lugar de Hilt.
3. Una alerta es un episodio (flujos agrupados por origen y destino), no un flujo de red.
4. Cada alerta trae un tipo de ataque que define la severidad; ninguna alerta se oculta.
5. Sin fijación de certificado: alcanza con la verificación de Android.
6. Las alertas de muestra salen de CICIDS2017; sus fechas se corren para la demo.

## Forma de trabajo

- GitHub Flow: `main` siempre compila; todo cambio entra por pull request con revisión de otro integrante.
- Ramas `feature/tema` y `fix/tema`; commits chicos con prefijos `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `ci:`.
- GitHub Actions compila la app y corre los tests en cada pull request.
- Ningún token ni dirección de servidor en el repositorio: van en `backend/.env`, que está en `.gitignore`.
- Uso de asistentes de IA: ver [docs/prompts/README.md](docs/prompts/README.md).

## Limitaciones conocidas

Se completan al cierre de la Etapa 2.

## Licencia

MIT. Ver [LICENSE](LICENSE).
