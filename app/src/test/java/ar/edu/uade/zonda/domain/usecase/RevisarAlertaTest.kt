package ar.edu.uade.zonda.domain.usecase

import org.junit.Ignore
import org.junit.Test

/**
 * LOS ESCRIBE RODRIGO cuando Taiel implemente [RevisarAlerta]. Casos a cubrir (RF04):
 *
 * - con resultado, motivo y comentario válidos: la alerta queda con ese estado y una acción PENDIENTE
 *   de tipo REVISAR_ALERTA con `id = "accion-1"` (con [ar.edu.uade.zonda.domain.fakes.GeneradorDeIdsFijo]),
 *   `versionVista` igual a la versión de la alerta y la revisión con origen ESTE_TELEFONO;
 * - comentario vacío o solo espacios: lanza ErrorZonda.Validacion con campo "comentario" y no cambia nada;
 * - motivo de falso positivo con resultado ataque real (o al revés): lanza ErrorZonda.Validacion con campo "motivo";
 * - alerta inexistente: lanza ErrorZonda.NoEncontrado;
 * - revisar dos veces la misma alerta deja una sola acción en la cola (la segunda reemplaza a la primera);
 * - la fecha de la revisión sale del reloj inyectado.
 */
class RevisarAlertaTest {

    @Ignore("Pendiente: lo escribe Rodrigo cuando Taiel implemente RevisarAlerta")
    @Test
    fun `una revision valida deja la alerta revisada y una accion pendiente`() = Unit
}
