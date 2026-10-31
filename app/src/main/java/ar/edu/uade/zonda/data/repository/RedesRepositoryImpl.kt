package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.local.dao.RedesDao
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.local.entidades.RedEntity
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aDto
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.mapper.aEntidades
import ar.edu.uade.zonda.data.mapper.aInstante
import ar.edu.uade.zonda.data.mapper.enLaApi
import ar.edu.uade.zonda.data.remote.CodigoPersonaEntradaDto
import ar.edu.uade.zonda.data.remote.OperacionHttp
import ar.edu.uade.zonda.data.remote.PareoEntradaDto
import ar.edu.uade.zonda.data.remote.PerfilEntradaDto
import ar.edu.uade.zonda.data.remote.ProveedorDeApi
import ar.edu.uade.zonda.data.remote.ProveedorDeApiRetrofit
import ar.edu.uade.zonda.data.remote.VinculoEntradaDto
import ar.edu.uade.zonda.data.remote.ZondaApi
import ar.edu.uade.zonda.data.remote.conErroresTraducidos
import ar.edu.uade.zonda.data.seguridad.AlmacenSeguro
import ar.edu.uade.zonda.data.sync.perfilDelPareo
import ar.edu.uade.zonda.data.sync.traerPersonasYRegistro
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.CambioRegistro
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.CodigoPersona
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.DetallesConexion
import ar.edu.uade.zonda.domain.model.EquipoDeLaRed
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoPieza
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
import ar.edu.uade.zonda.domain.repository.RedesRepository
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

/**
 * Redes pareadas sobre Room, con el token de cada una en el [AlmacenSeguro] (clave `token_<id>`),
 * nunca en la base ni en claro. Al quitar una red se borran su token y, por cascada, todo lo suyo.
 *
 * El pareo, quitar el dispositivo y el vínculo con Telegram hablan con el servidor a través del
 * [ProveedorDeApi]; las pruebas le pasan una API en memoria.
 */
class RedesRepositoryImpl(
    private val dao: RedesDao,
    private val almacen: AlmacenSeguro,
    private val apis: ProveedorDeApi = ProveedorDeApiRetrofit(tokens = { almacen.leer(claveToken(it)) }),
    private val reloj: () -> Instant = Instant::now,
) : RedesRepository {

    override fun observarRedes(): Flow<List<Red>> = dao.observar().map { filas -> filas.map(RedEntity::aDominio) }

    override fun observarRedActiva(): Flow<Red?> = dao.observarActiva().map { it?.aDominio() }

    override suspend fun activar(redId: String) = dao.activar(redId)

    override suspend fun verificarServidor(urlBase: String) {
        val api = try {
            apis.sinToken(urlBase)
        } catch (e: IllegalArgumentException) {
            throw ErrorZonda.Validacion("urlBase", "La dirección del servidor no es válida")
        }
        conErroresTraducidos(OperacionHttp.PAREO) { api.salud() }
    }

    /**
     * Primero `GET /salud` sin token, que verifica que el servidor responde y que su certificado es
     * válido antes de mandar el código; después `POST /pareo`, que entrega el token una sola vez.
     * Verifica aunque [ParearRed] ya lo haya hecho, para que nunca se mande el código a ciegas.
     */
    override suspend fun parear(datos: DatosPareo, nombreDispositivo: String): RedPareada {
        val api = try {
            apis.sinToken(datos.urlBase)
        } catch (e: IllegalArgumentException) {
            throw ErrorZonda.Validacion("urlBase", "La dirección del servidor no es válida")
        }
        conErroresTraducidos(OperacionHttp.PAREO) { api.salud() }
        val salida = conErroresTraducidos(OperacionHttp.PAREO) {
            api.parear(PareoEntradaDto(codigo = datos.codigo, nombreDispositivo = nombreDispositivo))
        }
        val red = Red(
            id = salida.red.id,
            nombre = salida.red.nombre,
            urlBase = datos.urlBase.trim().trimEnd('/'),
            cursor = null,
            ultimaSincronizacion = null,
            telegramVinculado = false,
            activa = true,
            accesoEmitido = salida.emitido?.aInstante() ?: reloj(),
            accesoVence = salida.vence.aInstante(),
            // Entra con el perfil del código: los de la terminal son de administrador (decisión 39).
            perfil = perfilDelPareo(salida.perfil),
            dispositivoId = salida.dispositivoId,
        )
        guardarRedPareada(red, salida.token)
        return RedPareada(
            red = dao.obtener(red.id)?.aDominio() ?: red,
            alertasParaDescargar = salida.alertas30Dias,
        )
    }

    /**
     * Deja la red pareada y activa, con su token en el almacenamiento seguro. Si la red ya estaba
     * (pareo repetido después de un 401), conserva sus alertas, su cursor y sus acciones pendientes.
     */
    suspend fun guardarRedPareada(red: Red, token: String) {
        if (dao.obtener(red.id) == null) {
            dao.insertarComoActiva(red.aEntidad(creadaEn = reloj()))
        } else {
            dao.actualizarDatosDePareo(red.id, red.nombre, red.urlBase, red.accesoEmitido?.toEpochMilli(), red.accesoVence?.toEpochMilli())
            dao.actualizarPerfil(red.id, red.perfil.name, red.dispositivoId)
            dao.activar(red.id)
        }
        almacen.guardar(claveToken(red.id), token)
    }

    /** Token de la red, para el interceptor del cliente HTTP. */
    fun tokenDe(redId: String): String? = almacen.leer(claveToken(redId))

    /**
     * Revoca el token en el servidor y borra todo lo local. Si no hay conexión o el token ya no vale,
     * se borra igual: el token del servidor vence solo a los 90 días. Si es el último administrador y
     * queda más gente, el servidor no lo deja (409) y no se borra nada (decisión 39).
     */
    override suspend fun quitarDispositivo(redId: String) {
        dao.obtener(redId)?.aDominio()?.let { red ->
            try {
                conErroresTraducidos(OperacionHttp.PERSONAS) {
                    val respuesta = apis.para(red).quitarDispositivo()
                    if (!respuesta.isSuccessful) throw HttpException(respuesta)
                }
            } catch (e: ErrorZonda.UltimoAdministrador) {
                throw e
            } catch (e: ErrorZonda) {
                // Se sigue con el borrado local.
            }
        }
        dao.borrar(redId)
        almacen.borrar(claveToken(redId))
        apis.olvidar(redId)
    }

    override suspend fun detallesDeConexion(redId: String): DetallesConexion {
        val red = dao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
        val respuesta = conErroresTraducidos {
            apis.para(red).saludConRespuesta().also { if (!it.isSuccessful) throw HttpException(it) }
        }
        val handshake = respuesta.raw().handshake
        val certificado = handshake?.peerCertificates?.firstOrNull() as? X509Certificate
        return DetallesConexion(
            versionTls = handshake?.tlsVersion?.javaName,
            emisorCertificado = certificado?.let { organizacion(it.issuerX500Principal.name) },
            certificadoVence = certificado?.notAfter?.toInstant(),
            accesoVence = respuesta.body()?.tokenVence?.aInstante() ?: red.accesoVence,
        )
    }

    override suspend fun estadoDelSistema(redId: String): EstadoSistema {
        val red = dao.obtener(redId)?.aDominio() ?: return EstadoSistema.sinRespuesta(reloj())
        return try {
            val api = apis.para(red)
            val salud = conErroresTraducidos { api.salud() }
            val horaServidor = runCatching { salud.hora.aInstante() }.getOrNull()
            val consultadoEn = reloj()
            val sistema = salud.sistema.aDominio(horaServidor = horaServidor, consultadoEn = consultadoEn)
            val firewall = salud.firewall?.takeIf { it.habilitado } ?: return sistema
            val corrimiento = horaServidor?.let { Duration.between(it, consultadoEn) } ?: Duration.ZERO
            sistema.copy(
                firewall = if (firewall.conectado) EstadoPieza.FUNCIONA else EstadoPieza.DETENIDO,
                ultimaConsultaFirewall = firewall.aDominio(corrimiento).ultimaConsulta,
                bloqueos = bloqueosAbiertos(api),
            )
        } catch (e: ErrorZonda.NoAutorizado) {
            // Contestó pero no acepta el token: el servidor funciona; el acceso vencido lo avisa la sincronización.
            EstadoSistema.sinRespuesta(reloj()).copy(servidor = EstadoPieza.FUNCIONA)
        } catch (e: ErrorZonda) {
            EstadoSistema.sinRespuesta(reloj())
        }
    }

    /** Los bloqueos y pedidos abiertos de la red; si no se pueden traer, la tarjeta sigue sin ellos. */
    private suspend fun bloqueosAbiertos(api: ZondaApi): List<Bloqueo> = try {
        conErroresTraducidos(OperacionHttp.BLOQUEO) { api.bloqueos() }.aDominio().bloqueos.filter { it.estado.activo }
    } catch (e: ErrorZonda) {
        emptyList()
    }

    override suspend fun marcarAccesoVencido(redId: String, vencido: Boolean) = dao.marcarAccesoVencido(redId, vencido)

    override suspend fun reiniciarSincronizacion(redId: String) = dao.reiniciarSincronizacion(redId)

    override fun observarSeveridades(redId: String): Flow<SeveridadPorTipo> =
        dao.observarSeveridades(redId).map { it.aDominio() }

    override suspend fun guardarSeveridadesLocal(redId: String, severidades: SeveridadPorTipo) =
        dao.guardarSeveridades(redId, severidades.aEntidades(redId))

    // ---- Telegram ----

    private suspend fun red(redId: String): Red = dao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()

    override suspend fun pedirVinculoTelegram(redId: String, avisos: PreferenciasAvisos): VinculoTelegram {
        val api = apis.para(red(redId))
        return conErroresTraducidos(OperacionHttp.TELEGRAM) { api.vincularTelegram(VinculoEntradaDto(avisos.aDto())) }.aDominio()
    }

    override suspend fun consultarTelegram(redId: String): EstadoTelegram {
        val api = apis.para(red(redId))
        val estado = conErroresTraducidos(OperacionHttp.TELEGRAM) { api.telegram() }.aDominio()
        dao.actualizarTelegram(redId, estado.vinculado, estado.bot)
        return estado
    }

    override suspend fun desvincularTelegram(redId: String) {
        val api = apis.para(red(redId))
        conErroresTraducidos(OperacionHttp.TELEGRAM) {
            val respuesta = api.desvincularTelegram()
            if (!respuesta.isSuccessful) throw HttpException(respuesta)
        }
        dao.marcarTelegram(redId, false)
    }

    override suspend fun enviarAvisoDePrueba(redId: String) {
        val api = apis.para(red(redId))
        try {
            conErroresTraducidos(OperacionHttp.TELEGRAM) {
                val respuesta = api.avisoDePrueba()
                if (!respuesta.isSuccessful) throw HttpException(respuesta)
            }
        } catch (e: ErrorZonda.TelegramSinVincular) {
            // El servidor ya no tiene el chat (por ejemplo, bloquearon el bot): la red queda sin Telegram.
            dao.marcarTelegram(redId, false)
            throw e
        }
    }

    override suspend fun enviarAvisosTelegram(redId: String, avisos: PreferenciasAvisos) {
        val api = apis.para(red(redId))
        conErroresTraducidos(OperacionHttp.TELEGRAM) { api.guardarAvisos(avisos.aDto()) }
    }

    override fun observarCatalogo(redId: String): Flow<Catalogo> =
        combine(dao.observarEquipos(redId), dao.observarServicios(redId)) { equipos, servicios ->
            Catalogo(
                equipos = equipos.associate { it.ip to it.nombre },
                servicios = servicios.map { it.servicio },
            )
        }

    // ---- Personas, equipos y registro de cambios (decisiones 39 a 41) ----

    override fun observarPersonas(redId: String): Flow<List<Persona>> =
        dao.observarPersonas(redId).map { filas -> filas.map { it.aDominio() } }

    override fun observarEquipos(redId: String): Flow<List<EquipoDeLaRed>> =
        dao.observarEquipos(redId).map { filas -> filas.map { it.aDominio() }.sortedWith(compareBy({ it.nombre }, { it.ip })) }

    override fun observarRegistro(redId: String): Flow<List<CambioRegistro>> =
        dao.observarRegistro(redId).map { filas -> filas.map { it.aDominio() } }

    override suspend fun crearCodigoPersona(redId: String, nombre: String, perfil: Perfil): CodigoPersona {
        val api = apis.para(red(redId))
        return conErroresTraducidos(OperacionHttp.PERSONAS) {
            api.codigoParaPersona(CodigoPersonaEntradaDto(nombre = nombre.trim(), perfil = perfil.enLaApi()))
        }.aDominio()
    }

    override suspend fun cambiarPerfil(redId: String, personaId: String, perfil: Perfil) {
        val api = apis.para(red(redId))
        conErroresTraducidos(OperacionHttp.PERSONAS) { api.cambiarPerfil(personaId, PerfilEntradaDto(perfil.enLaApi())) }
        actualizarPersonasYRegistro(redId)
    }

    override suspend fun quitarPersona(redId: String, personaId: String) {
        val api = apis.para(red(redId))
        conErroresTraducidos(OperacionHttp.PERSONAS) {
            val respuesta = api.quitarPersona(personaId)
            if (!respuesta.isSuccessful) throw HttpException(respuesta)
        }
        actualizarPersonasYRegistro(redId)
    }

    override suspend fun actualizarPersonasYRegistro(redId: String) =
        traerPersonasYRegistro(redId, apis.para(red(redId)), dao)

    override suspend fun guardarImportanciaLocal(redId: String, ip: String, importancia: Importancia) =
        dao.actualizarImportancia(redId, ip, importancia.name)

    suspend fun guardarCatalogo(redId: String, catalogo: Catalogo) = dao.guardarCatalogo(
        redId = redId,
        equipos = catalogo.equipos.map { (ip, nombre) -> CatalogoEquipoEntity(redId, ip, nombre) },
        servicios = catalogo.servicios.map { CatalogoServicioEntity(redId, it) },
    )

    /** Lo llama la sincronización al terminar. */
    suspend fun actualizarSincronizacion(redId: String, cursor: String?, hora: Instant) =
        dao.actualizarSincronizacion(redId, cursor, hora.toEpochMilli())

    companion object {
        /** Clave con la que se guarda el token de cada red en el almacenamiento seguro. */
        fun claveToken(redId: String) = "token_$redId"

        /**
         * La organización de un nombre X.500 ("CN=R11,O=Let's Encrypt,C=US" da "Let's Encrypt"); si no
         * tiene, el CN. Las comas dentro de un valor vienen escapadas ("O=Ejemplo\, Inc.").
         */
        fun organizacion(nombreX500: String): String? {
            val partes = nombreX500.split(Regex("""(?<!\\),""")).map { it.trim() }
            fun valor(clave: String) = partes.firstOrNull { it.startsWith("$clave=") }?.substringAfter("=")?.replace("\\,", ",")
            return valor("O") ?: valor("CN")
        }
    }
}
