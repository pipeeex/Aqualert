package com.aqualer.app.util

/**
 * Envoltorio del resultado de cualquier operacion de la capa de servicios.
 *
 * Permite que las pantallas muestren los tres estados posibles sin
 * conocer detalles de Firebase: cargando, exito o error.
 * Es la base del RNF005 (mostrar mensajes informativos ante fallos).
 */
sealed class Recurso<out T> {

    /** Operacion en curso. */
    data object Cargando : Recurso<Nothing>()

    /** Operacion completada con exito. */
    data class Exito<out T>(val datos: T) : Recurso<T>()

    /** Operacion fallida con un mensaje legible para el usuario. */
    data class Error(
        val mensaje: String,
        val causa: Throwable? = null
    ) : Recurso<Nothing>()

    val estaCargando: Boolean get() = this is Cargando
    val esExito: Boolean get() = this is Exito
    val esError: Boolean get() = this is Error

    /** Devuelve los datos si la operacion fue exitosa, o null. */
    fun datosONull(): T? = (this as? Exito)?.datos

    /** Ejecuta [accion] solo si hubo exito. */
    inline fun alExito(accion: (T) -> Unit): Recurso<T> {
        if (this is Exito) accion(datos)
        return this
    }

    /** Ejecuta [accion] solo si hubo error. */
    inline fun alError(accion: (String) -> Unit): Recurso<T> {
        if (this is Error) accion(mensaje)
        return this
    }
}