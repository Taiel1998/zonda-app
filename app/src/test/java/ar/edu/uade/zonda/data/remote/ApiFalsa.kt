package ar.edu.uade.zonda.data.remote

import ar.edu.uade.zonda.domain.model.Red
import java.time.OffsetDateTime
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Una respuesta de error de la API, como la vería Retrofit. */
fun errorHttp(codigo: Int, cuerpo: String = """{"detail": "error de prueba"}"""): HttpException =
    HttpException(Response.error<Any>(codigo, cuerpo.toResponseBody("application/json".toMediaType())))

/**
 * Servidor en memoria con las mismas reglas que la API real (backend/app/rutas.py): secuencia y
 * cursor por instalación, 410 con un cursor ajeno, revisiones idempotentes por `accionId`, 409 si
 * otra persona revisó antes, códigos de pareo de un uso. Las pruebas de los repositorios y de la
 * sincronización lo usan en lugar de HTTP; el cliente HTTP real se prueba aparte con MockWebServer.
 */
class ApiFalsa : ZondaApi {

    var instancia = "inst1"
    var tamanioPagina = 200
    var redId = "red-oficina"
    var redNombre = "Oficina"

    /**
     * Por operación (`salud`, `alertas`, `alerta`, `revisar`, `severidades`, `catalogo`, `parear`, `vinculo`,
     * `quitar`, `telegram`, `desvincular`, `prueba`, `avisos`): qué lanzar.
     */
    val errores = mutableMapOf<String, Exception>()

    private val alertas = linkedMapOf<String, AlertaDto>()
    private val secuencias = mutableMapOf<String, Int>()
    private var secuencia = 0
    private val accionesAplicadas = mutableSetOf<String>()

    val revisionesRecibidas = mutableListOf<RevisionEntradaDto>()
    val severidades = mutableMapOf(
        "fuerza_bruta" to "alta",
        "sin_tipificar" to "alta",
        "escaneo" to "media",
        "ddos" to "media",
        "servicio_no_autorizado" to "media",
        "volumen_inusual" to "media",
        "probable_falsa_alarma" to "baja",
    )
    var catalogo = CatalogoDto(equipos = mapOf("192.168.10.50" to "srv-web", "192.168.10.9" to "pc-09"), servicios = listOf("smb", "ssh"))
    val codigosDePareo = mutableSetOf<String>()
    val codigosUsados = mutableSetOf<String>()
    var dispositivosPareados = 0
    var tokenRevocado = false
    var vinculo = VinculoTelegramDto("ABCD-EFGH", "https://t.me/zonda_alertas_bot?start=ABCD-EFGH", null)

    /** La gente de la red, como `GET /personas`: este teléfono es el primero (d-1) y administrador. */
    val personas = mutableListOf(PersonaDto("d-1", null, "Este teléfono", "administrador", "2026-10-12T00:00:00+00:00", vos = true))
    val registro = mutableListOf<CambioRegistroDto>()
    val importancias = mutableMapOf<String, String>()
    val motivosRecibidos = mutableListOf<String>()
    val asignacionesRecibidas = mutableListOf<AsignacionEntradaDto>()
    /** Lo que devuelve `PUT /alertas/{id}/asignacion` en lugar de aplicarla (por ejemplo, un 409). */
    var errorAlAsignar: Exception? = null

    /** Lo de Telegram de este teléfono, como lo guarda la API (`dispositivos` y su chat). */
    var telegramVinculado = false
    var botTelegram = "zonda_alertas_bot"
    var avisosTelegram = AvisosDto("sonido", "vibracion", "sin_aviso")
    val vinculosPedidos = mutableListOf<VinculoEntradaDto>()
    var avisosGuardados = 0
    var pruebasEnviadas = 0

    fun alertaGuardada(id: String): AlertaDto = alertas.getValue(id)

    /** Se llama al pedir cada página de alertas, antes de responder (para mirar el progreso de la descarga). */
    var alPedirPagina: ((desde: String?) -> Unit)? = null

    /** Publica (o republica) una alerta: toma el número de secuencia siguiente, como en la API. */
    fun publicar(alerta: AlertaDto) {
        secuencia++
        secuencias[alerta.id] = secuencia
        alertas[alerta.id] = alerta
    }

    /** Otra persona revisó la alerta desde su teléfono. */
    fun revisarDesdeOtroTelefono(
        id: String,
        resultado: String = "falso_positivo",
        motivo: String = "tarea_programada",
        comentario: String = "Era el respaldo nocturno",
        autor: String = "Teléfono de Lucía",
    ) {
        val actual = alertaGuardada(id)
        publicar(
            actual.copy(
                estadoRevision = resultado,
                version = actual.version + 1,
                historial = actual.historial + RevisionDto(resultado, motivo, comentario, autor, "2026-10-12T05:00:00+00:00"),
                actualizadaEn = "2026-10-12T05:00:00+00:00",
            ),
        )
    }

    private fun fallarSiCorresponde(operacion: String) {
        errores[operacion]?.let { throw it }
    }

    override suspend fun saludConRespuesta(): retrofit2.Response<SaludDto> = retrofit2.Response.success(salud())

    /** Lo que manda `GET /salud` en `sistema` (API 0.7.0); null, como un servidor anterior. */
    var sistema: SistemaDto? = null

    /** La hora del servidor que manda `GET /salud`. */
    var horaSalud = "2026-10-12T04:05:29+00:00"

    override suspend fun salud(): SaludDto {
        fallarSiCorresponde("salud")
        return SaludDto("0.3.0", horaSalud, alertas.size, RedDto(redId, redNombre), null, sistema, firewall.takeIf { firewallEnSalud })
    }

    override suspend fun alertas(desde: String?): PaginaAlertasDto {
        fallarSiCorresponde("alertas")
        alPedirPagina?.invoke(desde)
        var desdeSecuencia = 0
        if (desde != null) {
            val partes = desde.split(":")
            if (partes.size != 2 || partes[0] != instancia || partes[1].toIntOrNull() == null) throw errorHttp(410)
            desdeSecuencia = partes[1].toInt()
        }
        val pendientes = alertas.values
            .filter { secuencias.getValue(it.id) > desdeSecuencia }
            .sortedBy { secuencias.getValue(it.id) }
        val pagina = pendientes.take(tamanioPagina)
        val ultima = pagina.lastOrNull()?.let { secuencias.getValue(it.id) } ?: desdeSecuencia
        // Como el servidor: la primera página de una sincronización desde cero dice cuántas trae en total.
        val total = if (desde == null) pendientes.size else null
        return PaginaAlertasDto(pagina, "$instancia:$ultima", pendientes.size > tamanioPagina, "2026-10-12T12:00:00+00:00", total)
    }

    override suspend fun alerta(id: String): AlertaDto {
        fallarSiCorresponde("alerta")
        return alertas[id] ?: throw errorHttp(404)
    }

    override suspend fun revisar(id: String, revision: RevisionEntradaDto): AlertaDto {
        fallarSiCorresponde("revisar")
        val actual = alertas[id] ?: throw errorHttp(404)
        revisionesRecibidas += revision
        if (revision.accionId in accionesAplicadas) return actual
        val versionVista = revision.versionVista
        if (versionVista != null && versionVista < actual.version && !revision.forzar && actual.historial.isNotEmpty()) {
            val ajena = ProveedorDeApiRetrofit.gson.toJson(actual.historial.last())
            throw errorHttp(409, """{"detail": {"mensaje": "Otra persona revisó esta alerta", "revisionAjena": $ajena}}""")
        }
        accionesAplicadas += revision.accionId
        val nueva = actual.copy(
            estadoRevision = revision.resultado,
            version = actual.version + 1,
            historial = actual.historial + RevisionDto(revision.resultado, revision.motivo, revision.comentario, "Este teléfono", "2026-10-12T06:00:00+00:00"),
            actualizadaEn = "2026-10-12T06:00:00+00:00",
        )
        publicar(nueva)
        return nueva
    }

    override suspend fun severidades(): Map<String, String> {
        fallarSiCorresponde("severidades")
        return severidades.toMap()
    }

    override suspend fun guardarSeveridades(entrada: SeveridadesEntradaDto): Map<String, String> {
        fallarSiCorresponde("severidades")
        // Como la API 0.6.0: sin motivo, 422 (decisión 40).
        if (entrada.motivo.isBlank()) throw errorHttp(422)
        motivosRecibidos += entrada.motivo
        this.severidades.putAll(entrada.severidades)
        // Como la API: la severidad sale del tipo y todas las alertas vuelven a salir en la sincronización.
        alertas.values.toList().forEach { alerta -> publicar(alerta.copy(severidad = severidadDe(alerta))) }
        return this.severidades.toMap()
    }

    /** Como la API: la de su tipo o, si la IA no la vio, la más alta de los detectores que la marcaron (cambio 9). */
    private fun severidadDe(alerta: AlertaDto): String {
        val claves = alerta.tipoAtaque?.let(::listOf)
            ?: listOfNotNull(
                "servicio_no_autorizado".takeIf { alerta.veredictos.reglas },
                "volumen_inusual".takeIf { alerta.veredictos.umbrales },
            ).ifEmpty { listOf("servicio_no_autorizado", "volumen_inusual") }
        val delTipo = claves.map(severidades::getValue).maxBy { NIVELES.indexOf(it) }
        // Y un nivel más o menos según la importancia del equipo destino (decisión 41).
        val nivel = NIVELES.indexOf(delTipo) + when (importancias[alerta.destinoIp]) {
            "critico" -> 1
            "poco_importante" -> -1
            else -> 0
        }
        return NIVELES[nivel.coerceIn(0, NIVELES.lastIndex)]
    }

    override suspend fun catalogo(): CatalogoDto {
        fallarSiCorresponde("catalogo")
        return catalogo
    }

    override suspend fun parear(entrada: PareoEntradaDto): PareoSalidaDto {
        fallarSiCorresponde("parear")
        val codigo = entrada.codigo.trim().uppercase()
        if (codigo !in codigosDePareo) throw errorHttp(400)
        if (codigo in codigosUsados) throw errorHttp(410)
        codigosUsados += codigo
        dispositivosPareados++
        return PareoSalidaDto("token-$dispositivosPareados", RedDto(redId, redNombre), "2027-01-10T00:00:00+00:00", "d-$dispositivosPareados", "2026-10-12T00:00:00+00:00", alertas.size)
    }

    override suspend fun telegram(): EstadoTelegramDto {
        fallarSiCorresponde("telegram")
        return EstadoTelegramDto(telegramVinculado, botTelegram, null, avisosTelegram)
    }

    override suspend fun vincularTelegram(entrada: VinculoEntradaDto): VinculoTelegramDto {
        fallarSiCorresponde("vinculo")
        vinculosPedidos += entrada
        entrada.avisos?.let { avisosTelegram = it }
        return vinculo
    }

    override suspend fun desvincularTelegram(): Response<Unit> {
        fallarSiCorresponde("desvincular")
        telegramVinculado = false
        return Response.success(204, Unit)
    }

    /** Como la API: 409 si el teléfono no tiene un chat vinculado. */
    override suspend fun avisoDePrueba(): Response<Unit> {
        fallarSiCorresponde("prueba")
        if (!telegramVinculado) throw errorHttp(409)
        pruebasEnviadas++
        return Response.success(204, Unit)
    }

    override suspend fun guardarAvisos(avisos: AvisosDto): AvisosDto {
        fallarSiCorresponde("avisos")
        avisosGuardados++
        avisosTelegram = avisos
        return avisos
    }

    override suspend fun quitarDispositivo(): Response<Unit> {
        fallarSiCorresponde("quitar")
        tokenRevocado = true
        return Response.success(204, Unit)
    }

    // ---- Personas, equipos, registro y asignaciones (API 0.6.0) ----

    override suspend fun personas(): List<PersonaDto> {
        fallarSiCorresponde("personas")
        return personas.toList()
    }

    override suspend fun codigoParaPersona(entrada: CodigoPersonaEntradaDto): CodigoPersonaDto {
        fallarSiCorresponde("codigo")
        return CodigoPersonaDto("LAUR-A123", "2026-10-12T00:10:00+00:00", "zonda:https://zonda-equipo.duckdns.org#LAUR-A123;vence=2026-10-12T00:10:00+00:00", entrada.nombre, entrada.perfil)
    }

    override suspend fun cambiarPerfil(id: String, entrada: PerfilEntradaDto): PersonaDto {
        fallarSiCorresponde("perfil")
        val indice = personas.indexOfFirst { it.id == id }
        if (indice < 0) throw errorHttp(404)
        personas[indice] = personas[indice].copy(perfil = entrada.perfil)
        return personas[indice]
    }

    override suspend fun quitarPersona(id: String): Response<Unit> {
        fallarSiCorresponde("quitarPersona")
        personas.removeAll { it.id == id }
        return Response.success(204, Unit)
    }

    override suspend fun equipos(): List<EquipoDto> {
        fallarSiCorresponde("equipos")
        return catalogo.equipos.map { (ip, nombre) -> EquipoDto(ip, nombre, importancias[ip] ?: "normal") }
    }

    override suspend fun cambiarImportancia(ip: String, entrada: ImportanciaEntradaDto): EquipoDto {
        fallarSiCorresponde("importancia")
        if (entrada.motivo.isBlank()) throw errorHttp(422)
        val nombre = catalogo.equipos[ip] ?: throw errorHttp(404)
        importancias[ip] = entrada.importancia
        motivosRecibidos += entrada.motivo
        // Como la API: las alertas de ese equipo vuelven a salir con la severidad nueva.
        alertas.values.filter { it.destinoIp == ip }.forEach { alerta -> publicar(alerta.copy(severidad = severidadDe(alerta))) }
        return EquipoDto(ip, nombre, entrada.importancia)
    }

    /** Si la última vez se pidió el registro con los bloqueos (`?bloqueos=true`). */
    var registroConBloqueos: Boolean? = null

    /** Como la API 0.9.0: los bloqueos y desbloqueos, solo a quien los pide. */
    override suspend fun registro(conBloqueos: Boolean): List<CambioRegistroDto> {
        fallarSiCorresponde("registro")
        registroConBloqueos = conBloqueos
        return registro.filter { conBloqueos || it.tipo !in setOf("bloqueo", "desbloqueo") }
    }

    override suspend fun asignar(id: String, entrada: AsignacionEntradaDto): AlertaDto {
        fallarSiCorresponde("asignar")
        errorAlAsignar?.let { throw it }
        val actual = alertas[id] ?: throw errorHttp(404)
        asignacionesRecibidas += entrada
        val persona = personas.firstOrNull { it.id == entrada.responsableId }
        val evento = AsignacionDto(entrada.responsableId, persona?.let { it.nombre ?: it.telefono }, "d-1", "Este teléfono", "2026-10-12T06:00:00+00:00")
        val nueva = actual.copy(asignacion = evento.takeIf { entrada.responsableId != null }, asignaciones = actual.asignaciones.orEmpty() + evento)
        publicar(nueva)
        return nueva
    }

    // ---- Bloqueos del firewall (API 0.9.0, decisión 51) ----

    /** El firewall de la red: apagado, como un servidor sin ZONDA_FIREWALL_TOKEN (modo de observación). */
    var firewall = EstadoFirewallDto(habilitado = false, conectado = false, ultimaConsulta = null, nuncaBloquear = emptyList())

    /** Si `GET /salud` manda el firewall; falso, como un servidor anterior a la API 0.9.0. */
    var firewallEnSalud = true

    /** Los bloqueos de la red, del más viejo al más nuevo. */
    val bloqueos = mutableListOf<BloqueoDto>()
    val bloqueosRecibidos = mutableListOf<BloqueoEntradaDto>()

    /** Como la API: el teléfono de un administrador lo deja aprobado; el de un analista, pedido. */
    private fun esAdministrador() = (personas.firstOrNull { it.vos }?.perfil ?: "administrador") == "administrador"

    override suspend fun bloqueos(): ListaBloqueosDto {
        fallarSiCorresponde("bloqueos")
        return ListaBloqueosDto(bloqueos.reversed(), firewall)
    }

    override suspend fun bloquear(entrada: BloqueoEntradaDto): BloqueoDto {
        fallarSiCorresponde("bloquear")
        if (!firewall.habilitado) throw errorHttp(503, """{"detail": "Los bloqueos están apagados: esta red no tiene un firewall conectado"}""")
        bloqueos.firstOrNull { it.id == "b-${entrada.accionId}" }?.let { return it }
        val alerta = alertas[entrada.alertaId] ?: throw errorHttp(404)
        if (entrada.duracion == null && alerta.estadoRevision != "ataque_real") {
            throw errorHttp(422, """{"detail": "«Hasta desbloquear» se elige con la alerta revisada como ataque real"}""")
        }
        if (bloqueos.any { it.ip == alerta.origenIp && it.estado in ACTIVOS }) {
            throw errorHttp(409, """{"detail": "Ese equipo ya tiene un bloqueo o un pedido de bloqueo"}""")
        }
        bloqueosRecibidos += entrada
        val administrador = esAdministrador()
        val nuevo = BloqueoDto(
            id = "b-${entrada.accionId}",
            alertaId = alerta.id,
            ip = alerta.origenIp,
            nombre = alerta.origenNombre,
            estado = if (administrador) "aprobado" else "pedido",
            duracion = entrada.duracion,
            motivo = entrada.motivo,
            pedidoPorId = "d-1",
            pedidoPor = "Este teléfono",
            pedidoEn = HORA_BLOQUEO,
            aprobadoPorId = "d-1".takeIf { administrador },
            aprobadoPor = "Este teléfono".takeIf { administrador },
            aprobadoEn = HORA_BLOQUEO.takeIf { administrador },
            venceEn = if (administrador) vence(entrada.duracion) else null,
            aplicadoEn = null,
            terminadoPorId = null,
            terminadoPor = null,
            terminadoEn = null,
            detalle = null,
        )
        bloqueos += nuevo
        return nuevo
    }

    override suspend fun aprobarBloqueo(id: String): BloqueoDto = cambiarBloqueo(id, setOf("pedido")) {
        it.copy(estado = "aprobado", aprobadoPorId = "d-1", aprobadoPor = "Este teléfono", aprobadoEn = HORA_BLOQUEO, venceEn = vence(it.duracion))
    }

    override suspend fun rechazarBloqueo(id: String, entrada: MotivoEntradaDto): BloqueoDto = cambiarBloqueo(id, setOf("pedido")) {
        motivosRecibidos += entrada.motivo
        it.copy(estado = "rechazado", terminadoPorId = "d-1", terminadoPor = "Este teléfono", terminadoEn = HORA_BLOQUEO, detalle = entrada.motivo)
    }

    override suspend fun desbloquear(id: String, entrada: MotivoEntradaDto): BloqueoDto = cambiarBloqueo(id, setOf("aprobado", "aplicado")) {
        motivosRecibidos += entrada.motivo
        it.copy(estado = "desbloqueado", terminadoPorId = "d-1", terminadoPor = "Este teléfono", terminadoEn = HORA_BLOQUEO, detalle = entrada.motivo)
    }

    override suspend fun cambiarDuracionBloqueo(id: String, entrada: DuracionEntradaDto): BloqueoDto = cambiarBloqueo(id, setOf("aprobado", "aplicado")) {
        motivosRecibidos += entrada.motivo
        it.copy(estado = "aprobado", duracion = entrada.duracion, venceEn = vence(entrada.duracion), aplicadoEn = null)
    }

    /** El firewall preguntó y dijo que aplicó lo aprobado. */
    fun aplicarBloqueosAprobados() {
        bloqueos.replaceAll { if (it.estado == "aprobado") it.copy(estado = "aplicado", aplicadoEn = HORA_BLOQUEO) else it }
    }

    private fun cambiarBloqueo(id: String, estados: Set<String>, cambio: (BloqueoDto) -> BloqueoDto): BloqueoDto {
        fallarSiCorresponde("cambiarBloqueo")
        val indice = bloqueos.indexOfFirst { it.id == id }
        if (indice < 0) throw errorHttp(404)
        if (bloqueos[indice].estado !in estados) throw errorHttp(409, """{"detail": "Ese bloqueo ya no está vigente"}""")
        bloqueos[indice] = cambio(bloqueos[indice])
        return bloqueos[indice]
    }

    private fun vence(duracion: Int?): String? = duracion?.let { OffsetDateTime.parse(HORA_BLOQUEO).plusSeconds(it.toLong()).toString() }

    private companion object {
        val NIVELES = listOf("baja", "media", "alta")
        val ACTIVOS = setOf("pedido", "aprobado", "aplicado")
        const val HORA_BLOQUEO = "2026-10-12T11:58:00+00:00"
    }
}

/** Entrega la misma API en memoria para cualquier red. */
class ProveedorDeApiFalso(private val api: ZondaApi) : ProveedorDeApi {

    val olvidadas = mutableListOf<String>()

    override fun para(red: Red): ZondaApi = api

    override fun para(redId: String, urlBase: String): ZondaApi = api

    override fun sinToken(urlBase: String): ZondaApi = api

    override fun olvidar(redId: String) {
        olvidadas += redId
    }
}
