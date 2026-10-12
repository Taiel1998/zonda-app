package ar.edu.uade.zonda.domain.model

import java.time.Instant

/** Color del semáforo de la pantalla Estado (RF01). */
enum class Semaforo { ROJO, AMARILLO, VERDE, SIN_DATOS }

/** Alertas sin revisar por severidad. Son los tres contadores de la pantalla Estado. */
data class ContadoresSinRevisar(
    val altas: Int,
    val medias: Int,
    val bajas: Int,
) {
    val total: Int
        get() = altas + medias + bajas

    fun de(severidad: Severidad): Int = when (severidad) {
        Severidad.ALTA -> altas
        Severidad.MEDIA -> medias
        Severidad.BAJA -> bajas
    }

    companion object {
        val CERO = ContadoresSinRevisar(0, 0, 0)
    }
}

/** Lo que muestra la pantalla Estado para la red activa. */
data class EstadoRed(
    val red: Red,
    val semaforo: Semaforo,
    val sinRevisar: ContadoresSinRevisar,
    /** Hora de los datos que se están mostrando ("actualizado hace X"); vacía si nunca se sincronizó. */
    val datosDe: Instant?,
)
