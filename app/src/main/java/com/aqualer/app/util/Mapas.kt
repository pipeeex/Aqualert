package com.aqualer.app.util

import android.content.Context
import android.widget.ImageView
import com.aqualer.app.BuildConfig
import com.aqualer.app.data.model.Ubicacion
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Configuracion y utilidades del mapa (RF008: visualizar la ubicacion
 * del reporte sobre cartografia).
 *
 * Se usa OpenStreetMap a traves de OSMDroid en lugar del SDK de Google
 * Maps porque este ultimo exige una cuenta de facturacion activa, lo
 * que queda fuera del alcance de un prototipo academico.
 */
object Mapas {

    /** Zoom con el que se encuadra un punto concreto: nivel de manzana. */
    const val ZOOM_PUNTO = 16.5

    /** Zoom para ver Bogota completa en el mapa general de reportes. */
    const val ZOOM_CIUDAD = 11.0

    /**
     * Debe llamarse una sola vez, al arrancar la aplicacion.
     *
     * La politica de uso de las teselas de OpenStreetMap obliga a
     * identificar a la aplicacion con un User-Agent propio. Sin esto
     * el servidor responde 403 y el mapa se queda en gris, sin ningun
     * mensaje de error visible.
     */
    fun configurar(contexto: Context) {
        val config = Configuration.getInstance()

        config.load(
            contexto,
            contexto.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )

        config.userAgentValue = BuildConfig.APPLICATION_ID

        // Las teselas son desechables: en cache no consumen la cuota
        // de almacenamiento del usuario ni requieren permisos
        config.osmdroidBasePath = File(contexto.cacheDir, "osmdroid")
        config.osmdroidTileCache = File(contexto.cacheDir, "osmdroid/teselas")
    }
}

/**
 * Prepara un MapView para mostrar la ubicacion de un reporte.
 *
 * @param interactivo false para una vista previa dentro de un
 *        formulario: el mapa no debe robar el gesto de desplazamiento
 *        al scroll que lo contiene.
 */
fun MapView.prepararComoVistaPrevia(interactivo: Boolean = false) {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(interactivo)
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

    // Sin esto el mapa consume el arrastre vertical y el formulario
    // deja de poder desplazarse
    setOnTouchListener { vista, evento ->
        vista.parent.requestDisallowInterceptTouchEvent(interactivo)
        if (interactivo) false else true
    }
}

/** Centra el mapa en la ubicacion y deja un unico marcador. */
fun MapView.mostrarPunto(ubicacion: Ubicacion, zoom: Double = Mapas.ZOOM_PUNTO) {
    if (!ubicacion.esValida) return

    val punto = GeoPoint(ubicacion.lat, ubicacion.lng)

    overlays.removeAll { it is Marker }

    val marcador = Marker(this).apply {
        position = punto
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = ubicacion.direccion.ifBlank { "Ubicacion del reporte" }
    }
    overlays.add(marcador)

    controller.setZoom(zoom)
    controller.setCenter(punto)
    invalidate()
}