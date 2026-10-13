package ar.edu.uade.zonda.data.sync

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.ProgresoDescarga
import ar.edu.uade.zonda.domain.repository.ResultadoSincronizacion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lo que el repositorio de alertas le pide a la red. Es una interfaz para que las pruebas de Room
 * (fase 2) no necesiten HTTP; la implementación real es [SincronizadorHttp].
 */
interface Sincronizador {

    /** La primera descarga en curso ("120 de 213"), o null si no hay ninguna. */
    val progresoDescarga: StateFlow<ProgresoDescarga?>

    suspend fun sincronizar(redId: String): ResultadoSincronizacion

    suspend fun descargarDetalle(redId: String, alertaId: String): Alerta
}

/** Sin servidor: todo responde "sin conexión". Lo usan las pruebas que solo miran la base local. */
object SinSincronizador : Sincronizador {

    override val progresoDescarga: StateFlow<ProgresoDescarga?> = MutableStateFlow(null)

    override suspend fun sincronizar(redId: String): ResultadoSincronizacion = throw ErrorZonda.SinConexion()

    override suspend fun descargarDetalle(redId: String, alertaId: String): Alerta = throw ErrorZonda.SinConexion()
}
