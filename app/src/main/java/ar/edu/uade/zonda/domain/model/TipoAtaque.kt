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

    /**
     * La IA no la vio y la marcaron las reglas: una conexión a un servicio que no está en la lista de
     * permitidos (cambio 9). La API manda `tipoAtaque: null` y el veredicto de las reglas.
     */
    SERVICIO_NO_AUTORIZADO,

    /** La IA no la vio y la marcaron los umbrales: demasiadas conexiones a un mismo servicio en un minuto (cambio 9). */
    VOLUMEN_INUSUAL;

    companion object {
        /**
         * Lo que detectó la IA: el modelo vio algo raro y el clasificador le puso el tipo (cambios 8 y 14).
         * En este orden van en la pantalla de severidades, en "Por tipo de ataque" del Resumen y en la
         * hoja "Qué es cada tipo".
         */
        val DETECTADOS_POR_LA_IA = listOf(FUERZA_BRUTA, SIN_TIPIFICAR, ESCANEO, DDOS, PROBABLE_FALSA_ALARMA)

        /** Lo que la IA no detectó: lo marcaron los otros dos detectores, las reglas o los umbrales (cambio 9). */
        val NO_DETECTADOS_POR_LA_IA = listOf(SERVICIO_NO_AUTORIZADO, VOLUMEN_INUSUAL)

        /** Los dos bloques seguidos: el orden de todas las pantallas. */
        val EN_PANTALLA = DETECTADOS_POR_LA_IA + NO_DETECTADOS_POR_LA_IA

        /**
         * El tipo de una alerta que la IA no vio (la API manda `tipoAtaque: null`): el del detector que la
         * marcó. Si la vieron los dos se guarda como de las reglas, y [Alerta.tiposDeAtaque] la cuenta en
         * los dos (cambio 9).
         */
        fun sinLaIa(reglas: Boolean, umbrales: Boolean): TipoAtaque =
            if (umbrales && !reglas) VOLUMEN_INUSUAL else SERVICIO_NO_AUTORIZADO
    }
}
