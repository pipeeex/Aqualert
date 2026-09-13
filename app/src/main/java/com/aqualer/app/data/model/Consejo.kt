package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoConsejo
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

/**
 * Categoria del consejo. Corresponde al campo "tipo" de la
 * coleccion Consejos del Modelo Estructural de Base de Datos,
 * y habilita el caso de uso "Ver consejos por categoria".
 */
enum class CategoriaConsejo(val valor: String, val etiqueta: String) {
    PREVENCION("prevencion", "Prevencion"),
    AHORRO("ahorro", "Ahorro de agua"),
    COMO_REPORTAR("como_reportar", "Como reportar"),
    NORMATIVA("normativa", "Normativa"),
    EMERGENCIA("emergencia", "Que hacer ante contaminacion");

    companion object {
        fun desde(valor: String?): CategoriaConsejo =
            entries.firstOrNull { it.valor == valor } ?: PREVENCION
    }
}

/**
 * Clase Consejo del diagrama de clases (RF0005).
 *
 * Atributos del diagrama:
 *   String id, titulo, contenido, categoria
 *   DateTime fecha_publicacion
 *
 * Metodos del diagrama: publicar(), archivar()
 *
 * Relacion: Visitante --consulta--> Consejo
 */
data class Consejo(
    val id: String = "",

    val titulo: String = "",

    val contenido: String = "",

    val categoria: CategoriaConsejo = CategoriaConsejo.PREVENCION,

    val estado: EstadoConsejo = EstadoConsejo.BORRADOR,

    val fechaPublicacion: Date = Date(),

    /** Orden de aparicion en la seccion de consejos. */
    val orden: Int = 0
) {

    /** publicar() [Admin]: BORRADOR --> PUBLICADO. */
    fun publicar(): Consejo = cambiarEstado(EstadoConsejo.PUBLICADO)

    /** despublicar() [Admin]: PUBLICADO --> BORRADOR. */
    fun despublicar(): Consejo = cambiarEstado(EstadoConsejo.BORRADOR)

    /** archivar() [Admin]: PUBLICADO --> ARCHIVADO. */
    fun archivar(): Consejo = cambiarEstado(EstadoConsejo.ARCHIVADO)

    /** restaurar() [Admin]: ARCHIVADO --> PUBLICADO. */
    fun restaurar(): Consejo = cambiarEstado(EstadoConsejo.PUBLICADO)

    /** Valida la transicion contra la maquina de estados del Consejo. */
    fun cambiarEstado(nuevoEstado: EstadoConsejo): Consejo {
        check(estado.puedeTransicionarA(nuevoEstado)) {
            "Transicion invalida de consejo: $estado -> $nuevoEstado"
        }
        return copy(estado = nuevoEstado)
    }

    fun aMapa(usarTimestampServidor: Boolean = false): Map<String, Any?> = mapOf(
        CAMPO_TITULO to titulo.trim(),
        CAMPO_CONTENIDO to contenido.trim(),
        CAMPO_TIPO to categoria.valor,
        CAMPO_ESTADO to estado.valor,
        CAMPO_ORDEN to orden,
        CAMPO_FECHA_CREACION to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(fechaPublicacion)
    )

    companion object {
        const val CAMPO_TITULO = "titulo"
        const val CAMPO_CONTENIDO = "contenido"
        const val CAMPO_TIPO = "tipo"
        const val CAMPO_ESTADO = "estado"
        const val CAMPO_ORDEN = "orden"
        const val CAMPO_FECHA_CREACION = "fecha_creacion"

        fun desde(doc: DocumentSnapshot): Consejo = Consejo(
            id = doc.id,
            titulo = doc.getString(CAMPO_TITULO).orEmpty(),
            contenido = doc.getString(CAMPO_CONTENIDO).orEmpty(),
            categoria = CategoriaConsejo.desde(doc.getString(CAMPO_TIPO)),
            estado = EstadoConsejo.desde(doc.getString(CAMPO_ESTADO)),
            orden = (doc.getLong(CAMPO_ORDEN) ?: 0L).toInt(),
            fechaPublicacion = doc.getTimestamp(CAMPO_FECHA_CREACION)?.toDate() ?: Date()
        )
    }
}