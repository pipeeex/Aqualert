package com.aqualer.app.data.estado

/**
 * Maquina de estados de la clase Usuario.
 *
 * Transiciones definidas en el diagrama de estados USUARIO:
 *
 *   [inicio] --> NO_REGISTRADO
 *   NO_REGISTRADO --registrarse()--> REGISTRADO
 *   REGISTRADO    --iniciarSesion()--> ACTIVO
 *   ACTIVO        --cerrarSesion()--> REGISTRADO
 *   REGISTRADO    --bloquearUsuario() [Admin]--> BLOQUEADO
 *   ACTIVO        --bloquearUsuario() [Admin]--> BLOQUEADO
 *   BLOQUEADO     --habilitarUsuario() [Admin]--> REGISTRADO
 *   REGISTRADO    --eliminarCuenta()--> ELIMINADO
 *   ELIMINADO     --> [fin]
 */
enum class EstadoUsuario(val valor: String) {

    /** Accede como visitante, sin cuenta. */
    NO_REGISTRADO("no_registrado"),

    /** Cuenta activa y verificada, sin sesion abierta. */
    REGISTRADO("registrado"),

    /** Sesion en curso. */
    ACTIVO("activo"),

    /** Sin acceso a la plataforma (bloqueado por un administrador). */
    BLOQUEADO("bloqueado"),

    /** Cuenta permanentemente removida. */
    ELIMINADO("eliminado");

    /** Indica si el usuario puede usar la aplicacion. */
    val puedeAcceder: Boolean
        get() = this == REGISTRADO || this == ACTIVO

    /**
     * Valida si es posible pasar de este estado al [destino],
     * respetando estrictamente el diagrama de estados.
     */
    fun puedeTransicionarA(destino: EstadoUsuario): Boolean = when (this) {
        NO_REGISTRADO -> destino == REGISTRADO
        REGISTRADO -> destino == ACTIVO || destino == BLOQUEADO || destino == ELIMINADO
        ACTIVO -> destino == REGISTRADO || destino == BLOQUEADO
        BLOQUEADO -> destino == REGISTRADO
        ELIMINADO -> false
    }

    companion object {
        /** Convierte el valor almacenado en base de datos al enum. */
        fun desde(valor: String?): EstadoUsuario =
            entries.firstOrNull { it.valor == valor } ?: NO_REGISTRADO
    }
}