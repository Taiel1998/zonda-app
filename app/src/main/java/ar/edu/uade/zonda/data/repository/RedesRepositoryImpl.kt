package ar.edu.uade.zonda.data.repository

import ar.edu.uade.zonda.data.local.dao.RedesDao
import ar.edu.uade.zonda.data.local.entidades.CatalogoEquipoEntity
import ar.edu.uade.zonda.data.local.entidades.CatalogoServicioEntity
import ar.edu.uade.zonda.data.local.entidades.RedEntity
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.mapper.aEntidades
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

/**
 * Redes pareadas sobre Room, con el token de cada una en el [AlmacenSeguro] (clave `token_<id>`),
 * nunca en la base ni en claro. Al quitar una red se borran su token y, por cascada, todo lo suyo.
 *
 * El pareo y el vínculo con Telegram hablan con el servidor y se completan en las fases 4, 7 y 10;
 * hasta entonces responden "sin conexión".
 */
class RedesRepositoryImpl(
    private val dao: RedesDao,
    private val almacen: AlmacenSeguro,
    private val reloj: () -> Instant = Instant::now,
) : RedesRepository {

    override fun observarRedes(): Flow<List<Red>> = dao.observar().map { filas -> filas.map(RedEntity::aDominio) }

    override fun observarRedActiva(): Flow<Red?> = dao.observarActiva().map { it?.aDominio() }

    override suspend fun activar(redId: String) = dao.activar(redId)

    override suspend fun parear(datos: DatosPareo, nombreDispositivo: String): Red {
        // Fase 4 y 7: GET /salud y POST /pareo con Retrofit; después guardarRedPareada.
        throw ErrorZonda.SinConexion()
    }

    /** Deja la red pareada y activa, con su token en el almacenamiento seguro. */
    suspend fun guardarRedPareada(red: Red, token: String) {
        dao.insertarComoActiva(red.aEntidad(creadaEn = reloj()))
        almacen.guardar(claveToken(red.id), token)
    }

    /** Token de la red, para el interceptor del cliente HTTP (fase 4). */
    fun tokenDe(redId: String): String? = almacen.leer(claveToken(redId))

    override suspend fun quitarDispositivo(redId: String) {
        // Fase 4: DELETE /dispositivos/actual antes de borrar lo local.
        dao.borrar(redId)
        almacen.borrar(claveToken(redId))
    }

    override fun observarSeveridades(redId: String): Flow<SeveridadPorTipo> =
        dao.observarSeveridades(redId).map { it.aDominio() }

    override suspend fun guardarSeveridadesLocal(redId: String, severidades: SeveridadPorTipo) =
        dao.guardarSeveridades(redId, severidades.aEntidades(redId))

    override suspend fun pedirVinculoTelegram(redId: String): VinculoTelegram {
        // Fase 10: POST /telegram/vinculo.
        throw ErrorZonda.SinConexion()
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

    /** Lo llama la sincronización al terminar (fase 4). */
    suspend fun actualizarSincronizacion(redId: String, cursor: String?, hora: Instant) =
        dao.actualizarSincronizacion(redId, cursor, hora.toEpochMilli())

    private fun claveToken(redId: String) = "token_$redId"
}
