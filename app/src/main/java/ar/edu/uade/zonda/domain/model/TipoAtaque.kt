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
    SOLO_REGLAS_O_UMBRALES;

    companion object {
        /**
         * Lo que detectó la IA: el modelo vio algo raro y el clasificador le puso el tipo (cambios 8 y 14).
         * En este orden van en la pantalla de severidades, en "Por tipo de ataque" del Resumen y en la
         * hoja "Qué es cada tipo".
         */
        val DETECTADOS_POR_LA_IA = listOf(FUERZA_BRUTA, SIN_TIPIFICAR, ESCANEO, DDOS, PROBABLE_FALSA_ALARMA)

        /** Lo que la IA no detectó: lo marcaron los otros dos detectores. */
        val NO_DETECTADOS_POR_LA_IA = listOf(SOLO_REGLAS_O_UMBRALES)

        /** Los dos bloques seguidos: el orden de todas las pantallas. */
        val EN_PANTALLA = DETECTADOS_POR_LA_IA + NO_DETECTADOS_POR_LA_IA
    }
}
