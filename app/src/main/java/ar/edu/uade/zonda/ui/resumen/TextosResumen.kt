package ar.edu.uade.zonda.ui.resumen

import android.content.res.Resources
import androidx.annotation.StringRes
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.GrupoResumen
import ar.edu.uade.zonda.domain.model.GrupoTipo
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.Proporcion
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
 * La barra de un modelo va clara si ese modelo tiene menos de [ResumenActividad.REVISIONES_PARA_COMPARAR]
 * revisiones: su porcentaje todavía no se puede comparar con el de los otros (cambio 4). Las 30 se
 * cuentan por modelo, así uno con muchas revisiones no presta las suyas a otro que tiene pocas.
 */
fun Proporcion.barraClara(): Boolean = total < ResumenActividad.REVISIONES_PARA_COMPARAR

/**
 * El ejemplo de "Precisión" en la hoja del ⓘ, con los números reales (cambio 15): "la IA tiene 27 de
 * 33: marcó 33 alertas que ya revisaste y 27 eran ataques". Usa el primer modelo con al menos dos
 * revisiones; si ninguno tiene, la hoja va sin ejemplo.
 */
fun ejemploDePrecision(resumen: ResumenActividad, recursos: Resources): String? {
    val (metodo, precision) = MetodoDeteccion.entries
        .map { it to resumen.precisionPorMetodo.getValue(it) }
        .firstOrNull { (_, precision) -> precision.total >= 2 } ?: return null
    val plural = when (metodo) {
        MetodoDeteccion.IA -> R.plurals.precision_ejemplo_ia
        MetodoDeteccion.UMBRALES -> R.plurals.precision_ejemplo_umbrales
        MetodoDeteccion.REGLAS -> R.plurals.precision_ejemplo_reglas
    }
    return recursos.getQuantityString(plural, precision.parte, precision.parte, precision.total)
}

/**
 * El recuadro azul de la hoja del ⓘ, que cambia con el filtro elegido (cambio 10): sin filtro, de qué
 * días es la precisión; con un modelo elegido, que cada fila cuenta las que marcó ese modelo junto con
 * el de la fila (por eso las otras filas bajan); con un día, una semana o un tipo, que cuenta solo esas.
 */
fun recuadroDePrecision(resumen: ResumenActividad, recursos: Resources): String = when (val grupo = resumen.grupo) {
    null -> recursos.getString(
        if (resumen.periodo == PeriodoResumen.SIETE_DIAS) R.string.precision_sin_filtro_7 else R.string.precision_sin_filtro_30,
    )
    is GrupoResumen.Metodo -> recursos.getString(
        when (grupo.metodo) {
            MetodoDeteccion.IA -> R.string.precision_filtro_ia
            MetodoDeteccion.UMBRALES -> R.string.precision_filtro_umbrales
            MetodoDeteccion.REGLAS -> R.string.precision_filtro_reglas
        },
    )
    // "Estás viendo el viernes 25/09", "Estás viendo la semana del 28/8": el día y la semana en minúscula.
    is GrupoResumen.Barra -> recursos.getString(
        if (grupo.tramo.unSoloDia) R.string.precision_filtro_dia else R.string.precision_filtro_semana,
        grupo.etiquetaChip(recursos).replaceFirstChar { it.lowercase(LOCALE) },
    )
    is GrupoResumen.Tipo -> recursos.getString(R.string.precision_filtro_tipo, grupo.etiquetaFrase(recursos))
    GrupoResumen.Revisadas -> recursos.getString(R.string.precision_filtro_revisadas)
    GrupoResumen.SinRevisar -> recursos.getString(R.string.precision_filtro_sin_revisar)
    GrupoResumen.Altas -> recursos.getString(R.string.precision_filtro_altas)
    GrupoResumen.Reales -> recursos.getString(R.string.precision_filtro_reales)
    GrupoResumen.FalsosPositivos -> recursos.getString(R.string.precision_filtro_falsos)
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
