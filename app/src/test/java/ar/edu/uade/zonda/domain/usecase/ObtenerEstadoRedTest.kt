package ar.edu.uade.zonda.domain.usecase

import org.junit.Ignore
import org.junit.Test

/**
 * LOS ESCRIBE RODRIGO cuando Taiel implemente [ObtenerEstadoRed]. Casos a cubrir (RF01):
 *
 * - sin red pareada emite null;
 * - red que nunca se sincronizó: semáforo SIN_DATOS aunque no haya alertas (nunca verde sin datos);
 * - una alta sin revisar: ROJO, aunque haya medias y bajas;
 * - sin altas y con una media sin revisar: AMARILLO;
 * - solo bajas sin revisar: VERDE (las bajas no cambian el semáforo);
 * - una alta revisada no cuenta: si no queda ninguna sin revisar, VERDE;
 * - al revisar la última alta, el semáforo pasa de ROJO a AMARILLO o VERDE sin volver a llamar al caso de uso (es un Flow);
 * - `datosDe` es la última sincronización de la red.
 *
 * Usar [ar.edu.uade.zonda.domain.fakes.FakeRedesRepository], [ar.edu.uade.zonda.domain.fakes.FakeAlertasRepository],
 * [ar.edu.uade.zonda.domain.Fixtures] y Turbine, como en [ObservarAlertasTest].
 */
class ObtenerEstadoRedTest {

    @Ignore("Pendiente: lo escribe Rodrigo cuando Taiel implemente ObtenerEstadoRed")
    @Test
    fun `una alta sin revisar pone el semaforo en rojo`() = Unit
}
