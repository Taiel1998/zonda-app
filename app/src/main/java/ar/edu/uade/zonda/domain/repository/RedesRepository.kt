package ar.edu.uade.zonda.domain.repository

import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.Red
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
     * almacenamiento seguro y deja la red pareada y activa. Lanza [ar.edu.uade.zonda.domain.model.ErrorZonda].
     */
    suspend fun parear(datos: DatosPareo, nombreDispositivo: String): Red

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

    /** Pide al servidor el código de un uso para vincular el chat de Telegram (`POST /telegram/vinculo`). */
    suspend fun pedirVinculoTelegram(redId: String): VinculoTelegram

    suspend fun marcarTelegramVinculado(redId: String, vinculado: Boolean)

    /** Catálogo de servicios y nombres de equipos de la red (`GET /catalogo`), el último guardado. */
    fun observarCatalogo(redId: String): Flow<Catalogo>
}
