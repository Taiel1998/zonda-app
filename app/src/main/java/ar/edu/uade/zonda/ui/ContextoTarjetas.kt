package ar.edu.uade.zonda.ui

import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.CalculoSeveridad
import ar.edu.uade.zonda.domain.model.Importancia
import ar.edu.uade.zonda.domain.model.Persona
import ar.edu.uade.zonda.domain.model.SeveridadPorTipo
import ar.edu.uade.zonda.domain.repository.RedesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Lo de la red que suma cada tarjeta de alerta desde la 1.0.5 (decisiones 41 y 42; Figma 468-1454): quién
 * es este teléfono, para el chip "Vos", y la importancia de los equipos, para la línea "Subió de media a
 * alta: srv-facturacion es crítico.". La gente va también para el filtro Responsable.
 */
data class ContextoTarjetas(
    val miId: String? = null,
    val severidades: SeveridadPorTipo = SeveridadPorTipo.RECOMENDADA,
    val importancias: Map<String, Importancia> = emptyMap(),
    /** Vos primero, después los administradores y los analistas. */
    val personas: List<Persona> = emptyList(),
) {
    /** El cálculo de la severidad si el equipo destino no es normal; si no, null y la tarjeta no suma la línea. */
    fun calculo(alerta: Alerta): CalculoSeveridad? =
        CalculoSeveridad.de(alerta, severidades, importancias).takeIf { it.conImportancia }

    /** La alerta la tiene este teléfono. */
    fun esMia(alerta: Alerta): Boolean = miId != null && alerta.asignacion?.responsableId == miId
}

/** El [ContextoTarjetas] de la red activa, que cambia solo con cada sincronización. */
@OptIn(ExperimentalCoroutinesApi::class)
fun RedesRepository.observarContextoTarjetas(): Flow<ContextoTarjetas> = observarRedActiva().flatMapLatest { red ->
    if (red == null) {
        flowOf(ContextoTarjetas())
    } else {
        combine(observarSeveridades(red.id), observarEquipos(red.id), observarPersonas(red.id)) { severidades, equipos, personas ->
            ContextoTarjetas(
                miId = red.dispositivoId ?: personas.firstOrNull { it.vos }?.id,
                severidades = severidades,
                importancias = equipos.associate { it.ip to it.importancia },
                personas = Persona.ordenar(personas),
            )
        }
    }
}
