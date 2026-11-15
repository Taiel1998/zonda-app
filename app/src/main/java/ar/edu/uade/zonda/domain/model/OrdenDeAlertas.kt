package ar.edu.uade.zonda.domain.model

/**
 * Las sin revisar van siempre con las altas primero: altas, medias y bajas, y dentro de cada severidad de la
 * más reciente a la más vieja (decisión 63). Son una cola de trabajo, y lo urgente queda arriba aunque una baja
 * sea más nueva. Lo usan Estado y la lista de Alertas con el filtro «Sin revisar», igual que el tablero; todo
 * lo demás va por fecha, porque es el historial.
 */
fun List<Alerta>.enOrdenDePrioridad(): List<Alerta> =
    sortedWith(compareBy<Alerta> { it.severidad }.thenByDescending { it.inicio })
