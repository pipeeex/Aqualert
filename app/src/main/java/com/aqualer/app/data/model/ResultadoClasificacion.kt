package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoClasificadorIA
import com.aqualer.app.util.Constantes

/**
 * Etiqueta individual devuelta por el modelo de clasificacion.
 * Corresponde a obtenerEtiquetas() de la clase ClasificadorIA.
 */
data class EtiquetaIA(
    val nombre: String,
    val confianza: Float
) {
    val porcentaje: Int get() = (confianza * 100).toInt()
}

/**
 * Resultado de clasificarImagen() de la clase ClasificadorIA.
 *
 * El estado resultante sigue el diagrama de estados CLASIFICADOR IA:
 *   confianza >= umbral            --> EXITOSO
 *   confianza <  umbral            --> DUDOSO   (requiere revision humana)
 *   fallo del modelo               --> ERROR
 *
 * Ese estado es el que decide la transicion del Reporte:
 *   EN_REVISION --clasificarImagen() OK--> PUBLICADO
 *   EN_REVISION --clasificarImagen() FAIL--> RECHAZADO
 */
data class ResultadoClasificacion(
    /** Etiqueta principal, la de mayor confianza. */
    val clasificacion: String = "",

    /** Confianza de la etiqueta principal (0.0 a 1.0). */
    val confianza: Float = 0f,

    /** Todas las etiquetas detectadas, ordenadas de mayor a menor. */
    val etiquetas: List<EtiquetaIA> = emptyList(),

    /** Estado final del clasificador tras procesar la imagen. */
    val estado: EstadoClasificadorIA = EstadoClasificadorIA.INACTIVO,

    /** Mensaje de error cuando el estado es ERROR. */
    val error: String = "",

    /** Version del modelo que produjo el resultado. */
    val versionModelo: String = Constantes.VERSION_MODELO_IA
) {

    /** La imagen corresponde a contaminacion con confianza suficiente. */
    val esExitoso: Boolean
        get() = estado == EstadoClasificadorIA.EXITOSO

    /** Confianza baja: el reporte se publica pero queda marcado para revision. */
    val requiereRevisionHumana: Boolean
        get() = estado == EstadoClasificadorIA.DUDOSO

    /** El modelo fallo o la imagen no es valida. */
    val fallo: Boolean
        get() = estado == EstadoClasificadorIA.ERROR

    val confianzaPorcentaje: Int get() = (confianza * 100).toInt()

    companion object {

        /** Construye el resultado aplicando la regla del umbral. */
        fun evaluar(
            clasificacion: String,
            confianza: Float,
            etiquetas: List<EtiquetaIA> = emptyList()
        ): ResultadoClasificacion {
            val estado = if (confianza >= Constantes.UMBRAL_CONFIANZA_IA) {
                EstadoClasificadorIA.EXITOSO
            } else {
                EstadoClasificadorIA.DUDOSO
            }
            return ResultadoClasificacion(
                clasificacion = clasificacion,
                confianza = confianza,
                etiquetas = etiquetas,
                estado = estado
            )
        }

        /** Resultado de un fallo del modelo. */
        fun conError(mensaje: String): ResultadoClasificacion =
            ResultadoClasificacion(
                estado = EstadoClasificadorIA.ERROR,
                error = mensaje
            )
    }
}