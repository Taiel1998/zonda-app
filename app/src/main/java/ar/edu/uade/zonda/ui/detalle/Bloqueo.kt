package ar.edu.uade.zonda.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.Bloqueo
import ar.edu.uade.zonda.domain.model.DuracionBloqueo
import ar.edu.uade.zonda.domain.model.EstadoBloqueo
import ar.edu.uade.zonda.domain.model.Perfil
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.components.BotonChico
import ar.edu.uade.zonda.ui.components.BotonContorno
import ar.edu.uade.zonda.ui.components.BotonPrincipal
import ar.edu.uade.zonda.ui.components.CampoTexto
import ar.edu.uade.zonda.ui.components.HojaInferior
import ar.edu.uade.zonda.ui.components.LeyendaObligatorio
import ar.edu.uade.zonda.ui.components.MarcaRadio
import ar.edu.uade.zonda.ui.components.Panel
import ar.edu.uade.zonda.ui.components.RellenoBotonAngosto
import ar.edu.uade.zonda.ui.components.TituloSeccion
import ar.edu.uade.zonda.ui.nombreCorto
import ar.edu.uade.zonda.ui.theme.Tonos
import ar.edu.uade.zonda.ui.theme.nota
import ar.edu.uade.zonda.ui.theme.tituloHoja
import java.time.Instant
import java.time.ZoneId

/**
 * "Bloqueo" (decisión 51), debajo del responsable y con su misma forma: cómo está el origen de la alerta en
 * el firewall y el botón que le toca a cada perfil. Sin bloquear: "Bloquear" para un administrador y "Pedir
 * bloqueo" para un analista. Bloqueado: hasta cuándo, y para el administrador "Cambiar duración" y
 * "Desbloquear". Pedido: quién lo pidió y por qué, y para el administrador "Rechazar" y "Aprobar".
 */
@Composable
fun TarjetaBloqueo(
    seccion: SeccionBloqueo,
    alerta: Alerta,
    perfil: Perfil,
    zona: ZoneId,
    ahora: Instant,
    acciones: AccionesDetalle,
) {
    val actual = seccion.actual
    val administrador = perfil == Perfil.ADMINISTRADOR
    val propuesto = motivoPropuesto(alerta)
    Panel {
        TituloSeccion(stringResource(R.string.bloqueo_titulo))
        when {
            actual == null -> {
                val aviso = when {
                    seccion.nuncaSeBloquea -> stringResource(R.string.bloqueo_nunca)
                    !seccion.firewall.conectado -> stringResource(R.string.bloqueo_firewall_no_responde)
                    else -> null
                }
                FilaBloqueo(Icons.Outlined.LockOpen, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.bloqueo_sin_bloquear), aviso) {
                    if (!seccion.nuncaSeBloquea) {
                        val texto = stringResource(if (administrador) R.string.bloqueo_bloquear else R.string.bloqueo_pedir)
                        BotonChico(texto, { acciones.alBloquear(propuesto) })
                    }
                }
            }
            actual.estado == EstadoBloqueo.PEDIDO -> {
                val cuando = "${Formato.diaRelativo(actual.pedidoEn, ahora, zona)} ${Formato.hora(actual.pedidoEn, zona)}"
                FilaBloqueo(
                    icono = Icons.Outlined.HourglassEmpty,
                    color = Tonos.Media.texto,
                    titulo = stringResource(R.string.bloqueo_pedido, textoDuracionBloqueo(actual.segundos)),
                    detalle = stringResource(R.string.bloqueo_lo_pidio, actual.pedidoPor, cuando),
                )
                Text(text = stringResource(R.string.registro_motivo, actual.motivo), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (administrador) {
                    BotonesBloqueo(
                        primero = stringResource(R.string.bloqueo_rechazar) to acciones.alRechazarBloqueo,
                        segundo = stringResource(R.string.bloqueo_aprobar) to acciones.alAprobarBloqueo,
                    )
                } else {
                    Text(text = stringResource(R.string.bloqueo_espera_administrador), style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                val detalle = when {
                    !seccion.firewall.conectado -> stringResource(R.string.bloqueo_firewall_no_responde)
                    actual.estado == EstadoBloqueo.APROBADO -> stringResource(R.string.bloqueo_esperando_firewall)
                    else -> actual.aprobadoEn?.let { aprobado ->
                        val cuando = "${Formato.diaRelativo(aprobado, ahora, zona)} ${Formato.hora(aprobado, zona)}"
                        stringResource(R.string.bloqueo_lo_bloqueo, actual.aprobadoPor.orEmpty(), cuando)
                    }
                }
                FilaBloqueo(Icons.Outlined.Lock, MaterialTheme.colorScheme.error, tituloBloqueado(actual, ahora, zona), detalle)
                if (administrador) {
                    BotonesBloqueo(
                        primero = stringResource(R.string.bloqueo_cambiar_duracion) to acciones.alCambiarDuracionBloqueo,
                        segundo = stringResource(R.string.bloqueo_desbloquear) to acciones.alDesbloquear,
                    )
                }
            }
        }
    }
}

/** El ícono, el título y el dato de la tarjeta, con el botón chico a la derecha si hay. */
@Composable
private fun FilaBloqueo(
    icono: ImageVector,
    color: Color,
    titulo: String,
    detalle: String?,
    boton: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(imageVector = icono, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = titulo, style = MaterialTheme.typography.titleSmall)
            if (detalle != null) {
                Text(text = detalle, style = MaterialTheme.typography.nota, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        boton()
    }
}

@Composable
private fun BotonesBloqueo(primero: Pair<String, () -> Unit>, segundo: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
        BotonChico(primero.first, primero.second)
        BotonChico(segundo.first, segundo.second)
    }
}

/** "Bloqueado · vence 13:34", con el día si no es hoy, o "Bloqueado hasta desbloquear". */
@Composable
private fun tituloBloqueado(bloqueo: Bloqueo, ahora: Instant, zona: ZoneId): String {
    val vence = bloqueo.venceEn ?: return stringResource(R.string.bloqueo_hasta_desbloquear_titulo)
    val cuando = if (Formato.mismoDia(vence, ahora, zona)) Formato.hora(vence, zona) else Formato.fechaYHora(vence, zona)
    return stringResource(R.string.bloqueo_vence, cuando)
}

/** "15 min", "1 h", "8 h", "24 h" o "hasta desbloquear": la duración en la tarjeta y en el registro de cambios. */
@Composable
fun textoDuracionBloqueo(segundos: Int?): String = when {
    segundos == null -> stringResource(R.string.bloqueo_hasta_desbloquear)
    segundos % 3600 == 0 -> stringResource(R.string.bloqueo_horas, segundos / 3600)
    else -> stringResource(R.string.bloqueo_minutos, (segundos + 59) / 60)
}

/**
 * El motivo que la hoja trae escrito, en forma impersonal como los comentarios de la revisión: "Fuerza bruta
 * de pc-rrhh a srv-sueldos." Si la alerta no tiene un tipo de ataque, "Alerta de pc-rrhh a srv-sueldos."
 */
@Composable
private fun motivoPropuesto(alerta: Alerta): String {
    val origen = alerta.origen.etiqueta
    val destino = alerta.destino.etiqueta
    return when (alerta.tipoAtaque) {
        TipoAtaque.SIN_TIPIFICAR, TipoAtaque.PROBABLE_FALSA_ALARMA -> stringResource(R.string.bloqueo_motivo_propuesto_alerta, origen, destino)
        else -> stringResource(R.string.bloqueo_motivo_propuesto, stringResource(alerta.tipoAtaque.nombreCorto()), origen, destino)
    }
}

/**
 * La hoja del bloqueo: qué se corta, la duración (al bloquear y al cambiarla), el motivo obligatorio y
 * "Cancelar" o la acción. Cerrarla es Cancelar.
 */
@Composable
fun HojaBloqueo(formulario: FormularioBloqueo, alerta: Alerta, perfil: Perfil, acciones: AccionesDetalle) {
    HojaInferior(alCerrar = acciones.alCerrarBloqueo) {
        ContenidoHojaBloqueo(formulario, alerta, perfil, acciones)
    }
}

/** El contenido de [HojaBloqueo], aparte para dibujarlo en las pruebas sin la ventana de la hoja. */
@Composable
fun ColumnScope.ContenidoHojaBloqueo(formulario: FormularioBloqueo, alerta: Alerta, perfil: Perfil, acciones: AccionesDetalle) {
    val equipo = alerta.origen.etiqueta
    val analista = perfil == Perfil.ANALISTA
    val (titulo, confirmar, ejemplo) = when (formulario.accion) {
        AccionBloqueo.BLOQUEAR -> if (analista) {
            Triple(stringResource(R.string.bloqueo_hoja_pedir, equipo), stringResource(R.string.bloqueo_pedir), stringResource(R.string.bloqueo_ejemplo_bloquear))
        } else {
            Triple(stringResource(R.string.bloqueo_hoja_bloquear, equipo), stringResource(R.string.bloqueo_bloquear), stringResource(R.string.bloqueo_ejemplo_bloquear))
        }
        AccionBloqueo.CAMBIAR_DURACION -> Triple(stringResource(R.string.bloqueo_hoja_duracion, equipo), stringResource(R.string.bloqueo_guardar), stringResource(R.string.bloqueo_ejemplo_duracion))
        AccionBloqueo.DESBLOQUEAR -> Triple(stringResource(R.string.bloqueo_hoja_desbloquear, equipo), stringResource(R.string.bloqueo_desbloquear), stringResource(R.string.bloqueo_ejemplo_desbloquear))
        AccionBloqueo.RECHAZAR -> Triple(stringResource(R.string.bloqueo_hoja_rechazar, equipo), stringResource(R.string.bloqueo_rechazar), stringResource(R.string.bloqueo_ejemplo_rechazar))
    }
    Text(text = titulo, style = MaterialTheme.typography.tituloHoja)
    // Hasta desbloquear se puede elegir siempre; la línea pasa a ámbar para que se vea que no vence solo.
    val sinVencimiento = formulario.conDuracion && formulario.duracion == DuracionBloqueo.HASTA_DESBLOQUEAR
    queSeCorta(formulario, alerta)?.let {
        Text(text = it, style = MaterialTheme.typography.bodyMedium, color = if (sinVencimiento) Tonos.Media.texto else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (formulario.conDuracion) {
        TituloSeccion(stringResource(if (formulario.accion == AccionBloqueo.CAMBIAR_DURACION) R.string.bloqueo_cuanto_desde_ahora else R.string.bloqueo_cuanto))
        Column(modifier = Modifier.selectableGroup()) {
            DuracionBloqueo.entries.forEach { duracion ->
                FilaDuracion(duracion = duracion, elegida = formulario.duracion == duracion, alElegir = { acciones.alElegirDuracion(duracion) })
            }
        }
    }
    CampoTexto(
        valor = formulario.motivo,
        alCambiar = acciones.alCambiarMotivoBloqueo,
        etiqueta = stringResource(R.string.motivo_campo),
        ejemplo = ejemplo,
        ayuda = stringResource(R.string.motivo_ayuda),
        error = if (formulario.conError) stringResource(R.string.bloqueo_motivo_falta) else null,
        lineasMinimas = 2,
        ayudaSiempre = true,
        obligatorio = true,
    )
    LeyendaObligatorio()
    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonContorno(texto = stringResource(R.string.revisar_cancelar), alTocar = acciones.alCerrarBloqueo, modifier = Modifier.weight(1f), relleno = RellenoBotonAngosto)
        BotonPrincipal(texto = confirmar, alTocar = acciones.alConfirmarBloqueo, modifier = Modifier.weight(1f), habilitado = !formulario.enviando, cargando = formulario.enviando)
    }
}

/**
 * Lo que la persona corta o devuelve, con el equipo y su dirección: "pc-rrhh (192.168.30.50) no va a llegar
 * a otras áreas ni a internet durante 1 hora." El tráfico dentro de su misma área no pasa por el firewall.
 */
@Composable
private fun queSeCorta(formulario: FormularioBloqueo, alerta: Alerta): String? {
    val origen = alerta.origen
    val equipo = if (origen.nombre != null) "${origen.nombre} (${origen.ip})" else origen.ip
    return when (formulario.accion) {
        AccionBloqueo.BLOQUEAR, AccionBloqueo.CAMBIAR_DURACION -> if (formulario.duracion == DuracionBloqueo.HASTA_DESBLOQUEAR) {
            stringResource(R.string.bloqueo_se_corta_hasta, equipo)
        } else {
            stringResource(R.string.bloqueo_se_corta, equipo, stringResource(formulario.duracion.titulo()))
        }
        AccionBloqueo.DESBLOQUEAR -> stringResource(R.string.bloqueo_vuelve, equipo)
        AccionBloqueo.RECHAZAR -> null
    }
}

/** Una duración con su botón de radio. */
@Composable
private fun FilaDuracion(duracion: DuracionBloqueo, elegida: Boolean, alElegir: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = elegida, onClick = alElegir, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MarcaRadio(elegida)
        Text(text = stringResource(duracion.titulo()), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
}

/** "15 minutos", "1 hora", "8 horas", "24 horas" o "Hasta desbloquear": la opción de la hoja y el diálogo de la huella. */
internal fun DuracionBloqueo.titulo(): Int = when (this) {
    DuracionBloqueo.UNA_HORA -> R.string.bloqueo_1_hora
    DuracionBloqueo.UN_DIA -> R.string.bloqueo_24_horas
    DuracionBloqueo.HASTA_DESBLOQUEAR -> R.string.bloqueo_hasta_desbloquear_opcion
}
