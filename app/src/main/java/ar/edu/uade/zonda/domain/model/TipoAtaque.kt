package ar.edu.uade.zonda.domain.model

/**
 * Tipo que la plataforma de detección asigna a cada alerta.
 *
 * El tipo ordena la cola de atención y define la severidad recomendada; nunca oculta una alerta
 * (docs/decisiones.md, decisión 4).
 */
enum class TipoAtaque {
    FUERZA_BRUTA,
    ESCANEO,
    DDOS,

    /** El clasificador no reunió mayoría de votos: puede ser un ataque nuevo, por eso se recomienda alta. */
    SIN_TIPIFICAR,

    /** Se parece a tráfico legítimo. Se muestra igual, al final de la cola, con severidad baja. */
    PROBABLE_FALSA_ALARMA,

    /** La detectaron solo las reglas o los umbrales; el clasificador no corrió. La API manda `tipoAtaque: null`. */
    SOLO_REGLAS_O_UMBRALES,
}
