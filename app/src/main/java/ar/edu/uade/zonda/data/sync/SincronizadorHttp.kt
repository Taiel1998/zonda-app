package ar.edu.uade.zonda.data.sync

import ar.edu.uade.zonda.data.local.ZondaDatabase
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aDto
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.mapper.aEntidades
import ar.edu.uade.zonda.data.mapper.aRevisionEntrada
import ar.edu.uade.zonda.data.mapper.aSeveridadPorTipo
import ar.edu.uade.zonda.data.remote.OperacionHttp
import ar.edu.uade.zonda.data.remote.ProveedorDeApi
import ar.edu.uade.zonda.data.remote.ZondaApi
import ar.edu.uade.zonda.data.remote.conErroresTraducidos
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * La sincronización de una red (informe, sección 14.2; docs/decisiones.md, decisión 13):
 *
 * 1. Envía la cola de acciones pendientes en orden de creación. Cada sincronización es un intento;
 *    a los cinco fallos la acción queda en ERROR y espera un reintento manual. Un 409 la deja en
 *    CONFLICTO con la revisión ajena, sin frenar al resto. Un 401 o la falta de conexión detienen todo.
 * 2. Baja las alertas nuevas o modificadas desde el cursor, página por página, guardando el cursor
 *    después de cada una. Un 410 (el servidor se reinstaló) dispara una sincronización completa que
 *    conserva lo guardado y lo pendiente.
 * 3. Actualiza el catálogo y las severidades de la red, purga las alertas de más de 30 días (RNF03)
 *    y registra la hora de la sincronización.
 */
class SincronizadorHttp(
    private val base: ZondaDatabase,
    private val apis: ProveedorDeApi,
    private val reloj: () -> Instant = Instant::now,
    private val diasDeCache: Long = DIAS_DE_CACHE,
) : Sincronizador {

    private val alertasDao get() = base.alertasDao()
    private val redesDao get() = base.redesDao()
    private val accionesDao get() = base.accionesPendientesDao()

    /**
     * Una sincronización por vez: si se piden dos juntas (al volver la red desde dos pantallas, por
     * ejemplo), la segunda espera a que termine la primera y no manda la misma acción dos veces.
     */
    private val candado = Mutex()

    override suspend fun sincronizar(redId: String): ResultadoSincronizacion = candado.withLock {
        try {
            sincronizarAhora(redId)
        } catch (e: ErrorZonda.NoAutorizado) {
            // El token venció o fue revocado: la red queda marcada y la app pide un pareo nuevo (RNF12).
            redesDao.marcarAccesoVencido(redId, true)
            throw e
        }
    }

    private suspend fun sincronizarAhora(redId: String): ResultadoSincronizacion {
        val red = redesDao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
        val api = apis.para(red)

        val envio = enviarCola(redId, api)
        val descarga = descargar(redId, red.cursor, api)
        actualizarCatalogoYSeveridades(redId, api)
        alertasDao.purgarAnterioresA(redId, reloj().minus(Duration.ofDays(diasDeCache)).toEpochMilli())

        val hora = reloj()
        redesDao.actualizarSincronizacion(redId, descarga.cursor, hora.toEpochMilli())
        if (red.accesoVencido) redesDao.marcarAccesoVencido(redId, false)
        return ResultadoSincronizacion(
            nuevas = descarga.nuevas,
            modificadas = descarga.modificadas,
            altasNuevas = descarga.altasNuevas,
            accionesEnviadas = envio.enviadas,
            accionesConError = envio.conError,
            hora = hora,
            mediasNuevas = descarga.mediasNuevas,
            bajasNuevas = descarga.bajasNuevas,
        )
    }

    override suspend fun descargarDetalle(redId: String, alertaId: String): Alerta {
        val red = redesDao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
        val dto = conErroresTraducidos { apis.para(red).alerta(alertaId) }
        guardar(listOf(dto.aDominio(redId)))
        return alertasDao.obtener(alertaId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
    }

    // ---- 1. Cola de acciones ----

    private data class ResultadoEnvio(val enviadas: Int, val conError: Int)

    private suspend fun enviarCola(redId: String, api: ZondaApi): ResultadoEnvio {
        var enviadas = 0
        var conError = 0
        for (fila in accionesDao.listarParaEnviar(redId)) {
            val accion = fila.aDominio()
            accionesDao.actualizarEnvio(accion.id, EstadoEnvio.ENVIANDO.name, accion.intentos, accion.ultimoError)
            try {
                enviarUna(redId, accion, api)
                enviadas++
            } catch (e: ErrorZonda.Conflicto) {
                val ajena = e.revisionAjena
                accionesDao.marcarConflicto(accion.id, ajena.resultado.name, ajena.motivo.name, ajena.comentario, ajena.autor, ajena.fecha.toEpochMilli())
            } catch (e: ErrorZonda.SinConexion) {
                devolverAPendiente(accion)
                throw e
            } catch (e: ErrorZonda.ServidorNoVerificado) {
                devolverAPendiente(accion)
                throw e
            } catch (e: ErrorZonda.NoAutorizado) {
                // El token venció o fue revocado: se pide un pareo nuevo; la acción espera.
                devolverAPendiente(accion)
                throw e
            } catch (e: ErrorZonda) {
                registrarFallo(accion, e)
                conError++
            }
        }
        return ResultadoEnvio(enviadas, conError)
    }

    private suspend fun enviarUna(redId: String, accion: AccionPendiente, api: ZondaApi) {
        when (accion.tipo) {
            TipoAccion.REVISAR_ALERTA -> {
                val alertaId = accion.alertaId ?: throw ErrorZonda.Validacion("alertaId", "La acción no indica la alerta")
                val entrada = accion.aRevisionEntrada() ?: throw ErrorZonda.Validacion("revision", "La acción no tiene revisión")
                val dto = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.revisar(alertaId, entrada) }
                val alerta = convertir { dto.aDominio(redId) }
                // La revisión local deja de ser provisoria: ya figura en el historial que manda el servidor.
                alertasDao.confirmarDelServidor(alerta.aEntidad(), alerta.historial.map { it.aEntidad(alerta.id) })
                accionesDao.borrar(accion.id)
            }
            TipoAccion.CAMBIAR_SEVERIDADES -> {
                val severidades = accion.severidades ?: throw ErrorZonda.Validacion("severidades", "La acción no tiene severidades")
                val respuesta = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.guardarSeveridades(severidades.aDto()) }
                redesDao.guardarSeveridades(redId, convertir { respuesta.aSeveridadPorTipo() }.aEntidades(redId))
                accionesDao.borrar(accion.id)
            }
        }
    }

    private suspend fun devolverAPendiente(accion: AccionPendiente) {
        accionesDao.actualizarEnvio(accion.id, EstadoEnvio.PENDIENTE.name, accion.intentos, accion.ultimoError)
    }

    /** Un intento más. Si el servidor rechazó los datos (4xx) o ya se intentó cinco veces, queda en ERROR. */
    private suspend fun registrarFallo(accion: AccionPendiente, error: ErrorZonda) {
        val intentos = accion.intentos + 1
        val definitivo = intentos >= AccionPendiente.MAXIMO_INTENTOS ||
            error is ErrorZonda.NoEncontrado ||
            error is ErrorZonda.Validacion ||
            (error is ErrorZonda.ErrorDelServidor && error.codigo != null && error.codigo in 400..499)
        val estado = if (definitivo) EstadoEnvio.ERROR else EstadoEnvio.PENDIENTE
        accionesDao.actualizarEnvio(accion.id, estado.name, intentos, error.message)
    }

    // ---- 2. Descarga por cursor ----

    private data class ResultadoDescarga(
        val cursor: String?,
        val nuevas: Int,
        val modificadas: Int,
        val altasNuevas: Int,
        val mediasNuevas: Int,
        val bajasNuevas: Int,
    )

    private suspend fun descargar(redId: String, cursorInicial: String?, api: ZondaApi): ResultadoDescarga =
        try {
            descargarDesde(redId, cursorInicial, api)
        } catch (e: ErrorZonda.CursorInvalido) {
            if (cursorInicial == null) throw e
            // El servidor se reinstaló: sincronización completa. Las alertas y las acciones pendientes quedan.
            descargarDesde(redId, cursorInicial = null, api)
        }

    private suspend fun descargarDesde(redId: String, cursorInicial: String?, api: ZondaApi): ResultadoDescarga {
        var cursor = cursorInicial
        var nuevas = 0
        var modificadas = 0
        var altasNuevas = 0
        var mediasNuevas = 0
        var bajasNuevas = 0
        var paginas = 0
        while (true) {
            val pagina = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.alertas(cursor) }
            val alertas = convertir { pagina.alertas.map { it.aDominio(redId) } }
            if (alertas.isNotEmpty()) {
                val existentes = idsExistentes(redId, alertas.map { it.id })
                for (alerta in alertas) {
                    if (alerta.id in existentes) {
                        modificadas++
                    } else {
                        nuevas++
                        when (alerta.severidad) {
                            Severidad.ALTA -> altasNuevas++
                            Severidad.MEDIA -> mediasNuevas++
                            Severidad.BAJA -> bajasNuevas++
                        }
                    }
                }
                guardar(alertas)
            }
            cursor = pagina.cursorSiguiente
            // El cursor se guarda por página: un corte a mitad de camino no obliga a bajar todo de nuevo.
            redesDao.actualizarCursor(redId, cursor)
            paginas++
            if (!pagina.hayMas || paginas >= MAXIMO_PAGINAS) break
        }
        return ResultadoDescarga(cursor, nuevas, modificadas, altasNuevas, mediasNuevas, bajasNuevas)
    }

    /** SQLite admite hasta 999 parámetros por consulta. */
    private suspend fun idsExistentes(redId: String, ids: List<String>): Set<String> =
        ids.chunked(500).flatMap { alertasDao.idsExistentes(redId, it) }.toSet()

    private suspend fun guardar(alertas: List<Alerta>) {
        val revisiones = alertas.associate { alerta ->
            alerta.id to alerta.historial.filter { it.origen == OrigenRevision.SERVIDOR }.map { it.aEntidad(alerta.id) }
        }
        alertasDao.guardarDelServidor(alertas.map { it.aEntidad() }, revisiones)
    }

    // ---- 3. Catálogo y severidades ----

    /** Son datos secundarios: si fallan, la sincronización de alertas vale igual. Un 401 sí se propaga. */
    private suspend fun actualizarCatalogoYSeveridades(redId: String, api: ZondaApi) {
        try {
            val catalogo = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.catalogo() }.aDominio()
            redesDao.guardarCatalogo(
                redId = redId,
                equipos = catalogo.equipos.map { (ip, nombre) -> CatalogoEquipoEntity(redId, ip, nombre) },
                servicios = catalogo.servicios.map { CatalogoServicioEntity(redId, it) },
            )
            val hayCambioPendiente = accionesDao.listarParaEnviar(redId).any { it.tipo == TipoAccion.CAMBIAR_SEVERIDADES.name }
            if (!hayCambioPendiente) {
                // Otra persona pudo cambiar las severidades desde su teléfono: rige lo que tiene el servidor.
                val severidades = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.severidades() }
                redesDao.guardarSeveridades(redId, convertir { severidades.aSeveridadPorTipo() }.aEntidades(redId))
            }
        } catch (e: ErrorZonda.NoAutorizado) {
            throw e
        } catch (e: ErrorZonda) {
            // Se reintenta en la sincronización siguiente.
        }
    }

    /** Un valor que el dominio no conoce (un tipo nuevo, por ejemplo) se informa como error del servidor, no como caída. */
    private inline fun <T> convertir(bloque: () -> T): T =
        try {
            bloque()
        } catch (e: IllegalArgumentException) {
            throw ErrorZonda.ErrorDelServidor(null, e)
        } catch (e: NullPointerException) {
            throw ErrorZonda.ErrorDelServidor(null, e)
        } catch (e: java.time.format.DateTimeParseException) {
            throw ErrorZonda.ErrorDelServidor(null, e)
        }

    companion object {
        const val DIAS_DE_CACHE = 30L
        private const val MAXIMO_PAGINAS = 1_000
    }
}
