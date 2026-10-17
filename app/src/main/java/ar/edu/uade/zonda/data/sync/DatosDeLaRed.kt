package ar.edu.uade.zonda.data.sync

import ar.edu.uade.zonda.data.local.dao.RedesDao
import ar.edu.uade.zonda.data.mapper.aDominio
import ar.edu.uade.zonda.data.mapper.aEntidad
import ar.edu.uade.zonda.data.remote.OperacionHttp
import ar.edu.uade.zonda.data.remote.ZondaApi
import ar.edu.uade.zonda.data.remote.conErroresTraducidos
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.Perfil

/**
 * La gente de la red y el registro de cambios, como los dice el servidor (decisiones 39 y 40). Lo usan
 * la sincronización y las pantallas de Personas después de un cambio. De paso deja al día el perfil de
 * este teléfono y su id en el servidor (la persona marcada como "vos").
 *
 * Un servidor anterior a la API 0.6.0 no tiene estas rutas (404): la red queda como antes, con este
 * teléfono de administrador y sin gente ni registro.
 */
internal suspend fun traerPersonasYRegistro(redId: String, api: ZondaApi, dao: RedesDao) {
    val personas = try {
        conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.personas() }.map { it.aDominio() }
    } catch (e: ErrorZonda.NoEncontrado) {
        return
    }
    dao.guardarPersonas(redId, personas.mapIndexed { orden, persona -> persona.aEntidad(redId, orden) })
    personas.firstOrNull { it.vos }?.let { yo -> dao.actualizarPerfil(redId, yo.perfil.name, yo.id) }
    val registro = conErroresTraducidos(OperacionHttp.SINCRONIZACION) { api.registro() }.map { it.aDominio() }
    dao.guardarRegistro(redId, registro.map { it.aEntidad(redId) })
}

/** El perfil con el que entró este teléfono, del pareo; sin el dato (servidor anterior), administrador. */
internal fun perfilDelPareo(perfil: String?): Perfil =
    perfil?.let { valor -> Perfil.entries.firstOrNull { it.name == valor.uppercase() } } ?: Perfil.ADMINISTRADOR
