package com.aqualer.app.data.model

import android.os.Parcelable
import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.util.Constantes
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Clase Reporte del diagrama de clases.
 *
 * Atributos del diagrama:
 *   String id, titulo, descripcion, foto_url, estado
 *   Float latitud, longitud
 *   String direccion
 *   DateTime fecha_creacion
 *   String clasificacion_ia
 *   Float confianza_ia
 *
 * Metodos del diagrama: crear(), actualizar(), eliminar(), cambiarEstado()
 *
 * En el Modelo Estructural de Base de Datos corresponde a la coleccion
 * "reportes" con la subcoleccion "historial_estados".
 */
@Parcelize
data class Reporte(
    val id: String = "",

    /** uid_usuario: autor del reporte (relacion Ciudadano 1 --crea--> 0..* Reporte). */
    val uidUsuario: String = "",

    /** Nombre del autor, desnormalizado para no consultar dos colecciones (RNF004). */
    val nombreUsuario: String = "",

    val titulo: String = "",
    val descripcion: String = "",

    /** Georreferenciacion: latitud, longitud y direccion del diagrama. */
    val ubicacion: Ubicacion = Ubicacion(),

    /** foto_url / imagen_url: evidencia fotografica almacenada en Storage. */
    val imagenUrl: String = "",

    val estado: EstadoReporte = EstadoReporte.BORRADOR,

    val fechaCreacion: Date = Date(),

    /** Etiqueta devuelta por el ClasificadorIA. */
    val clasificacionIa: String = "",

    /** Nivel de confianza de la clasificacion (0.0 a 1.0). */
    val confianzaIa: Float = 0f,

    /** Contador de comentarios, para mostrarlo en el listado sin consultar. */
    val totalComentarios: Int = 0
) : Parcelable {

    /** Latitud del diagrama de clases. */
    val latitud: Double get() = ubicacion.lat

    /** Longitud del diagrama de clases. */
    val longitud: Double get() = ubicacion.lng

    /** Direccion del diagrama de clases. */
    val direccion: String get() = ubicacion.direccion

    /** Confianza en porcentaje entero, para mostrarla en la interfaz. */
    val confianzaPorcentaje: Int get() = (confianzaIa * 100).toInt()

    /** La clasificacion supero el umbral definido para el modelo. */
    val clasificacionConfiable: Boolean
        get() = confianzaIa >= Constantes.UMBRAL_CONFIANZA_IA

    /**
     * Valida los campos obligatorios antes de enviar el reporte (RNF007).
     * El diagrama de estados exige foto + descripcion para salir de BORRADOR.
     */
    fun validar(): ResultadoValidacion {
        val errores = mutableListOf<String>()

        if (titulo.trim().length < Constantes.MIN_LONGITUD_TITULO) {
            errores.add("El titulo debe tener al menos ${Constantes.MIN_LONGITUD_TITULO} caracteres")
        }
        if (titulo.trim().length > Constantes.MAX_LONGITUD_TITULO) {
            errores.add("El titulo no puede superar ${Constantes.MAX_LONGITUD_TITULO} caracteres")
        }
        if (descripcion.trim().length < Constantes.MIN_LONGITUD_DESCRIPCION) {
            errores.add("La descripcion debe tener al menos ${Constantes.MIN_LONGITUD_DESCRIPCION} caracteres")
        }
        if (descripcion.trim().length > Constantes.MAX_LONGITUD_DESCRIPCION) {
            errores.add("La descripcion no puede superar ${Constantes.MAX_LONGITUD_DESCRIPCION} caracteres")
        }
        if (imagenUrl.isBlank()) {
            errores.add("Debes adjuntar una foto de evidencia")
        }
        if (!ubicacion.esValida) {
            errores.add("Debes registrar la ubicacion del reporte")
        }

        return if (errores.isEmpty()) ResultadoValidacion.Valido
        else ResultadoValidacion.Invalido(errores)
    }

    /**
     * cambiarEstado() del diagrama de clases.
     * Valida la transicion contra la maquina de estados del Reporte.
     */
    fun cambiarEstado(nuevoEstado: EstadoReporte): Reporte {
        check(estado.puedeTransicionarA(nuevoEstado)) {
            "Transicion invalida de reporte: $estado -> $nuevoEstado"
        }
        return copy(estado = nuevoEstado)
    }

    /** Serializa el reporte para la coleccion "reportes". */
    fun aMapa(usarTimestampServidor: Boolean = false): Map<String, Any?> = mapOf(
        CAMPO_UID_USUARIO to uidUsuario,
        CAMPO_NOMBRE_USUARIO to nombreUsuario,
        CAMPO_TITULO to titulo.trim(),
        CAMPO_DESCRIPCION to descripcion.trim(),
        CAMPO_UBICACION to ubicacion.aMapa(),
        CAMPO_IMAGEN_URL to imagenUrl,
        CAMPO_ESTADO to estado.valor,
        CAMPO_CLASIFICACION_IA to clasificacionIa,
        CAMPO_CONFIANZA_IA to confianzaIa.toDouble(),
        CAMPO_TOTAL_COMENTARIOS to totalComentarios,
        CAMPO_FECHA to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(fechaCreacion)
    )

    companion object {
        const val CAMPO_UID_USUARIO = "uid_usuario"
        const val CAMPO_NOMBRE_USUARIO = "nombre_usuario"
        const val CAMPO_TITULO = "titulo"
        const val CAMPO_DESCRIPCION = "descripcion"
        const val CAMPO_UBICACION = "ubicacion"
        const val CAMPO_IMAGEN_URL = "imagen_url"
        const val CAMPO_ESTADO = "estado"
        const val CAMPO_FECHA = "fecha"
        const val CAMPO_CLASIFICACION_IA = "clasificacion_ia"
        const val CAMPO_CONFIANZA_IA = "confianza_ia"
        const val CAMPO_TOTAL_COMENTARIOS = "total_comentarios"

        /** Reconstruye el reporte desde un documento de Firestore. */
        @Suppress("UNCHECKED_CAST")
        fun desde(doc: DocumentSnapshot): Reporte = Reporte(
            id = doc.id,
            uidUsuario = doc.getString(CAMPO_UID_USUARIO).orEmpty(),
            nombreUsuario = doc.getString(CAMPO_NOMBRE_USUARIO).orEmpty(),
            titulo = doc.getString(CAMPO_TITULO).orEmpty(),
            descripcion = doc.getString(CAMPO_DESCRIPCION).orEmpty(),
            ubicacion = Ubicacion.desde(doc.get(CAMPO_UBICACION) as? Map<String, Any?>),
            imagenUrl = doc.getString(CAMPO_IMAGEN_URL).orEmpty(),
            estado = EstadoReporte.desde(doc.getString(CAMPO_ESTADO)),
            fechaCreacion = doc.getTimestamp(CAMPO_FECHA)?.toDate() ?: Date(),
            clasificacionIa = doc.getString(CAMPO_CLASIFICACION_IA).orEmpty(),
            confianzaIa = (doc.getDouble(CAMPO_CONFIANZA_IA) ?: 0.0).toFloat(),
            totalComentarios = (doc.getLong(CAMPO_TOTAL_COMENTARIOS) ?: 0L).toInt()
        )
    }
}

/** Resultado de validar un formulario antes de guardarlo (RNF007). */
sealed class ResultadoValidacion {
    data object Valido : ResultadoValidacion()
    data class Invalido(val errores: List<String>) : ResultadoValidacion() {
        val primerError: String get() = errores.firstOrNull().orEmpty()
    }

    val esValido: Boolean get() = this is Valido
}