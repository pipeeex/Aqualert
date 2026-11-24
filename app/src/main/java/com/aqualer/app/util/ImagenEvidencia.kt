package com.aqualer.app.util

import android.net.Uri
import android.widget.ImageView
import com.aqualer.app.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * Carga de la evidencia fotografica de un reporte.
 *
 * El campo imagen_url puede contener dos cosas distintas:
 *
 *   - Una URL de Cloudinary, en los reportes con foto real
 *   - Un identificador "demo_N", en los reportes creados durante la
 *     etapa de demostracion, antes de integrar la camara
 *
 * Esta extension resuelve ambos casos para que los reportes antiguos
 * sigan viendose bien y no haya que migrar la base de datos.
 */

/** Ancho con el que se pide la imagen en el listado de reportes. */
const val ANCHO_TARJETA = 600

/** Ancho con el que se pide en el detalle, que ocupa toda la pantalla. */
const val ANCHO_DETALLE = 1000

fun ImageView.cargarEvidencia(referencia: String, ancho: Int = ANCHO_TARJETA) {
    when {
        referencia.isBlank() ->
            setImageResource(R.drawable.foto_demo_1)

        FotosDemo.esSimulada(referencia) ->
            setImageResource(FotosDemo.drawableDesde(referencia))

        else -> Glide.with(this)
            .load(optimizar(referencia, ancho))
            .placeholder(R.drawable.bg_marcador_imagen)
            .error(R.drawable.bg_marcador_imagen)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            // Un desvanecido corto disimula la espera de red
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200))
            .centerCrop()
            .into(this)
    }
}

/**
 * Pide a Cloudinary una version adaptada al tamano real de la vista.
 *
 * Sin esto se descarga la imagen completa de 1280 px para pintarla en
 * una tarjeta de 180 dp: cuatro veces mas datos de los necesarios.
 *
 *   w_<ancho>  redimensiona del lado del servidor
 *   q_auto     ajusta la compresion segun el contenido de la imagen
 *   f_auto     entrega WebP o AVIF si el dispositivo los soporta
 *
 * Cloudinary genera cada variante una sola vez y la deja cacheada en
 * su CDN, asi que no se gasta una transformacion por cada carga.
 */
private fun optimizar(url: String, ancho: Int): String {
    val marca = "/image/upload/"

    if (!url.contains("res.cloudinary.com") || !url.contains(marca)) {
        return url
    }
    // Evita encadenar transformaciones si la URL ya trae una
    if (url.substringAfter(marca).startsWith("w_")) {
        return url
    }

    return url.replace(marca, "${marca}w_$ancho,q_auto,f_auto/")
}

/** Muestra una imagen recien elegida del dispositivo, antes de subirla. */
fun ImageView.cargarUriLocal(uri: Uri) {
    Glide.with(this)
        .load(uri)
        .placeholder(R.drawable.bg_marcador_imagen)
        // Una foto recien tomada no debe cachearse: cambia en cada captura
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .skipMemoryCache(true)
        .centerCrop()
        .into(this)
}