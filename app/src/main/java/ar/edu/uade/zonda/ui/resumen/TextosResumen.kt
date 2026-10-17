package ar.edu.uade.zonda.ui.resumen

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import ar.edu.uade.zonda.R
import ar.edu.uade.zonda.domain.model.EscalaBarra
import ar.edu.uade.zonda.domain.model.FiltroResumen
import ar.edu.uade.zonda.domain.model.Franja
import ar.edu.uade.zonda.domain.model.MetodoDeteccion
import ar.edu.uade.zonda.domain.model.PeriodoResumen
import ar.edu.uade.zonda.domain.model.Proporcion
import ar.edu.uade.zonda.domain.model.RangoResumen
import ar.edu.uade.zonda.domain.model.ResumenActividad
import ar.edu.uade.zonda.domain.model.RevisionResumen
import ar.edu.uade.zonda.domain.model.TipoAtaque
import ar.edu.uade.zonda.domain.model.Tramo
import ar.edu.uade.zonda.ui.Formato
import ar.edu.uade.zonda.ui.nombreCorto
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

// Los textos que arma el Resumen con sus cifras. Reciben los recursos y no dependen de Compose, así
// sirven igual para la pantalla, la imagen que se comparte y las pruebas.

private val LOCALE: Locale = Locale.forLanguageTag("es-AR")

/**
 * El tipo en su fila de "Por tipo de ataque" (cambio 14; Figma 52-2629): "Fuerza bruta (SSH, FTP)" y
 * los demás con su nombre corto, el mismo del chip del filtro (Figma 100-8498).
 */
@StringRes
fun TipoAtaque.enFila(): Int = if (this == TipoAtaque.FUERZA_BRUTA) R.string.resumen_tipo_fuerza_bruta else nombreCorto()

/** Cuántas de ese tipo, dentro de la frase del gráfico: "11 de fuerza bruta", "3 probables falsas alarmas". */
@PluralsRes
fun TipoAtaque.enFrase(): Int = when (this) {
    TipoAtaque.FUERZA_BRUTA -> R.plurals.resumen_cuantas_fuerza_bruta
    TipoAtaque.SIN_TIPIFICAR -> R.plurals.resumen_cuantas_sin_tipificar
    TipoAtaque.ESCANEO -> R.plurals.resumen_cuantas_escaneo
    TipoAtaque.DDOS -> R.plurals.resumen_cuantas_ddos
    TipoAtaque.PROBABLE_FALSA_ALARMA -> R.plurals.resumen_cuantas_probable_falsa_alarma
    TipoAtaque.SERVICIO_NO_AUTORIZADO -> R.plurals.resumen_cuantas_servicio_no_autorizado
    TipoAtaque.VOLUMEN_INUSUAL -> R.plurals.resumen_cuantas_volumen_inusual
}

/** Cada método en la tarjeta de precisión y en su chip: "Modelo de IA", "Umbrales estadísticos", "Reglas estáticas". */
@StringRes
fun MetodoDeteccion.enPrecision(): Int = when (this) {
    MetodoDeteccion.IA -> R.string.filtro_metodo_ia
    MetodoDeteccion.UMBRALES -> R.string.resumen_metodo_umbrales
    MetodoDeteccion.REGLAS -> R.string.resumen_metodo_reglas
}

/**
 * Debajo de cada barra del gráfico: la hora ("2", Figma 463-1836), el día ("vie"; con más de 7 días, el
 * número del día) o el comienzo de la semana ("28/8").
 */
fun Franja.etiquetaGrafico(pocosDias: Boolean = true): String = when (escala) {
    EscalaBarra.HORA -> desde.hour.toString()
    EscalaBarra.DIA -> if (pocosDias) Formato.diaDeLaSemanaCorto(primerDia) else primerDia.dayOfMonth.toString()
    EscalaBarra.SEMANA -> Formato.diaYMesCorto(primerDia)
}

/** Dentro de una frase con varios filtros: "las 2 h", "viernes", "semana del 28/8" (Figma 463-1145). */
fun Franja.enFrase(recursos: Resources): String = when (escala) {
    EscalaBarra.HORA -> recursos.getString(R.string.resumen_las_horas, desde.hour)
    EscalaBarra.DIA -> Formato.diaDeLaSemana(primerDia)
    EscalaBarra.SEMANA -> recursos.getString(R.string.resumen_semana_del, Formato.diaYMesCorto(primerDia)).replaceFirstChar { it.lowercase(LOCALE) }
}

/** Con la fecha, en la lista y en la imagen: "viernes 25/09", "semana del 28/8" (Figma 463-1669 y 463-3007). */
fun Franja.conFecha(recursos: Resources): String = when (escala) {
    EscalaBarra.HORA -> recursos.getString(R.string.resumen_las_horas_del, desde.hour, Formato.diaDeLaSemana(primerDia) + " " + Formato.diaYMes(primerDia))
    EscalaBarra.DIA -> Formato.diaDeLaSemana(primerDia) + " " + Formato.diaYMes(primerDia)
    EscalaBarra.SEMANA -> enFrase(recursos)
}

/** El chip de la hoja de filtros: "2 h", "vie 25/09", "semana del 28/8" (Figma 463-1317). */
fun Franja.enHoja(recursos: Resources): String = when (escala) {
    EscalaBarra.HORA -> recursos.getString(R.string.resumen_hora_chip, desde.hour)
    EscalaBarra.DIA -> Formato.diaDeLaSemanaCorto(primerDia) + " " + Formato.diaYMes(primerDia)
    EscalaBarra.SEMANA -> enFrase(recursos)
}

/** "viernes 25/09": un día con su nombre y su fecha. */
private fun diaConFecha(dia: java.time.LocalDate): String = Formato.diaDeLaSemana(dia) + " " + Formato.diaYMes(dia)

/** "sáb 10/10 22:00": un momento con el día corto. */
private fun momentoCorto(momento: java.time.LocalDateTime): String =
    Formato.diaDeLaSemanaCorto(momento.toLocalDate()) + " " + Formato.diaYMes(momento.toLocalDate()) + " " + Formato.horaDelDia(momento.toLocalTime())

/**
 * Lo que abarca el Resumen en la línea de arriba (cambio 19): los atajos y los días enteros, "del 21/09
 * al 27/09"; un día solo, "viernes 25/09"; un día con horas, "viernes 25/09 · de 00:00 a 05:59" (Figma
 * 463-1836); y días distintos con horas, "del sáb 10/10 22:00 al dom 11/10 06:00".
 */
fun RangoResumen.enLinea(recursos: Resources, esAtajo: Boolean = false): String = when {
    esAtajo || (diasEnteros && dias > 1) -> recursos.getString(R.string.resumen_del_al, Formato.diaYMes(primerDia), Formato.diaYMes(ultimoDia))
    diasEnteros -> diaConFecha(primerDia)
    dias == 1L -> recursos.getString(
        R.string.resumen_dia_de_a,
        diaConFecha(primerDia),
        Formato.horaDelDia(desde.toLocalTime()),
        Formato.horaDelDia(hasta.toLocalTime()),
    )
    else -> recursos.getString(R.string.resumen_del_al, momentoCorto(desde), momentoCorto(hasta))
}

/** El chip "Fechas" con fechas elegidas (cambio 19; Figma 463-1836): "dom 11/10" o "10/10 al 11/10". */
fun RangoResumen.enChip(recursos: Resources): String =
    if (dias == 1L) {
        Formato.diaDeLaSemanaCorto(primerDia) + " " + Formato.diaYMes(primerDia)
    } else {
        recursos.getString(R.string.resumen_al, Formato.diaYMes(primerDia), Formato.diaYMes(ultimoDia))
    }

/** "Revisadas", "Sin revisar", "Reales", "Falsos positivos": los chips de la hoja de filtros. */
@StringRes
fun RevisionResumen.nombre(): Int = when (this) {
    RevisionResumen.REVISADAS -> R.string.resumen_chip_revisadas
    RevisionResumen.SIN_REVISAR -> R.string.resumen_chip_sin_revisar
    RevisionResumen.REALES -> R.string.resumen_reales
    RevisionResumen.FALSOS_POSITIVOS -> R.string.resumen_falsos_positivos
}

/** "a", "a y b", "a, b y c". */
private fun enumerar(partes: List<String>, recursos: Resources): String = when (partes.size) {
    0 -> ""
    1 -> partes.single()
    else -> recursos.getString(R.string.resumen_y, partes.dropLast(1).joinToString(", "), partes.last())
}

private fun String.minuscula(): String = replaceFirstChar { it.lowercase(LOCALE) }

/**
 * Cada filtro puesto, con palabras y en el orden de la hoja (cuándo, tipo, revisión y modelo), un
 * grupo por clase: "sábado y domingo", "fuerza bruta y escaneo de puertos". Los tipos que la red tiene
 * en alta, si son justo esos, son "las altas". Con [conFecha], los días llevan la fecha (la lista y la
 * imagen); si no, solo el nombre (la frase del gráfico).
 */
fun FiltroResumen.grupos(recursos: Resources, altas: Set<TipoAtaque>, conFecha: Boolean): List<String> {
    val grupos = mutableListOf<String>()
    if (barras.isNotEmpty()) {
        grupos += enumerar(barras.sortedBy { it.desde }.map { if (conFecha) it.conFecha(recursos) else it.enFrase(recursos) }, recursos)
    }
    if (tipos.isNotEmpty()) {
        grupos += if (tiposSonLasAltas(altas)) {
            recursos.getString(R.string.resumen_las_altas)
        } else {
            enumerar(TipoAtaque.EN_PANTALLA.filter { it in tipos }.map { recursos.getString(it.nombreCorto()).minuscula() }, recursos)
        }
    }
    revision?.let { grupos += recursos.getString(it.nombre()).minuscula() }
    metodo?.let { grupos += recursos.getString(it.enPrecision()).minuscula() }
    return grupos
}

/** Cuántas cosas nombra la frase: con más de cuatro queda larga y dice cuántos filtros hay. */
private fun FiltroResumen.nombrados(altas: Set<TipoAtaque>): Int =
    barras.size + (if (tiposSonLasAltas(altas)) 1 else tipos.size) + (if (revision == null) 0 else 1) + (if (metodo == null) 0 else 1)

/**
 * Cómo se nombra un filtro solo en "Mostrando …": el día con mayúscula y la fecha, como el chip que
 * había antes ("Mostrando Viernes 25/09"), y lo demás en minúscula ("Mostrando fuerza bruta").
 */
private fun FiltroResumen.unoSolo(recursos: Resources, altas: Set<TipoAtaque>): String {
    barras.singleOrNull()?.let { barra ->
        return if (barra.escala == EscalaBarra.DIA) Formato.diaDeLaSemanaYFecha(barra.primerDia) else barra.enFrase(recursos)
    }
    return grupos(recursos, altas, conFecha = false).single()
}

/**
 * La frase debajo del gráfico (cambio 16; Figma 100-8096 y 463-1145): con un filtro, "Mostrando
 * fuerza bruta: 15 alertas. Tocá de nuevo para sacarlo."; con más, los nombra ("Mostrando 9 alertas:
 * sábado y domingo · fuerza bruta y escaneo de puertos.") y, si quedan más de cuatro, dice cuántos son
 * ("Mostrando 20 alertas con 5 filtros."). Sin filtros, lo que más pasó. En la imagen no se puede
 * tocar nada: la de un filtro no dice "Tocá de nuevo" ([enImagen]).
 */
fun fraseDelGrafico(resumen: ResumenActividad, recursos: Resources, altas: Set<TipoAtaque> = emptySet(), enImagen: Boolean = false): String? {
    val filtro = resumen.filtro
    val total = resumen.total
    if (!filtro.vacio) {
        val nombrados = filtro.nombrados(altas)
        return when {
            nombrados == 1 -> recursos.getQuantityString(
                if (enImagen) R.plurals.resumen_mostrando_imagen else R.plurals.resumen_mostrando,
                total,
                filtro.unoSolo(recursos, altas),
                total,
            )
            nombrados <= MAXIMO_NOMBRADOS -> recursos.getQuantityString(
                R.plurals.resumen_mostrando_filtros,
                total,
                total,
                filtro.grupos(recursos, altas, conFecha = false).joinToString(" · "),
            )
            else -> recursos.getQuantityString(R.plurals.resumen_mostrando_con_filtros, total, total, filtro.cantidad)
        }
    }
    val destacado = resumen.destacado ?: return null
    val barra = destacado.barra
    val delTipo = recursos.getQuantityString(destacado.tipo.enFrase(), destacado.delTipo, destacado.delTipo)
    return when (barra.franja.escala) {
        // Figma 463-1836: "A las 2 h hubo más alertas: 5, con 4 de fuerza bruta."
        EscalaBarra.HORA -> recursos.getQuantityString(
            R.plurals.resumen_destacado_hora,
            barra.cantidad,
            barra.franja.desde.hour,
            barra.cantidad,
            delTipo,
        )
        EscalaBarra.DIA -> recursos.getQuantityString(
            if (destacado.mismoDestino) R.plurals.resumen_destacado_dia_servidor else R.plurals.resumen_destacado_dia,
            barra.cantidad,
            // Con más de 7 días, el nombre del día se repite: va con la fecha.
            if (resumen.rango.dias > 7) barra.franja.conFecha(recursos) else Formato.diaDeLaSemana(barra.franja.primerDia),
            barra.cantidad,
            delTipo,
        )
        EscalaBarra.SEMANA -> recursos.getQuantityString(
            R.plurals.resumen_destacado_semana,
            barra.cantidad,
            Formato.diaYMesCorto(barra.franja.primerDia),
            barra.cantidad,
        )
    }
}

/** Más de cuatro cosas en la frase la hacen larga: se dice cuántos filtros hay. */
private const val MAXIMO_NOMBRADOS = 4

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
 * El recuadro azul de la hoja del ⓘ, que cambia con los filtros (cambio 10): sin filtros, de qué días
 * es la precisión; con un modelo elegido, que cada fila cuenta las que marcó ese modelo junto con el
 * de la fila (por eso las otras filas bajan); con un solo filtro de otra clase, que cuenta solo esas; y
 * con varios, que cuenta solo las revisadas de los filtros elegidos.
 */
fun recuadroDePrecision(resumen: ResumenActividad, recursos: Resources, altas: Set<TipoAtaque> = emptySet()): String {
    val filtro = resumen.filtro
    if (filtro.vacio) {
        return recursos.getString(
            when (resumen.periodo) {
                PeriodoResumen.SIETE_DIAS -> R.string.precision_sin_filtro_7
                PeriodoResumen.TREINTA_DIAS -> R.string.precision_sin_filtro_30
                null -> R.string.precision_sin_filtro_fechas
            },
        )
    }
    filtro.metodo?.let { metodo ->
        return recursos.getString(
            when (metodo) {
                MetodoDeteccion.IA -> R.string.precision_filtro_ia
                MetodoDeteccion.UMBRALES -> R.string.precision_filtro_umbrales
                MetodoDeteccion.REGLAS -> R.string.precision_filtro_reglas
            },
        )
    }
    if (filtro.tiposSonLasAltas(altas) && filtro.cantidad == filtro.tipos.size) return recursos.getString(R.string.precision_filtro_altas)
    if (filtro.cantidad > 1) return recursos.getString(R.string.precision_filtro_varios)
    filtro.barras.singleOrNull()?.let { barra ->
        // "Estás viendo el viernes 25/09", "Estás viendo la semana del 28/8".
        return recursos.getString(
            when (barra.escala) {
                EscalaBarra.HORA -> R.string.precision_filtro_hora
                EscalaBarra.DIA -> R.string.precision_filtro_dia
                EscalaBarra.SEMANA -> R.string.precision_filtro_semana
            },
            barra.conFecha(recursos),
        )
    }
    filtro.tipos.singleOrNull()?.let { tipo ->
        return recursos.getString(R.string.precision_filtro_tipo, recursos.getString(tipo.nombreCorto()).minuscula())
    }
    return recursos.getString(
        when (filtro.revision) {
            RevisionResumen.REVISADAS -> R.string.precision_filtro_revisadas
            RevisionResumen.SIN_REVISAR -> R.string.precision_filtro_sin_revisar
            RevisionResumen.REALES -> R.string.precision_filtro_reales
            RevisionResumen.FALSOS_POSITIVOS, null -> R.string.precision_filtro_falsos
        },
    )
}

/**
 * Lo que dice la lista de "Ver estas alertas" debajo del título (Figma 463-1669): "Del Resumen: viernes
 * 25/09 · fuerza bruta". Sin días elegidos, el período ("del 21/09 al 27/09").
 */
fun lineaDeLaLista(resumen: ResumenActividad, recursos: Resources, altas: Set<TipoAtaque>): String {
    val grupos = resumen.filtro.grupos(recursos, altas, conFecha = true).toMutableList()
    if (resumen.filtro.barras.isEmpty()) {
        grupos.add(0, resumen.rango.enLinea(recursos, esAtajo = resumen.periodo != null))
    }
    return recursos.getString(R.string.resumen_lista_del_resumen, grupos.joinToString(" · "))
}

/**
 * La línea de arriba: "Oficina central · del 21/09 al 27/09 · actualizado 02:14", con el rango exacto
 * si hay fechas elegidas ("viernes 25/09 · de 00:00 a 05:59"); sin conexión termina en "datos de las
 * 02:14" (Figma 52-2807 y 463-1836).
 */
fun lineaDelResumen(red: String?, resumen: ResumenActividad, datosDe: Instant?, sinConexion: Boolean, recursos: Resources, zona: ZoneId): String {
    val partes = mutableListOf<String>()
    red?.let { partes += it }
    partes += resumen.rango.enLinea(recursos, esAtajo = resumen.periodo != null)
    partes += when {
        datosDe == null -> recursos.getString(R.string.alertas_sin_sincronizar)
        sinConexion -> recursos.getString(R.string.alertas_datos_de, Formato.hora(datosDe, zona))
        else -> recursos.getString(R.string.alertas_actualizado, Formato.hora(datosDe, zona))
    }
    return partes.joinToString(" · ")
}

/** "Resumen_21-09_27-09", sin la extensión: la imagen y el PDF se llaman igual (Figma 107-5511 y 463-3243). */
fun nombreDelArchivo(tramo: Tramo, recursos: Resources): String =
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
