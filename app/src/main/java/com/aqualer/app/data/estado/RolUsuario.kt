package com.aqualer.app.data.estado

/**
 * Roles definidos en el entregable 5 "Definicion de actores y roles".
 *
 *  - VISITANTE: no registrado, externo. Solo consulta.
 *  - CIUDADANO: registrado, actor principal. Crea reportes y comenta.
 *  - ADMINISTRADOR: privilegiado, externo. Modera y gestiona.
 */
enum class RolUsuario(val valor: String) {

    VISITANTE("visitante"),
    CIUDADANO("ciudadano"),
    ADMINISTRADOR("administrador");

    /** Solo los usuarios autenticados tienen cuenta en el sistema. */
    val estaAutenticado: Boolean
        get() = this != VISITANTE

    /** UC5: crear reporte. Exclusivo del Ciudadano. */
    val puedeCrearReportes: Boolean
        get() = this == CIUDADANO

    /** UC8: comentar reporte. Exclusivo del Ciudadano. */
    val puedeComentar: Boolean
        get() = this == CIUDADANO

    /** UC10: usar chatbot. Disponible para Visitante y Ciudadano. */
    val puedeUsarChatbot: Boolean
        get() = this == VISITANTE || this == CIUDADANO

    /** UC12: actualizar estado de reporte, moderar, gestionar usuarios. */
    val puedeModerar: Boolean
        get() = this == ADMINISTRADOR

    companion object {
        fun desde(valor: String?): RolUsuario =
            entries.firstOrNull { it.valor == valor } ?: VISITANTE
    }
}