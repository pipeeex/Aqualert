package com.aqualer.app.data.model

import androidx.annotation.ColorRes
import com.aqualer.app.R
import com.aqualer.app.data.estado.EstadoAlerta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import java.util.Date

/**
 * Nivel de riesgo de una alerta.
 * Determina si la alerta puede escalarse a atencion prioritaria.
 */
enum class NivelRiesgo(
    val valor: String,
    val peso: Int,
    @ColorRes val color: Int
) {
    BAJO("bajo", 1, R.color.riesgo_bajo),
    MEDIO("medio", 2, R.color.riesgo_medio),
    ALTO("alto", 3, R.color.riesgo_alto),
    CRITICO("critico", 4, R.color.riesgo_critico);

    /** Guarda [nivel critico] de la transicion EN_SEGUIMIENTO --> ESCALADA. */
    val esCritico: Boolean
        get() = this == CRITICO

    companion object {
        fun desde(valor: String?): NivelRiesgo =
            entries.firstOrNull { it.valor == valor } ?: BAJO

        /**
         * Deriva el nivel de riesgo a partir de la clasificacion automatica.
         * A mayor confianza del modelo sobre una contaminacion severa,
         * mayor nivel de riesgo asignado a la alerta.
         */
        fun desdeClasificacion(clasificacion: String, confianza: Float): NivelRiesgo {
            val severa = clasificacion.contains("vertimiento", true) ||
                    clasificacion.contains("quimico", true) ||
                    clasificacion.contains("aguas_residuales", true)
            return when {
                severa && confianza >= 0.85f -> CRITICO
                severa -> ALTO
                confianza >= 0.85f -> MEDIO
                else -> BAJO
            }
        }
    }
}

/** Tipo de alerta emitida por el sistema. */
enum class TipoAlerta(val valor: String) {
    CONTAMINACION_DETECTADA("contaminacion_detectada"),
    REPORTE_VERIFICADO("reporte_verificado"),
    ZONA_CRITICA("zona_critica"),
    SEGUIMIENTO("seguimiento");

    companion object {
        fun desde(valor: String?): TipoAlerta =
            entries.firstOrNull { it.valor == valor } ?: CONTAMINACION_DETECTADA
    }
}

/**
 * Clase Alerta del diagrama de clases.
 *
 * Atributos del diagrama:
 *   String id, tipo, nivel_riesgo, estado
 *   DateTime fecha_emision
 *
 * Metodos del diagrama: emitir(), resolverAlerta(), escalar()
 *
 * Relaciones: Reporte 1 --genera--> 0..1 Alerta
 *             Alerta 1 --emite--> 0..* Notificacion
 */
data class Alerta(
    val id: String = "",

    /** Reporte que genero la alerta. */
    val idReporte: String = "",

    val tipo: TipoAlerta = TipoAlerta.CONTAMINACION_DETECTADA,

    val nivelRiesgo: NivelRiesgo = NivelRiesgo.BAJO,

    val estado: EstadoAlerta = EstadoAlerta.GENERADA,

    val fechaEmision: Date = Date(),

    /** Mensaje que se envia en la notificacion asociada. */
    val mensaje: String = ""
) {

    /** emitir(): GENERADA --> EMITIDA. */
    fun emitir(): Alerta = cambiarEstado(EstadoAlerta.EMITIDA)

    /**
     * escalar(): EMITIDA --> EN_SEGUIMIENTO, y
     *            EN_SEGUIMIENTO --> ESCALADA solo si el nivel es critico.
     */
    fun escalar(): Alerta = when (estado) {
        EstadoAlerta.EMITIDA -> cambiarEstado(EstadoAlerta.EN_SEGUIMIENTO)
        EstadoAlerta.EN_SEGUIMIENTO -> {
            check(nivelRiesgo.esCritico) {
                "Solo las alertas de nivel critico pueden escalarse a ESCALADA"
            }
            cambiarEstado(EstadoAlerta.ESCALADA)
        }
        else -> error("No se puede escalar una alerta en estado $estado")
    }

    /** resolverAlerta(): desde EMITIDA, EN_SEGUIMIENTO o ESCALADA --> RESUELTA. */
    fun resolverAlerta(): Alerta = cambiarEstado(EstadoAlerta.RESUELTA)

    /** archivar(): RESUELTA --> ARCHIVADA. */
    fun archivar(): Alerta = cambiarEstado(EstadoAlerta.ARCHIVADA)

    /** Valida la transicion contra la maquina de estados de la Alerta. */
    fun cambiarEstado(nuevoEstado: EstadoAlerta): Alerta {
        check(estado.puedeTransicionarA(nuevoEstado)) {
            "Transicion invalida de alerta: $estado -> $nuevoEstado"
        }
        return copy(estado = nuevoEstado)
    }

    fun aMapa(usarTimestampServidor: Boolean = true): Map<String, Any?> = mapOf(
        CAMPO_ID_REPORTE to idReporte,
        CAMPO_TIPO to tipo.valor,
        CAMPO_NIVEL_RIESGO to nivelRiesgo.valor,
        CAMPO_ESTADO to estado.valor,
        CAMPO_MENSAJE to mensaje,
        CAMPO_FECHA_EMISION to if (usarTimestampServidor) FieldValue.serverTimestamp()
        else Timestamp(fechaEmision)
    )

    companion object {
        const val CAMPO_ID_REPORTE = "id_reporte"
        const val CAMPO_TIPO = "tipo"
        const val CAMPO_NIVEL_RIESGO = "nivel_riesgo"
        const val CAMPO_ESTADO = "estado"
        const val CAMPO_MENSAJE = "mensaje"
        const val CAMPO_FECHA_EMISION = "fecha_emision"

        fun desde(doc: DocumentSnapshot): Alerta = Alerta(
            id = doc.id,
            idReporte = doc.getString(CAMPO_ID_REPORTE).orEmpty(),
            tipo = TipoAlerta.desde(doc.getString(CAMPO_TIPO)),
            nivelRiesgo = NivelRiesgo.desde(doc.getString(CAMPO_NIVEL_RIESGO)),
            estado = EstadoAlerta.desde(doc.getString(CAMPO_ESTADO)),
            mensaje = doc.getString(CAMPO_MENSAJE).orEmpty(),
            fechaEmision = doc.getTimestamp(CAMPO_FECHA_EMISION)?.toDate() ?: Date()
        )
    }
}