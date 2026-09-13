package com.aqualer.app.util

import android.util.Patterns
import com.aqualer.app.data.model.ResultadoValidacion

/**
 * Validaciones de formularios (RNF007 - Integridad de los datos).
 *
* Corresponde al paso "1.1 validar formulario" del diagrama de
* colaboracion del Proceso de Autenticacion: la validacion ocurre
* en la interfaz antes de llamar al servicio.
*/
object Validador {

    /** Comprueba que el correo tenga un formato valido. */
    fun emailValido(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    /** La contrasena debe cumplir la longitud minima. */
    fun passwordValida(password: String): Boolean =
        password.length >= Constantes.MIN_LONGITUD_PASSWORD

    /** El nombre debe tener al menos dos caracteres. */
    fun nombreValido(nombre: String): Boolean =
        nombre.trim().length >= 2

    /**
     * Valida el formulario de registro completo.
     * Devuelve todos los errores encontrados, no solo el primero.
     */
    fun validarRegistro(
        nombre: String,
        email: String,
        password: String,
        confirmacion: String
    ): ResultadoValidacion {
        val errores = mutableListOf<String>()

        if (!nombreValido(nombre)) errores.add("Ingresa tu nombre completo")
        if (!emailValido(email)) errores.add("Ingresa un correo valido")
        if (!passwordValida(password)) {
            errores.add("La contrasena debe tener al menos ${Constantes.MIN_LONGITUD_PASSWORD} caracteres")
        }
        if (password != confirmacion) errores.add("Las contrasenas no coinciden")

        return if (errores.isEmpty()) ResultadoValidacion.Valido
        else ResultadoValidacion.Invalido(errores)
    }

    /** Valida el formulario de inicio de sesion. */
    fun validarLogin(email: String, password: String): ResultadoValidacion {
        val errores = mutableListOf<String>()

        if (!emailValido(email)) errores.add("Ingresa un correo valido")
        if (password.isBlank()) errores.add("Ingresa tu contrasena")

        return if (errores.isEmpty()) ResultadoValidacion.Valido
        else ResultadoValidacion.Invalido(errores)
    }
}