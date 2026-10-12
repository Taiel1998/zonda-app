package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.local.dao.RedesDao
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.local.entidades.RedEntity
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.mapper.aEntidades
import ar.edu.uade.zonda.data.remote.OperacionHttp
import ar.edu.uade.zonda.data.remote.PareoEntradaDto
import ar.edu.uade.zonda.data.remote.ProveedorDeApi
import ar.edu.uade.zonda.data.remote.ProveedorDeApiRetrofit
import ar.edu.uade.zonda.data.remote.conErroresTraducidos
import ar.edu.uade.zonda.data.seguridad.AlmacenSeguro
import ar.edu.uade.zonda.domain.model.Catalogo
import ar.edu.uade.zonda.domain.model.DatosPareo
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Red
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.model.VinculoTelegram
import ar.edu.uade.zonda.domain.repository.RedesRepository
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
    override suspend fun parear(datos: DatosPareo, nombreDispositivo: String): Red {
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
        )
        guardarRedPareada(red, salida.token)
        return dao.obtener(red.id)?.aDominio() ?: red
    }

    /**
     * Deja la red pareada y activa, con su token en el almacenamiento seguro. Si la red ya estaba
     * (pareo repetido después de un 401), conserva sus alertas, su cursor y sus acciones pendientes.
     */
    suspend fun guardarRedPareada(red: Red, token: String) {
        if (dao.obtener(red.id) == null) {
            dao.insertarComoActiva(red.aEntidad(creadaEn = reloj()))
        } else {
            dao.actualizarDatosDePareo(red.id, red.nombre, red.urlBase)
            dao.activar(red.id)
        }
        almacen.guardar(claveToken(red.id), token)
    }

    /** Token de la red, para el interceptor del cliente HTTP. */
    fun tokenDe(redId: String): String? = almacen.leer(claveToken(redId))

    /**
     * Revoca el token en el servidor y borra todo lo local. Si no hay conexión o el token ya no vale,
     * se borra igual: el token del servidor vence solo a los 90 días.
     */
    override suspend fun quitarDispositivo(redId: String) {
        dao.obtener(redId)?.aDominio()?.let { red ->
            try {
                conErroresTraducidos {
                    val respuesta = apis.para(red).quitarDispositivo()
                    if (!respuesta.isSuccessful) throw HttpException(respuesta)
                }
            } catch (e: ErrorZonda) {
                // Se sigue con el borrado local.
            }
        }
        dao.borrar(redId)
        almacen.borrar(claveToken(redId))
        apis.olvidar(redId)
    }

    override suspend fun marcarAccesoVencido(redId: String, vencido: Boolean) = dao.marcarAccesoVencido(redId, vencido)

    override suspend fun reiniciarSincronizacion(redId: String) = dao.reiniciarSincronizacion(redId)

    override fun observarSeveridades(redId: String): Flow<SeveridadPorTipo> =
        dao.observarSeveridades(redId).map { it.aDominio() }

    override suspend fun guardarSeveridadesLocal(redId: String, severidades: SeveridadPorTipo) =
        dao.guardarSeveridades(redId, severidades.aEntidades(redId))

    override suspend fun pedirVinculoTelegram(redId: String): VinculoTelegram {
        val red = dao.obtener(redId)?.aDominio() ?: throw ErrorZonda.NoEncontrado()
        return conErroresTraducidos { apis.para(red).vincularTelegram() }.aDominio()
    }

    override suspend fun marcarTelegramVinculado(redId: String, vinculado: Boolean) = dao.marcarTelegram(redId, vinculado)

    override fun observarCatalogo(redId: String): Flow<Catalogo> =
        combine(dao.observarEquipos(redId), dao.observarServicios(redId)) { equipos, servicios ->
            Catalogo(
                equipos = equipos.associate { it.ip to it.nombre },
                servicios = servicios.map { it.servicio },
            )
        }

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
    }
}
