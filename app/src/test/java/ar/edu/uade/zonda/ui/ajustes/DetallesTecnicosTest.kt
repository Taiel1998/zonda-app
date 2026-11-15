package ar.edu.uade.zonda.ui.ajustes

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import ar.edu.uade.zonda.domain.Fixtures
import java.time.Duration
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que dice Detalles técnicos del vencimiento del acceso. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetallesTecnicosTest {

    private val recursos: Resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val utc = ZoneOffset.UTC

    /** Lunes 12/10/2026 al mediodía. */
    private val ahora = Fixtures.AHORA

    private fun vencimiento(falta: Duration, vencido: Boolean = false) = textoDeVencimiento(ahora.plus(falta), ahora, vencido, recursos, utc)

    @Test
    fun `el acceso dice los dias que faltan para que venza, no los que dura`() {
        // Un acceso de 90 días emitido hace 78: antes decía "(90 días)", que se leía como lo que quedaba.
        assertEquals("24/10/2026 · faltan 12 días", vencimiento(Duration.ofDays(12).plusHours(3)))
        assertEquals("13/10/2026 · falta 1 día", vencimiento(Duration.ofDays(1).plusHours(2)))
        assertEquals("12/10/2026 · falta menos de un día", vencimiento(Duration.ofHours(5)))
        // Vencido, solo la fecha: que venció lo dice la fila "Estado".
        assertEquals("11/10/2026", vencimiento(Duration.ofDays(-1), vencido = true))
    }
}
