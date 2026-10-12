package ar.edu.uade.zonda.domain.model

/**
 * Severidad de una alerta. Define el color del semáforo (RF01) y cómo avisa la app (RF05).
 * Sale del tipo de ataque a través de [SeveridadPorTipo]; la elige cada red.
 */
enum class Severidad { ALTA, MEDIA, BAJA }
