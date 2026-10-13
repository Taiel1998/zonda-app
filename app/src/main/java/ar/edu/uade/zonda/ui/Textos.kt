package ar.edu.uade.zonda.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.Alerta
import ar.edu.uade.zonda.domain.model.ErrorZonda
import ar.edu.uade.zonda.domain.model.EstadoEnvio
import ar.edu.uade.zonda.domain.model.EstadoRevision
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.ModoAviso
import ar.edu.uade.zonda.domain.model.Motivo
import ar.edu.uade.zonda.domain.model.Periodo
import ar.edu.uade.zonda.domain.model.ResultadoRevision
import ar.edu.uade.zonda.domain.model.Severidad
import ar.edu.uade.zonda.domain.model.TipoAtaque

// Qué texto de strings.xml corresponde a cada valor del dominio. El dominio no conoce los
// recursos de Android; la traducción se hace acá, en un solo lugar, y los textos los mantiene Melinda.

@StringRes
fun Severidad.etiqueta(): Int = when (this) {
    Severidad.ALTA -> R.string.severidad_alta
    Severidad.MEDIA -> R.string.severidad_media
    Severidad.BAJA -> R.string.severidad_baja
}

@StringRes
fun EstadoRevision.etiqueta(): Int = when (this) {
    EstadoRevision.SIN_REVISAR -> R.string.estado_sin_revisar
    EstadoRevision.ATAQUE_REAL -> R.string.estado_revisada
    EstadoRevision.FALSO_POSITIVO -> R.string.estado_falso_positivo
}

@StringRes
fun EstadoEnvio.etiqueta(): Int = when (this) {
    EstadoEnvio.PENDIENTE, EstadoEnvio.ENVIANDO -> R.string.envio_pendiente
    EstadoEnvio.ERROR -> R.string.envio_error
    EstadoEnvio.CONFLICTO -> R.string.envio_conflicto
}

/** Nombre corto para las etiquetas de la tarjeta: IA, Umbrales, Reglas. */
@StringRes
fun MetodoDeteccion.etiqueta(): Int = when (this) {
    MetodoDeteccion.IA -> R.string.metodo_ia
    MetodoDeteccion.UMBRALES -> R.string.metodo_umbrales
    MetodoDeteccion.REGLAS -> R.string.metodo_reglas
}

/** Cómo se nombra dentro de una frase: "detectada por IA y umbrales". */
@StringRes
fun MetodoDeteccion.nombreEnFrase(): Int = when (this) {
    MetodoDeteccion.IA -> R.string.metodo_ia_frase
    MetodoDeteccion.UMBRALES -> R.string.metodo_umbrales_frase
    MetodoDeteccion.REGLAS -> R.string.metodo_reglas_frase
}

/** Texto del chip de período. */
@StringRes
fun Periodo.etiqueta(): Int = when (this) {
    Periodo.ULTIMAS_24_HORAS -> R.string.filtro_periodo_24h
    Periodo.ULTIMOS_7_DIAS -> R.string.filtro_periodo_7d
    Periodo.ULTIMOS_30_DIAS -> R.string.filtro_periodo_30d
    Periodo.TODO -> R.string.filtro_periodo
}

/** Cómo aparece en la línea de resumen ("últimas 24 h"); null cuando no se filtra por período. */
@StringRes
fun Periodo.enResumen(): Int? = when (this) {
    Periodo.ULTIMAS_24_HORAS -> R.string.periodo_ultimas_24h
    Periodo.ULTIMOS_7_DIAS -> R.string.periodo_ultimos_7d
    Periodo.ULTIMOS_30_DIAS -> R.string.periodo_ultimos_30d
    Periodo.TODO -> null
}

@StringRes
fun ResultadoRevision.etiqueta(): Int = when (this) {
    ResultadoRevision.ATAQUE_REAL -> R.string.resultado_ataque_real
    ResultadoRevision.FALSO_POSITIVO -> R.string.resultado_falso_positivo
}

@StringRes
fun Motivo.etiqueta(): Int = when (this) {
    Motivo.ESCALADO_AL_PROVEEDOR -> R.string.motivo_escalado_al_proveedor
    Motivo.BLOQUEADO_MANUALMENTE -> R.string.motivo_bloqueado_manualmente
    Motivo.CONTRASENA_CAMBIADA -> R.string.motivo_contrasena_cambiada
    Motivo.INVESTIGANDO -> R.string.motivo_investigando
    Motivo.TAREA_PROGRAMADA -> R.string.motivo_tarea_programada
    Motivo.BACKUP -> R.string.motivo_backup
    Motivo.EQUIPO_NUEVO -> R.string.motivo_equipo_nuevo
    Motivo.USUARIO_AUTORIZADO -> R.string.motivo_usuario_autorizado
    Motivo.OTRO -> R.string.motivo_otro
}

@StringRes
fun TipoAtaque.etiqueta(): Int = when (this) {
    TipoAtaque.FUERZA_BRUTA -> R.string.tipo_fuerza_bruta
    TipoAtaque.ESCANEO -> R.string.tipo_escaneo
    TipoAtaque.DDOS -> R.string.tipo_ddos
    TipoAtaque.SIN_TIPIFICAR -> R.string.tipo_sin_tipificar
    TipoAtaque.PROBABLE_FALSA_ALARMA -> R.string.tipo_probable_falsa_alarma
    TipoAtaque.SOLO_REGLAS_O_UMBRALES -> R.string.tipo_solo_reglas_o_umbrales
}

@StringRes
fun ModoAviso.etiqueta(): Int = when (this) {
    ModoAviso.SONIDO -> R.string.modo_sonido
    ModoAviso.VIBRACION -> R.string.modo_vibracion
    ModoAviso.SIN_AVISO -> R.string.modo_sin_aviso
}

/**
 * El comentario que propone cada motivo al revisar, el de su pantalla del Figma; la persona lo puede
 * editar (RF04). Los que nombran equipos usan los de la alerta, para no proponer un nombre ajeno.
 */
fun Motivo.comentarioPropuesto(recursos: Resources, alerta: Alerta): String = when (this) {
    Motivo.ESCALADO_AL_PROVEEDOR -> recursos.getString(R.string.comentario_escalado_al_proveedor)
    Motivo.BLOQUEADO_MANUALMENTE ->
        recursos.getString(R.string.comentario_bloqueado_manualmente, alerta.servicio.uppercase(), alerta.origen.etiqueta)
    Motivo.CONTRASENA_CAMBIADA -> recursos.getString(R.string.comentario_contrasena_cambiada)
    Motivo.INVESTIGANDO -> recursos.getString(R.string.comentario_investigando)
    Motivo.TAREA_PROGRAMADA -> recursos.getString(R.string.comentario_tarea_programada)
    Motivo.BACKUP -> recursos.getString(R.string.comentario_backup, alerta.origen.etiqueta, alerta.destino.etiqueta)
    Motivo.EQUIPO_NUEVO -> recursos.getString(R.string.comentario_equipo_nuevo, alerta.origen.etiqueta)
    Motivo.USUARIO_AUTORIZADO -> recursos.getString(R.string.comentario_usuario_autorizado)
    Motivo.OTRO -> recursos.getString(R.string.comentario_otro)
}

/** El mensaje que ve la persona por cada error de datos (informe, sección 14.3). */
@StringRes
fun ErrorZonda.mensaje(): Int = when (this) {
    is ErrorZonda.SinConexion -> R.string.error_sin_conexion
    is ErrorZonda.ServidorNoVerificado -> R.string.error_servidor_no_verificado
    is ErrorZonda.NoAutorizado -> R.string.error_no_autorizado
    is ErrorZonda.NoEncontrado -> R.string.error_no_encontrado
    is ErrorZonda.Conflicto -> R.string.error_conflicto
    is ErrorZonda.CursorInvalido -> R.string.error_cursor_invalido
    is ErrorZonda.CodigoInvalido -> R.string.error_codigo_invalido
    is ErrorZonda.CodigoVencido -> R.string.error_codigo_vencido
    is ErrorZonda.ErrorDelServidor -> R.string.error_del_servidor
    is ErrorZonda.Validacion -> R.string.error_validacion
}
