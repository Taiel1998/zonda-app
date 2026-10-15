package ar.edu.uade.zonda.ui.resumen

import android.content.res.Resources
import androidx.annotation.StringRes
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.GrupoTipo
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.Tramo
import ar.edu.uade.zonda.ui.Formato
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// Los textos que arma el Resumen con sus cifras. Reciben los recursos y no dependen de Compose, así
// sirven igual para la pantalla, la imagen que se comparte y las pruebas.

private val LOCALE: Locale = Locale.forLanguageTag("es-AR")

/** El grupo en su fila: "Fuerza bruta (SSH, FTP)". */
@StringRes
fun GrupoTipo.enFila(): Int = when (this) {
    GrupoTipo.FUERZA_BRUTA -> R.string.resumen_tipo_fuerza_bruta
    GrupoTipo.ESCANEO -> R.string.resumen_tipo_escaneo
    GrupoTipo.DDOS -> R.string.resumen_tipo_ddos
    GrupoTipo.OTROS -> R.string.resumen_tipo_otros
}

/** El grupo en el chip del filtro: "Fuerza bruta" (Figma 100-8498). */
@StringRes
fun GrupoTipo.enChip(): Int = when (this) {
    GrupoTipo.FUERZA_BRUTA -> R.string.resumen_chip_fuerza_bruta
    GrupoTipo.ESCANEO -> R.string.resumen_tipo_escaneo
    GrupoTipo.DDOS -> R.string.resumen_tipo_ddos
    GrupoTipo.OTROS -> R.string.resumen_tipo_otros
}

/** El grupo dentro de la frase del gráfico: "11 de fuerza bruta". */
@StringRes
fun GrupoTipo.enFrase(): Int = when (this) {
    GrupoTipo.FUERZA_BRUTA -> R.string.resumen_frase_fuerza_bruta
    GrupoTipo.ESCANEO -> R.string.resumen_frase_escaneo
    GrupoTipo.DDOS -> R.string.resumen_frase_ddos
    GrupoTipo.OTROS -> R.string.resumen_frase_otros
}

/** Cada método en la tarjeta de precisión y en su chip: "Modelo de IA", "Umbrales estadísticos", "Reglas estáticas". */
@StringRes
fun MetodoDeteccion.enPrecision(): Int = when (this) {
    MetodoDeteccion.IA -> R.string.filtro_metodo_ia
    MetodoDeteccion.UMBRALES -> R.string.resumen_metodo_umbrales
    MetodoDeteccion.REGLAS -> R.string.resumen_metodo_reglas
}

/** El texto del chip del filtro: "Revisadas", "Viernes 25/09", "Semana del 28/8", "Modelo de IA". */
fun GrupoResumen.etiquetaChip(recursos: Resources): String = when (this) {
    GrupoResumen.Revisadas -> recursos.getString(R.string.resumen_chip_revisadas)
    GrupoResumen.SinRevisar -> recursos.getString(R.string.resumen_chip_sin_revisar)
    GrupoResumen.Altas -> recursos.getString(R.string.resumen_chip_altas)
    GrupoResumen.Reales -> recursos.getString(R.string.resumen_reales)
    GrupoResumen.FalsosPositivos -> recursos.getString(R.string.resumen_falsos_positivos)
    is GrupoResumen.Tipo -> recursos.getString(grupo.enChip())
    is GrupoResumen.Metodo -> recursos.getString(metodo.enPrecision())
    is GrupoResumen.Barra -> tramo.etiquetaChip(recursos)
}

/** Un día ("Viernes 25/09") o una semana ("Semana del 28/8"). */
fun Tramo.etiquetaChip(recursos: Resources): String =
    if (unSoloDia) Formato.diaDeLaSemanaYFecha(desde) else recursos.getString(R.string.resumen_semana_del, Formato.diaYMesCorto(desde))

/**
 * Cómo se nombra el grupo en "Mostrando …": como el chip, con minúscula al comienzo salvo el día y la
 * semana ("Mostrando fuerza bruta", "Mostrando Viernes 25/09").
 */
fun GrupoResumen.etiquetaFrase(recursos: Resources): String {
    val chip = etiquetaChip(recursos)
    return if (this is GrupoResumen.Barra) chip else chip.replaceFirstChar { it.lowercase(LOCALE) }
}

/**
 * La frase debajo del gráfico (Figma 52-2629, 100-7024 y las variantes filtradas): con un grupo,
 * "Mostrando revisadas: 37 alertas. Tocá de nuevo para ver todas."; si no, lo que más pasó.
 */
fun fraseDelGrafico(resumen: ResumenActividad, recursos: Resources): String? {
    resumen.grupo?.let { grupo ->
        return recursos.getQuantityString(R.plurals.resumen_mostrando, resumen.total, grupo.etiquetaFrase(recursos), resumen.total)
    }
    val destacado = resumen.destacado ?: return null
    val barra = destacado.barra
    return when (resumen.periodo) {
        PeriodoResumen.SIETE_DIAS -> recursos.getQuantityString(
            if (destacado.mismoDestino) R.plurals.resumen_destacado_dia_servidor else R.plurals.resumen_destacado_dia,
            barra.cantidad,
            Formato.diaDeLaSemana(barra.tramo.desde),
            barra.cantidad,
            destacado.delTipo,
            recursos.getString(destacado.tipo.enFrase()),
        )
        PeriodoResumen.TREINTA_DIAS -> recursos.getQuantityString(
            R.plurals.resumen_destacado_semana,
            barra.cantidad,
            Formato.diaYMesCorto(barra.tramo.desde),
            barra.cantidad,
        )
    }
}

/**
 * La nota de la precisión: la fórmula y qué método tiene la precisión más alta; con menos de
 * [ResumenActividad.REVISIONES_PARA_COMPARAR] revisiones de ese método, que la comparación todavía
 * no es confiable. En el Figma esta frase es fija; acá sale de las cifras.
 */
fun notaDePrecision(resumen: ResumenActividad, recursos: Resources): String {
    val formula = recursos.getString(R.string.resumen_precision_formula)
    val metodo = resumen.metodoMasPreciso ?: return formula
    val mejor = recursos.getString(
        when (metodo) {
            MetodoDeteccion.IA -> R.string.resumen_mejor_ia
            MetodoDeteccion.UMBRALES -> R.string.resumen_mejor_umbrales
            MetodoDeteccion.REGLAS -> R.string.resumen_mejor_reglas
        },
    )
    val revisiones = resumen.precisionPorMetodo.getValue(metodo).total
    val final = if (revisiones < ResumenActividad.REVISIONES_PARA_COMPARAR) {
        recursos.getQuantityString(R.plurals.resumen_mejor_pocas_revisiones, revisiones, revisiones)
    } else {
        "."
    }
    return "$formula $mejor$final"
}

/**
 * La línea de arriba: "Oficina central · del 21/09 al 27/09 · actualizado 02:14"; sin conexión
 * termina en "datos de las 02:14" (Figma 52-2807).
 */
fun lineaDelResumen(red: String?, tramo: Tramo, datosDe: Instant?, sinConexion: Boolean, recursos: Resources, zona: ZoneId): String {
    val partes = mutableListOf<String>()
    red?.let { partes += it }
    partes += recursos.getString(R.string.resumen_del_al, Formato.diaYMes(tramo.desde), Formato.diaYMes(tramo.hasta))
    partes += when {
        datosDe == null -> recursos.getString(R.string.alertas_sin_sincronizar)
        sinConexion -> recursos.getString(R.string.alertas_datos_de, Formato.hora(datosDe, zona))
        else -> recursos.getString(R.string.alertas_actualizado, Formato.hora(datosDe, zona))
    }
    return partes.joinToString(" · ")
}

/** "Resumen_21-09_27-09.png" (Figma 107-5511). */
fun nombreDeLaImagen(tramo: Tramo, recursos: Resources): String =
    recursos.getString(R.string.resumen_archivo, Formato.diaYMesParaArchivo(tramo.desde), Formato.diaYMesParaArchivo(tramo.hasta))

/**
 * El texto que acompaña la imagen (Figma 107-5485): "Resumen Zonda · del 21/09 al 27/09: 52 alertas,
 * 37 revisadas, 15 sin revisar." Solo cantidades (RNF02).
 */
fun textoParaCompartir(resumen: ResumenActividad, recursos: Resources): String =
    recursos.getString(
        R.string.resumen_texto_compartido,
        Formato.diaYMes(resumen.tramo.desde),
        Formato.diaYMes(resumen.tramo.hasta),
        recursos.getQuantityString(R.plurals.alertas_cantidad, resumen.total, resumen.total),
        recursos.getQuantityString(R.plurals.resumen_revisadas, resumen.revisadas, resumen.revisadas),
        resumen.sinRevisar.toString(),
    )

/** El aviso al volver de compartir (Figma 107-4941 a 107-5349). */
fun textoDelAviso(aviso: AvisoResumen, recursos: Resources): String = when (aviso) {
    is AvisoResumen.Enviado -> when (aviso.destino) {
        DestinoCompartir.GMAIL -> recursos.getString(R.string.resumen_enviado_gmail)
        DestinoCompartir.DRIVE -> recursos.getString(R.string.resumen_enviado_drive)
        else -> recursos.getString(R.string.resumen_enviado_por, recursos.getString(aviso.destino.nombre))
    }
    AvisoResumen.Copiado -> recursos.getString(R.string.resumen_copiado)
    AvisoResumen.NoSePudoCompartir -> recursos.getString(R.string.resumen_no_se_pudo_compartir)
}
