package ar.edu.uade.zonda.di

import android.content.Context

/**
 * Contenedor manual de dependencias.
 *
 * Acá se crean una sola vez las piezas compartidas (base de datos, cliente HTTP, repositorios,
 * casos de uso) y las pantallas las obtienen a través de sus ViewModels. Se eligió un contenedor
 * manual en lugar de Hilt para poder explicar cada instancia línea por línea en la defensa
 * (ver docs/decisiones.md, decisión 2).
 *
 * Las dependencias se agregan en las fases siguientes: Room en la fase 2, Retrofit en la fase 4.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
}
