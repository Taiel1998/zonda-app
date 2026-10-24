package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.CodigoPersona
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.DetallesConexion
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.EstadoSistema
import ar.edu.uade.zonda.domain.model.EstadoTelegram
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.RedPareada
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.VinculoTelegram
import kotlinx.coroutines.flow.Flow

/** Redes pareadas con este teléfono, su red activa y lo que cada una guarda en el servidor (RF05 y RF06). */
interface RedesRepository {

    fun observarRedes(): Flow<List<Red>>

    /** La red activa, o null si todavía no hay ninguna pareada (primer inicio). */
    fun observarRedActiva(): Flow<Red?>

    suspend fun activar(redId: String)

    /**
     * Verifica que el servidor responda y que su certificado sea válido (`GET /salud`, sin token), antes
     * de mandar el código, que es de un solo uso. Lanza [ar.edu.uade.zonda.domain.model.ErrorZonda].
     */
    suspend fun verificarServidor(urlBase: String)

    /**
     * Verifica el servidor, canjea el código por un token (`POST /pareo`), guarda el token en el
     * almacenamiento seguro y deja la red pareada y activa. Devuelve también cuántas alertas tiene
     * para descargar. Lanza [ar.edu.uade.zonda.domain.model.ErrorZonda].
     */
    suspend fun parear(datos: DatosPareo, nombreDispositivo: String): RedPareada

    /** Revoca el token en el servidor (`DELETE /dispositivos/actual`) y borra el token y todos los datos de la red. */
    suspend fun quitarDispositivo(redId: String)

    /** El servidor respondió 401 (o volvió a aceptar el token): la app pide un pareo nuevo sin borrar la caché. */
    suspend fun marcarAccesoVencido(redId: String, vencido: Boolean)

    /** Borrar datos locales: la red queda pareada pero sin cursor ni hora; la sincronización siguiente baja todo. */
    suspend fun reiniciarSincronizacion(redId: String)

    /** Severidad por tipo de ataque elegida para la red. Vale para todos los teléfonos pareados a ella. */
    fun observarSeveridades(redId: String): Flow<SeveridadPorTipo>

    /** Guarda las severidades en el teléfono. El envío al servidor va por la cola de acciones. */
    suspend fun guardarSeveridadesLocal(redId: String, severidades: SeveridadPorTipo)

    /**
     * Pide al servidor el código de un uso para vincular el chat de Telegram (`POST /telegram/vinculo`),
     * con los [avisos] de este teléfono para que el bot los use desde el primer aviso.
     */
    suspend fun pedirVinculoTelegram(redId: String, avisos: PreferenciasAvisos): VinculoTelegram

    /** Pregunta al servidor si hay un chat vinculado (`GET /telegram`) y deja la red al día. */
    suspend fun consultarTelegram(redId: String): EstadoTelegram

    /** El chat deja de recibir los avisos de la red (`DELETE /telegram/vinculo`). */
    suspend fun desvincularTelegram(redId: String)

    /** El bot manda un aviso de prueba al chat vinculado (`POST /telegram/prueba`). */
    suspend fun enviarAvisoDePrueba(redId: String)

    /** Cómo avisa Telegram cada severidad en este teléfono, para el bot de la red (`PUT /telegram/avisos`). */
    suspend fun enviarAvisosTelegram(redId: String, avisos: PreferenciasAvisos)

    /** Catálogo de servicios y nombres de equipos de la red (`GET /catalogo`), el último guardado. */
    fun observarCatalogo(redId: String): Flow<Catalogo>

    /** Protocolo, certificado y vencimiento del acceso, leídos de una llamada a `GET /salud` (Detalles técnicos). */
    suspend fun detallesDeConexion(redId: String): DetallesConexion

    /**
     * El sensor, el análisis en vivo y el servidor de la red (`GET /salud` con token, API 0.7.0), para la
     * tarjeta "Estado del sistema". No lanza: si el servidor no contesta, queda "No responde" y lo demás
     * sin datos.
     */
    suspend fun estadoDelSistema(redId: String): EstadoSistema

    // ---- Personas, equipos y registro de cambios (decisiones 39 a 41) ----

    /** La gente de la red, la última que dijo el servidor, en el orden en que se sumó. */
    fun observarPersonas(redId: String): Flow<List<Persona>>

    /** Los equipos de la red con nombre en el catálogo y su importancia. */
    fun observarEquipos(redId: String): Flow<List<EquipoDeLaRed>>

    /** El registro de cambios de la red, de lo más nuevo a lo más viejo. */
    fun observarRegistro(redId: String): Flow<List<CambioRegistro>>

    /** Un código de un uso para sumar a [nombre] con [perfil] (`POST /personas/codigo`). Necesita conexión. */
    suspend fun crearCodigoPersona(redId: String, nombre: String, perfil: Perfil): CodigoPersona

    /** "Hacer administrador" o "Pasar a analista" (`PUT /personas/{id}/perfil`). Necesita conexión. */
    suspend fun cambiarPerfil(redId: String, personaId: String, perfil: Perfil)

    /** "Quitar del equipo" (`DELETE /personas/{id}`). Necesita conexión. */
    suspend fun quitarPersona(redId: String, personaId: String)

    /** Vuelve a pedir la gente, los equipos y el registro al servidor (después de un cambio). */
    suspend fun actualizarPersonasYRegistro(redId: String)

    /** Guarda la importancia de un equipo en el teléfono. El envío al servidor va por la cola de acciones. */
    suspend fun guardarImportanciaLocal(redId: String, ip: String, importancia: Importancia)
}

