package com.aqualer.app.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Utilidades para convivir con el modo edge-to-edge.
 *

 * Estas extensiones suman el alto de la barra correspondiente al
 * relleno que la vista ya tenia, sin perderlo.
 */

/** Reserva el espacio de la barra de estado sobre esta vista. */
fun View.aplicarInsetSuperior() {
    val rellenoBase = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        vista.updatePadding(top = rellenoBase + barras.top)
        insets
    }
}

/**
 * Reserva el espacio de la barra de navegacion bajo esta vista.
 * Tiene en cuenta el teclado para que el contenido no quede oculto
 * cuando el usuario esta escribiendo.
 */
fun View.aplicarInsetInferior() {
    val rellenoBase = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { vista, insets ->
        val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val teclado = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        vista.updatePadding(bottom = rellenoBase + maxOf(barras, teclado))
        insets
    }
}