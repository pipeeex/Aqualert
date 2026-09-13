package com.aqualer.app.util

import androidx.annotation.DrawableRes
import com.aqualer.app.R

/**
 * Evidencia fotografica simulada para la demo del CRUD.
 *
 * En esta etapa no se usa la camara ni Firebase Storage: el campo
 * imagen_url del Reporte guarda un identificador ("demo_1"..."demo_6")
 * que aqui se traduce al drawable correspondiente.
 *
 * Cuando se implemente UC6 "Tomar o seleccionar foto", imagen_url
 * pasara a contener la URL real devuelta por Storage y esta clase
 * solo se usara como imagen de reemplazo.
 */
object FotosDemo {

    private const val PREFIJO = "demo_"

    /** Drawables disponibles como evidencia simulada. */
    private val fotos = listOf(
        R.drawable.foto_demo_1,
        R.drawable.foto_demo_2,
        R.drawable.foto_demo_3,
        R.drawable.foto_demo_4,
        R.drawable.foto_demo_5,
        R.drawable.foto_demo_6
    )

    /** Cantidad de evidencias simuladas disponibles. */
    val cantidad: Int get() = fotos.size

    /** Identificador que se guarda en el campo imagen_url. */
    fun identificador(indice: Int): String = "$PREFIJO${(indice % cantidad) + 1}"

    /** Convierte el identificador guardado en su indice de lista. */
    fun indiceDesde(identificador: String): Int {
        val numero = identificador.removePrefix(PREFIJO).toIntOrNull() ?: 1
        return (numero - 1).coerceIn(0, cantidad - 1)
    }

    /** Drawable correspondiente a un identificador guardado. */
    @DrawableRes
    fun drawableDesde(identificador: String): Int =
        fotos[indiceDesde(identificador)]

    /** Drawable correspondiente a una posicion de la lista. */
    @DrawableRes
    fun drawablePorIndice(indice: Int): Int = fotos[indice % cantidad]

    /** Indica si el valor guardado es una evidencia simulada. */
    fun esSimulada(identificador: String): Boolean =
        identificador.startsWith(PREFIJO)
}