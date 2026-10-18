package ar.edu.uade.zonda.data.sync

import ar.edu.uade.zonda.data.local.ZondaDatabase
import ar.edu.uade.zonda.data.local.entidades.AsignacionPendienteEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aDto
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.mapper.aEntidades
import ar.edu.uade.zonda.data.mapper.aRevisionEntrada
import ar.edu.uade.zonda.data.mapper.aSeveridadPorTipo
import ar.edu.uade.zonda.data.mapper.enLaApi
import ar.edu.uade.zonda.data.remote.AsignacionEntradaDto
import ar.edu.uade.zonda.data.remote.ImportanciaEntradaDto
import ar.edu.uade.zonda.data.remote.OperacionHttp
import ar.edu.uade.zonda.data.remote.ProveedorDeApi
import ar.edu.uade.zonda.data.remote.SeveridadesEntradaDto
import ar.edu.uade.zonda.data.remote.ZondaApi
import ar.edu.uade.zonda.data.remote.conErroresTraducidos
import ar.edu.uade.zonda.domain.model.AccionPendiente
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.AsignacionNueva
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.OrigenRevision
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAccion
import ar.edu.uade.zonda.domain.repository.AlertaNueva
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * 3. Actualiza el catálogo, las severidades y el estado de Telegram de la red (y le manda los avisos
 *    del teléfono si el servidor tiene otros), purga las alertas de más de 30 días (RNF03) y registra
 *    la hora de la sincronización.
 *
 * Mientras baja desde cero (la primera vez, o después de borrar los datos locales) publica cuántas
 * van en [progresoDescarga], para la tarjeta "Descargando alertas…" de Estado (Figma 50-616).
 */
class SincronizadorHttp(
    private val base: ZondaDatabase,
    private val apis: ProveedorDeApi,
    private val reloj: () -> Instant = Instant::now,
    private val diasDeCache: Long = DIAS_DE_CACHE,
    /** Cómo avisa Telegram cada severidad en este teléfono (las preferencias de DataStore). */
    private val avisosDelTelefono: suspend () -> PreferenciasAvisos = { PreferenciasAvisos() },
) : Sincronizador {

    private val alertasDao get() = base.alertasDao()
    private val redesDao get() = base.redesDao()
    private val accionesDao get() = base.accionesPendientesDao()

    /**
     * Una sincronización por vez: si se piden dos juntas (al volver la red desde dos pantallas, por
     * ejemplo), la segunda espera a que termine la primera y no manda la misma acción dos veces.
     */
    private val candado = Mutex()

    private val _progresoDescarga = MutableStateFlow<ProgresoDescarga?>(null)
    override val progresoDescarga: StateFlow<ProgresoDescarga?> = _progresoDescarga.asStateFlow()

    override suspend fun sincronizar(redId: String): ResultadoSincronizacion = candado.withLock {
        try {
            sincronizarAhora(redId)
        } catch (e: ErrorZonda.NoAutorizado) {
            // El token venció o fue revocado: la red queda marcada y la app pide un pareo nuevo (RNF12).
            redesDao.marcarAccesoVencido(redId, true)
            throw e
        } finally {
            _progresoDescarga.value = null
        }
    }

    private suspend fun sincronizarAhora(redId: String): ResultadoSincronizacion {
        val red = redesDao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
        val api = apis.para(red)

        val envio = enviarCola(redId, api)
        enviarAsignaciones(redId, api)
        // Primero la gente: dice el perfil de este teléfono y su id, que hace falta para saber qué le asignaron.
        actualizarPersonasYRegistro(redId, api)
        val miId = redesDao.obtener(redId)?.dispositivoId
        val descarga = descargar(redId, red.cursor, api, miId)
        actualizarCatalogoYSeveridades(redId, api)
        actualizarTelegram(redId, api)
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
            alertasNuevas = descarga.alertasNuevas,
            conflictosNuevos = envio.conflictos,
            asignacionesNuevas = descarga.asignacionesNuevas,
        )
    }

    override suspend fun descargarDetalle(redId: String, alertaId: String): Alerta {
        val red = redesDao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
        val dto = conErroresTraducidos { apis.para(red).alerta(alertaId) }
        guardar(listOf(dto.aDominio(redId)))
        return alertasDao.obtener(alertaId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
    }

    // ---- 1. Cola de acciones ----

    private data class ResultadoEnvio(val enviadas: Int, val conError: Int, val conflictos: List<String>)

    private suspend fun enviarCola(redId: String, api: ZondaApi): ResultadoEnvio {
        var enviadas = 0
        var conError = 0
        val conflictos = mutableListOf<String>()
        for (fila in accionesDao.listarParaEnviar(redId)) {
            val accion = fila.aDominio()
            accionesDao.actualizarEnvio(accion.id, EstadoEnvio.ENVIANDO.name, accion.intentos, accion.ultimoError)
            try {
                enviarUna(redId, accion, api)
                enviadas++
            } catch (e: ErrorZonda.Conflicto) {
                val ajena = e.revisionAjena
                accionesDao.marcarConflicto(
                    id = accion.id,
                    resultado = ajena.resultado.name,
                    motivo = ajena.motivo.name,
                    comentario = ajena.comentario,
                    autor = ajena.autor,
                    fecha = ajena.fecha.toEpochMilli(),
                    revisadaEn = ajena.revisadaEn?.toEpochMilli(),
                )
                accion.alertaId?.let(conflictos::add)
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
        return ResultadoEnvio(enviadas, conError, conflictos)
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
                // Con el motivo, que el servidor exige y anota en el registro de cambios (decisión 40).
                val entrada = SeveridadesEntradaDto(severidades.aDto(), accion.motivo.orEmpty())
                val respuesta = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.guardarSeveridades(entrada) }
                redesDao.guardarSeveridades(redId, convertir { respuesta.aSeveridadPorTipo() }.aEntidades(redId))
                accionesDao.borrar(accion.id)
            }
            TipoAccion.CAMBIAR_IMPORTANCIA -> {
                val ip = accion.equipoIp ?: throw ErrorZonda.Validacion("equipoIp", "La acción no indica el equipo")
                val importancia = accion.importancia ?: throw ErrorZonda.Validacion("importancia", "La acción no tiene la importancia")
                val entrada = ImportanciaEntradaDto(importancia.enLaApi(), accion.motivo.orEmpty())
                val equipo = convertir { conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.cambiarImportancia(ip, entrada) }.aDominio() }
                redesDao.guardarEquipos(listOf(equipo.aEntidad(redId)))
                accionesDao.borrar(accion.id)
            }
        }
    }

    // ---- 1 b. Asignaciones pendientes (decisión 42) ----

    /**
     * Cada asignación hecha en el teléfono, la última de cada alerta. Si el servidor la rechaza (a esta
     * persona no le toca, otra persona ya la tiene, la alerta o la persona ya no están), la alerta vuelve al
     * responsable que dice el servidor: no tiene sentido reintentarla. Sin conexión espera.
     */
    private suspend fun enviarAsignaciones(redId: String, api: ZondaApi) {
        for (fila in accionesDao.asignacionesParaEnviar(redId)) {
            accionesDao.actualizarEnvioAsignacion(fila.alertaId, EstadoEnvio.ENVIANDO.name, fila.intentos, fila.ultimoError)
            try {
                val dto = conErroresTraducidos(OperacionHttp.ASIGNACION) { api.asignar(fila.alertaId, AsignacionEntradaDto(fila.accionId, fila.responsableId)) }
                val alerta = convertir { dto.aDominio(redId) }
                alertasDao.confirmarAsignacionDelServidor(alerta.aEntidad(), revisionesDelServidor(alerta), asignacionesDe(alerta))
                accionesDao.borrarAsignacion(fila.alertaId)
            } catch (e: ErrorZonda.SinConexion) {
                devolverAPendiente(fila)
                throw e
            } catch (e: ErrorZonda.ServidorNoVerificado) {
                devolverAPendiente(fila)
                throw e
            } catch (e: ErrorZonda.NoAutorizado) {
                devolverAPendiente(fila)
                throw e
            } catch (e: ErrorZonda) {
                val intentos = fila.intentos + 1
                val rechazada = e is ErrorZonda.SinPermiso || e is ErrorZonda.YaAsignada || e is ErrorZonda.NoEncontrado ||
                    (e is ErrorZonda.ErrorDelServidor && e.codigo != null && e.codigo in 400..499)
                if (rechazada || intentos >= AccionPendiente.MAXIMO_INTENTOS) {
                    accionesDao.borrarAsignacion(fila.alertaId)
                    alertasDao.restaurarAsignacionDelServidor(fila.alertaId)
                } else {
                    accionesDao.actualizarEnvioAsignacion(fila.alertaId, EstadoEnvio.PENDIENTE.name, intentos, e.message)
                }
            }
        }
    }

    private suspend fun devolverAPendiente(fila: AsignacionPendienteEntity) {
        accionesDao.actualizarEnvioAsignacion(fila.alertaId, EstadoEnvio.PENDIENTE.name, fila.intentos, fila.ultimoError)
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
        val alertasNuevas: List<AlertaNueva>,
        val asignacionesNuevas: List<AsignacionNueva>,
    )

    private suspend fun descargar(redId: String, cursorInicial: String?, api: ZondaApi, miId: String?): ResultadoDescarga =
        try {
            descargarDesde(redId, cursorInicial, api, miId)
        } catch (e: ErrorZonda.CursorInvalido) {
            if (cursorInicial == null) throw e
            // El servidor se reinstaló: sincronización completa. Las alertas y las acciones pendientes quedan.
            descargarDesde(redId, cursorInicial = null, api, miId)
        }

    private suspend fun descargarDesde(redId: String, cursorInicial: String?, api: ZondaApi, miId: String?): ResultadoDescarga {
        var cursor = cursorInicial
        var nuevas = 0
        var modificadas = 0
        var altasNuevas = 0
        val alertasNuevas = mutableListOf<AlertaNueva>()
        val asignacionesNuevas = mutableListOf<AsignacionNueva>()
        var paginas = 0
        // Desde cero se baja todo lo de los últimos 30 días: la pantalla Estado muestra cuántas van.
        val desdeCero = cursorInicial == null
        var total: Int? = null
        var descargadas = 0
        if (desdeCero) _progresoDescarga.value = ProgresoDescarga(redId, descargadas = 0, total = null)
        while (true) {
            val pagina = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.alertas(cursor) }
            val alertas = convertir { pagina.alertas.map { it.aDominio(redId) } }
            if (desdeCero) {
                total = total ?: pagina.total
                descargadas += alertas.size
            }
            if (alertas.isNotEmpty()) {
                val existentes = idsExistentes(redId, alertas.map { it.id })
                for (alerta in alertas) {
                    if (alerta.id in existentes) {
                        modificadas++
                    } else {
                        nuevas++
                        if (alerta.severidad == Severidad.ALTA) altasNuevas++
                        alertasNuevas += AlertaNueva(alerta.id, alerta.severidad, conAvisoTelegram = alerta.avisoTelegram != null)
                    }
                }
                if (miId != null) asignacionesNuevas += asignadasPorOtro(alertas, miId)
                guardar(alertas)
            }
            cursor = pagina.cursorSiguiente
            // El cursor se guarda por página: un corte a mitad de camino no obliga a bajar todo de nuevo.
            redesDao.actualizarCursor(redId, cursor)
            if (desdeCero) _progresoDescarga.value = ProgresoDescarga(redId, descargadas, total)
            paginas++
            if (!pagina.hayMas || paginas >= MAXIMO_PAGINAS) break
        }
        return ResultadoDescarga(cursor, nuevas, modificadas, altasNuevas, alertasNuevas, asignacionesNuevas)
    }

    /**
     * Las alertas que otra persona le asignó a este teléfono y que el teléfono todavía no sabía: su última
     * asignación es para [miId], la hizo otra persona y es posterior a la última que conocía. Asignarse
     * una uno mismo no avisa (decisión 42).
     */
    private suspend fun asignadasPorOtro(alertas: List<Alerta>, miId: String): List<AsignacionNueva> {
        val paraMi = alertas.filter { alerta -> alerta.asignacion?.let { it.responsableId == miId && it.porId != miId } == true }
        if (paraMi.isEmpty()) return emptyList()
        val conocidas = paraMi.map { it.id }.chunked(500)
            .flatMap { alertasDao.ultimasAsignacionesDelServidor(it) }
            .associate { it.alertaId to it.fecha }
        return paraMi.mapNotNull { alerta ->
            val asignacion = alerta.asignacion ?: return@mapNotNull null
            val anterior = conocidas[alerta.id]
            if (anterior != null && anterior >= asignacion.fecha.toEpochMilli()) return@mapNotNull null
            AsignacionNueva(alerta.id, asignacion.por, alerta.severidad, alerta.servicio, alerta.inicio)
        }
    }

    /** SQLite admite hasta 999 parámetros por consulta. */
    private suspend fun idsExistentes(redId: String, ids: List<String>): Set<String> =
        ids.chunked(500).flatMap { alertasDao.idsExistentes(redId, it) }.toSet()

    private suspend fun guardar(alertas: List<Alerta>) {
        alertasDao.guardarDelServidor(
            alertas.map { it.aEntidad() },
            alertas.associate { it.id to revisionesDelServidor(it) },
            alertas.associate { it.id to asignacionesDe(it) },
        )
    }

    private fun revisionesDelServidor(alerta: Alerta) =
        alerta.historial.filter { it.origen == OrigenRevision.SERVIDOR }.map { it.aEntidad(alerta.id) }

    private fun asignacionesDe(alerta: Alerta) = alerta.asignaciones.map { it.aEntidad(alerta.id, OrigenRevision.SERVIDOR) }

    // ---- 3. Catálogo y severidades ----

    /** Son datos secundarios: si fallan, la sincronización de alertas vale igual. Un 401 sí se propaga. */
    private suspend fun actualizarCatalogoYSeveridades(redId: String, api: ZondaApi) {
        try {
            val catalogo = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.catalogo() }.aDominio()
            // El catálogo rehace la tabla: cada equipo conserva su importancia hasta que lleguen las del servidor.
            val importancias = redesDao.equiposDe(redId).associate { it.ip to it.importancia }
            redesDao.guardarCatalogo(
                redId = redId,
                equipos = catalogo.equipos.map { (ip, nombre) ->
                    CatalogoEquipoEntity(redId, ip, nombre, importancias[ip] ?: Importancia.NORMAL.name)
                },
                servicios = catalogo.servicios.map { CatalogoServicioEntity(redId, it) },
            )
            val pendientes = accionesDao.listarParaEnviar(redId)
            val hayCambioPendiente = pendientes.any { it.tipo == TipoAccion.CAMBIAR_SEVERIDADES.name }
            if (!hayCambioPendiente) {
                // Otra persona pudo cambiar las severidades desde su teléfono: rige lo que tiene el servidor.
                val severidades = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.severidades() }
                redesDao.guardarSeveridades(redId, convertir { severidades.aSeveridadPorTipo() }.aEntidades(redId))
            }
            actualizarEquipos(redId, api, pendientes.filter { it.tipo == TipoAccion.CAMBIAR_IMPORTANCIA.name }.mapNotNull { it.equipoIp }.toSet())
        } catch (e: ErrorZonda.NoAutorizado) {
            throw e
        } catch (e: ErrorZonda) {
            // Se reintenta en la sincronización siguiente.
        }
    }

    /**
     * La importancia de cada equipo (decisión 41). La de un equipo con un cambio todavía sin enviar queda
     * como la eligió esta persona. Un servidor anterior a la API 0.6.0 no tiene la ruta: todos normales.
     */
    private suspend fun actualizarEquipos(redId: String, api: ZondaApi, conCambioPendiente: Set<String>) {
        val equipos = try {
            conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.equipos() }
        } catch (e: ErrorZonda.NoEncontrado) {
            return
        }
        val locales = redesDao.equiposDe(redId).associate { it.ip to it.importancia }
        redesDao.guardarEquipos(
            convertir { equipos.map { it.aDominio() } }.map { equipo ->
                val entidad = equipo.aEntidad(redId)
                if (equipo.ip in conCambioPendiente) entidad.copy(importancia = locales[equipo.ip] ?: entidad.importancia) else entidad
            },
        )
    }

    /** La gente y el registro de cambios: datos secundarios, como el catálogo. Un 401 sí se propaga. */
    private suspend fun actualizarPersonasYRegistro(redId: String, api: ZondaApi) {
        try {
            traerPersonasYRegistro(redId, api, redesDao)
        } catch (e: ErrorZonda.NoAutorizado) {
            throw e
        } catch (e: ErrorZonda) {
            // Se reintenta en la sincronización siguiente.
        }
    }

    // ---- 4. Telegram ----

    /**
     * Si el chat sigue vinculado (la persona pudo bloquear el bot) y con qué bot. Si el servidor tiene
     * otros avisos que el teléfono (se cambiaron sin conexión), le manda los del teléfono. Es un dato
     * secundario, como el catálogo: si falla, la sincronización vale igual.
     */
    private suspend fun actualizarTelegram(redId: String, api: ZondaApi) {
        try {
            val estado = conErroresTraducidos(OperacionHttp.TELEGRAM) { api.telegram() }.let { convertir { it.aDominio() } }
            redesDao.actualizarTelegram(redId, estado.vinculado, estado.bot)
            val avisos = avisosDelTelefono()
            if (estado.avisos != avisos) {
                conErroresTraducidos(OperacionHttp.TELEGRAM) { api.guardarAvisos(avisos.aDto()) }
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
