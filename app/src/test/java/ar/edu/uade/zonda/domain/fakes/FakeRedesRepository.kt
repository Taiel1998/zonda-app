package ar.edu.uade.zonda.domain.fakes

import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.DetallesConexion
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoTelegram
import ar.edu.uade.zonda.domain.model.PreferenciasAvisos
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.RedPareada
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.VinculoTelegram
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Redes en memoria para las pruebas. */
class FakeRedesRepository(iniciales: List<Red> = emptyList()) : RedesRepository {

    private val redes = MutableStateFlow(iniciales)
    private val severidades = MutableStateFlow<Map<String, SeveridadPorTipo>>(emptyMap())
    private val catalogos = MutableStateFlow<Map<String, Catalogo>>(emptyMap())

    var vinculoTelegram = VinculoTelegram("ABCD-1234", "https://t.me/zonda_bot?start=ABCD-1234")
    val redesQuitadas = mutableListOf<String>()

    /** Direcciones verificadas y pareos pedidos, en orden, para que las pruebas vean qué se mandó. */
    val verificaciones = mutableListOf<String>()
    val pareos = mutableListOf<DatosPareo>()
    var errorAlVerificar: ErrorZonda? = null
    var errorAlParear: ErrorZonda? = null

    /** Si no es null, el pareo espera hasta que la prueba lo complete (para ver los pasos intermedios). */
    var esperaAlParear: CompletableDeferred<Unit>? = null

    /** Lo que devuelve [detallesDeConexion], o el error que lanza. */
    var detallesConexion = DetallesConexion(versionTls = "TLSv1.3", emisorCertificado = "Let's Encrypt", certificadoVence = null, accesoVence = null)
    var errorDetalles: ErrorZonda? = null

    override suspend fun detallesDeConexion(redId: String): DetallesConexion {
        errorDetalles?.let { throw it }
        return detallesConexion
    }

    override suspend fun verificarServidor(urlBase: String) {
        verificaciones += urlBase
        errorAlVerificar?.let { throw it }
    }

    val todas: List<Red>
        get() = redes.value

    fun agregar(red: Red) {
        redes.value = redes.value + red
    }

    /** Reemplaza la red con el mismo id, por ejemplo con la hora de su primera sincronización. */
    fun actualizar(red: Red) {
        redes.value = redes.value.map { if (it.id == red.id) red else it }
    }

    override fun observarRedes(): Flow<List<Red>> = redes

    override fun observarRedActiva(): Flow<Red?> = redes.map { lista -> lista.firstOrNull { it.activa } }

    override suspend fun activar(redId: String) {
        redes.value = redes.value.map { it.copy(activa = it.id == redId) }
    }

    /** Cuántas alertas de los últimos 30 días informa el pareo. */
    var alertasAlParear = 213

    override suspend fun parear(datos: DatosPareo, nombreDispositivo: String): RedPareada {
        pareos += datos
        esperaAlParear?.await()
        errorAlParear?.let { throw it }
        val red = Red(
            id = "red-${redes.value.size + 1}",
            nombre = "Red pareada",
            urlBase = datos.urlBase,
            cursor = null,
            ultimaSincronizacion = null,
            telegramVinculado = false,
            activa = true,
        )
        redes.value = redes.value.map { it.copy(activa = false) } + red
        return RedPareada(red, alertasParaDescargar = alertasAlParear)
    }

    override suspend fun quitarDispositivo(redId: String) {
        redesQuitadas += redId
        redes.value = redes.value.filter { it.id != redId }
    }

    override suspend fun marcarAccesoVencido(redId: String, vencido: Boolean) {
        redes.value = redes.value.map { if (it.id == redId) it.copy(accesoVencido = vencido) else it }
    }

    override suspend fun reiniciarSincronizacion(redId: String) {
        redes.value = redes.value.map { if (it.id == redId) it.copy(cursor = null, ultimaSincronizacion = null) else it }
    }

    override fun observarSeveridades(redId: String): Flow<SeveridadPorTipo> =
        severidades.map { it[redId] ?: SeveridadPorTipo.RECOMENDADA }

    override suspend fun guardarSeveridadesLocal(redId: String, severidades: SeveridadPorTipo) {
        this.severidades.value = this.severidades.value + (redId to severidades)
    }

    // ---- Telegram ----

    /** Lo que diría el servidor: la prueba lo pone en true como si la persona hubiera tocado Iniciar. */
    var telegramEnElServidor = false
    var botTelegram = "zonda_alertas_bot"
    var errorTelegram: ErrorZonda? = null
    val vinculosPedidos = mutableListOf<Pair<String, PreferenciasAvisos>>()
    val avisosEnviados = mutableListOf<Pair<String, PreferenciasAvisos>>()
    var consultasTelegram = 0
    var pruebasEnviadas = 0
    var desvinculos = 0

    override suspend fun pedirVinculoTelegram(redId: String, avisos: PreferenciasAvisos): VinculoTelegram {
        errorTelegram?.let { throw it }
        vinculosPedidos += redId to avisos
        return vinculoTelegram
    }

    override suspend fun consultarTelegram(redId: String): EstadoTelegram {
        consultasTelegram++
        errorTelegram?.let { throw it }
        redes.value = redes.value.map { if (it.id == redId) it.copy(telegramVinculado = telegramEnElServidor, telegramBot = botTelegram) else it }
        return EstadoTelegram(telegramEnElServidor, botTelegram, PreferenciasAvisos())
    }

    override suspend fun desvincularTelegram(redId: String) {
        errorTelegram?.let { throw it }
        desvinculos++
        telegramEnElServidor = false
        redes.value = redes.value.map { if (it.id == redId) it.copy(telegramVinculado = false) else it }
    }

    override suspend fun enviarAvisoDePrueba(redId: String) {
        errorTelegram?.let { throw it }
        pruebasEnviadas++
    }

    override suspend fun enviarAvisosTelegram(redId: String, avisos: PreferenciasAvisos) {
        errorTelegram?.let { throw it }
        avisosEnviados += redId to avisos
    }

    override fun observarCatalogo(redId: String): Flow<Catalogo> = catalogos.map { it[redId] ?: Catalogo.VACIO }
}
