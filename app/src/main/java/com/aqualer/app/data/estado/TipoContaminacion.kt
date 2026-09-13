package com.aqualer.app.data.estado

/**
 * Tipos de contaminacion que puede reportar el ciudadano.
 *
 * Alimenta la lista desplegable del formulario y corresponde al
 * atributo clasificacion_ia del Reporte: por ahora lo elige el usuario,
 * y cuando entre el ClasificadorIA sera el modelo quien lo proponga.
 */
enum class TipoContaminacion(val valor: String, val etiqueta: String) {

    BASURAS("basuras", "Basuras y residuos solidos"),
    VERTIMIENTO("vertimiento", "Vertimiento industrial"),
    AGUAS_RESIDUALES("aguas_residuales", "Aguas residuales"),
    ESPUMAS("espumas", "Espumas o detergentes"),
    ESCOMBROS("escombros", "Escombros"),
    MAL_OLOR("mal_olor", "Malos olores"),
    OTRO("otro", "Otro");

    companion object {
        fun desde(valor: String?): TipoContaminacion =
            entries.firstOrNull { it.valor == valor } ?: OTRO

        /** Etiquetas para llenar la lista desplegable. */
        fun etiquetas(): List<String> = entries.map { it.etiqueta }

        /** Posicion dentro de la lista desplegable. */
        fun indiceDe(tipo: TipoContaminacion): Int = entries.indexOf(tipo)

        /** Tipo seleccionado a partir de la posicion del desplegable. */
        fun porIndice(indice: Int): TipoContaminacion =
            entries.getOrElse(indice) { OTRO }
    }
}