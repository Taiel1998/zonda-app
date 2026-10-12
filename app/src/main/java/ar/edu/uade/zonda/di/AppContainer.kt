package ar.edu.uade.zonda.di

import android.content.Context
import ar.edu.uade.zonda.data.local.ZondaDatabase
import ar.edu.uade.zonda.data.preferencias.PreferenciasRepositoryImpl
import ar.edu.uade.zonda.data.preferencias.preferenciasZonda
import ar.edu.uade.zonda.data.repository.AccionesPendientesRepositoryImpl
import ar.edu.uade.zonda.data.repository.AlertasRepositoryImpl
import ar.edu.uade.zonda.data.repository.RedesRepositoryImpl
import ar.edu.uade.zonda.data.seguridad.AlmacenSeguro
import ar.edu.uade.zonda.data.seguridad.AlmacenSeguroKeystore
import ar.edu.uade.zonda.data.seguridad.ClaveDeBase
import ar.edu.uade.zonda.domain.repository.AccionesPendientesRepository
import ar.edu.uade.zonda.domain.repository.AlertasRepository
import ar.edu.uade.zonda.domain.repository.GeneradorDeIds
import ar.edu.uade.zonda.domain.repository.PreferenciasRepository
import ar.edu.uade.zonda.domain.repository.RedesRepository
import ar.edu.uade.zonda.domain.usecase.CalcularResumen
import ar.edu.uade.zonda.domain.usecase.CambiarSeveridades
import ar.edu.uade.zonda.domain.usecase.DeshacerRevision
import ar.edu.uade.zonda.domain.usecase.ObservarAlertas
import ar.edu.uade.zonda.domain.usecase.ObtenerEstadoRed
import ar.edu.uade.zonda.domain.usecase.ParearRed
import ar.edu.uade.zonda.domain.usecase.QuitarDispositivo
import ar.edu.uade.zonda.domain.usecase.RevisarAlerta
import ar.edu.uade.zonda.domain.usecase.SincronizarAlertas
import ar.edu.uade.zonda.domain.usecase.VincularTelegram
import java.util.UUID

/**
 * Contenedor manual de dependencias.
 *
 * Acá se crean una sola vez las piezas compartidas (almacenamiento seguro, base de datos,
 * preferencias, repositorios y casos de uso) y las pantallas las obtienen a través de sus
 * ViewModels. Se eligió un contenedor manual en lugar de Hilt para poder explicar cada instancia
 * línea por línea en la defensa (docs/decisiones.md, decisión 2).
 *
 * Todo es `lazy`: nada pesado se crea hasta que alguien lo usa, así el arranque de la app no
 * abre la base ni toca el Keystore si todavía no hace falta.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    // Capa de datos
    val almacenSeguro: AlmacenSeguro by lazy { AlmacenSeguroKeystore(appContext) }
    val baseDeDatos: ZondaDatabase by lazy { ZondaDatabase.cifrada(appContext, ClaveDeBase(almacenSeguro).obtener()) }
    val preferencias: PreferenciasRepository by lazy { PreferenciasRepositoryImpl(appContext.preferenciasZonda) }
    val redes: RedesRepository by lazy { RedesRepositoryImpl(baseDeDatos.redesDao(), almacenSeguro) }
    val alertas: AlertasRepository by lazy { AlertasRepositoryImpl(baseDeDatos.alertasDao()) }
    val accionesPendientes: AccionesPendientesRepository by lazy { AccionesPendientesRepositoryImpl(baseDeDatos.accionesPendientesDao()) }
    val generadorDeIds: GeneradorDeIds = GeneradorDeIds { UUID.randomUUID().toString() }

    // Casos de uso
    val observarAlertas: ObservarAlertas by lazy { ObservarAlertas(redes, alertas) }
    val obtenerEstadoRed: ObtenerEstadoRed by lazy { ObtenerEstadoRed(redes, alertas) }
    val revisarAlerta: RevisarAlerta by lazy { RevisarAlerta(alertas, accionesPendientes, generadorDeIds) }
    val deshacerRevision: DeshacerRevision by lazy { DeshacerRevision(alertas, accionesPendientes) }
    val sincronizarAlertas: SincronizarAlertas by lazy { SincronizarAlertas(redes, alertas) }
    val calcularResumen: CalcularResumen by lazy { CalcularResumen(redes, alertas) }
    val cambiarSeveridades: CambiarSeveridades by lazy { CambiarSeveridades(redes, accionesPendientes, generadorDeIds) }
    val parearRed: ParearRed by lazy { ParearRed(redes) }
    val quitarDispositivo: QuitarDispositivo by lazy { QuitarDispositivo(redes, alertas, accionesPendientes) }
    val vincularTelegram: VincularTelegram by lazy { VincularTelegram(redes) }
}
