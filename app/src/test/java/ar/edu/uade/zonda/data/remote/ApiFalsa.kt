package ar.edu.uade.zonda.data.remote

import ar.edu.uade.zonda.domain.model.Red
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

    /** Por operación (`salud`, `alertas`, `alerta`, `revisar`, `severidades`, `catalogo`, `parear`, `vinculo`, `quitar`): qué lanzar. */
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
        "solo_reglas_o_umbrales" to "media",
        "probable_falsa_alarma" to "baja",
    )
    var catalogo = CatalogoDto(equipos = mapOf("192.168.10.50" to "srv-web", "192.168.10.9" to "pc-09"), servicios = listOf("smb", "ssh"))
    val codigosDePareo = mutableSetOf<String>()
    val codigosUsados = mutableSetOf<String>()
    var dispositivosPareados = 0
    var tokenRevocado = false
    var vinculo = VinculoTelegramDto("ABCD-EFGH", "https://t.me/zonda_alertas_bot?start=ABCD-EFGH", null)

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

    override suspend fun salud(): SaludDto {
        fallarSiCorresponde("salud")
        return SaludDto("0.3.0", "2026-10-12T04:05:29+00:00", alertas.size, RedDto(redId, redNombre), null)
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

    override suspend fun guardarSeveridades(severidades: Map<String, String>): Map<String, String> {
        fallarSiCorresponde("severidades")
        this.severidades.putAll(severidades)
        // Como la API: la severidad sale del tipo y todas las alertas vuelven a salir en la sincronización.
        alertas.values.toList().forEach { alerta ->
            publicar(alerta.copy(severidad = this.severidades.getValue(alerta.tipoAtaque ?: "solo_reglas_o_umbrales")))
        }
        return this.severidades.toMap()
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

    override suspend fun vincularTelegram(): VinculoTelegramDto {
        fallarSiCorresponde("vinculo")
        return vinculo
    }

    override suspend fun quitarDispositivo(): Response<Unit> {
        fallarSiCorresponde("quitar")
        tokenRevocado = true
        return Response.success(204, Unit)
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
