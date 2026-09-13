package com.aqualer.app.data.model

import com.aqualer.app.data.estado.EstadoUsuario
import com.aqualer.app.data.estado.RolUsuario
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * Clase Usuario del diagrama de clases.
 *
 *
 * Nota sobre "password"
 * la contrasena NUNCA se almacena en esta clase ni en Firestore.
 * Firebase Authentication la guarda cifrada con scrypt en su propio
 * servicio, por eso el atributo no aparece como campo persistente.
 *
 * Las subclases Visitante, Ciudadano y Administrador extienden esta clase
 */
open class Usuario(
    val id: String = "",
    val nombre: String = "",
    val email: String = "",
    val fotoPerfil: String = "",
    val rol: RolUsuario = RolUsuario.VISITANTE,
    val activo: Boolean = true,
    val estado: EstadoUsuario = EstadoUsuario.NO_REGISTRADO,
    val fechaRegistro: Date = Date(),
    /** Token de Firebase Cloud Messaging para enviar notificaciones push. */
    val tokenFcm: String = ""
) {

    /** Iniciales para mostrar cuando el usuario no tiene foto de perfil. */
    val iniciales: String
        get() = nombre.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "?" }

    /** editarPerfil() del diagrama de clases. Devuelve una copia modificada. */
    fun editarPerfil(nuevoNombre: String, nuevaFoto: String = fotoPerfil): Usuario =
        crearCon(nombre = nuevoNombre, fotoPerfil = nuevaFoto)

    /**
     * Aplica una transicion de estado validandola contra el diagrama.
     * Lanza IllegalStateException si la transicion no esta permitida.
     */
    fun cambiarEstado(nuevoEstado: EstadoUsuario): Usuario {
        check(estado.puedeTransicionarA(nuevoEstado)) {
            "Transicion invalida de usuario: $estado -> $nuevoEstado"
        }
        return crearCon(estado = nuevoEstado, activo = nuevoEstado.puedeAcceder)
    }

    /** Construye una copia conservando la subclase correcta segun el rol. */
    private fun crearCon(
        nombre: String = this.nombre,
        fotoPerfil: String = this.fotoPerfil,
        estado: EstadoUsuario = this.estado,
        activo: Boolean = this.activo
    ): Usuario = construir(
        id = id,
        nombre = nombre,
        email = email,
        fotoPerfil = fotoPerfil,
        rol = rol,
        activo = activo,
        estado = estado,
        fechaRegistro = fechaRegistro,
        tokenFcm = tokenFcm
    )

    /** Serializa el usuario para guardarlo en la coleccion "usuarios". */
    fun aMapa(): Map<String, Any?> = mapOf(
        CAMPO_NOMBRE to nombre,
        CAMPO_EMAIL to email,
        CAMPO_FOTO to fotoPerfil,
        CAMPO_ROL to rol.valor,
        CAMPO_ACTIVO to activo,
        CAMPO_ESTADO to estado.valor,
        CAMPO_FECHA_REGISTRO to Timestamp(fechaRegistro),
        CAMPO_TOKEN_FCM to tokenFcm
    )

    companion object {
        const val CAMPO_NOMBRE = "nombre"
        const val CAMPO_EMAIL = "email"
        const val CAMPO_FOTO = "foto_perfil"
        const val CAMPO_ROL = "rol"
        const val CAMPO_ACTIVO = "activo"
        const val CAMPO_ESTADO = "estado"
        const val CAMPO_FECHA_REGISTRO = "fecha_registro"
        const val CAMPO_TOKEN_FCM = "token_fcm"

        /**
         * Fabrica que devuelve la subclase correcta segun el rol.
         * Es el punto unico donde se decide si el usuario es
         * Visitante, Ciudadano o Administrador.
         */
        fun construir(
            id: String,
            nombre: String,
            email: String,
            fotoPerfil: String = "",
            rol: RolUsuario,
            activo: Boolean = true,
            estado: EstadoUsuario = EstadoUsuario.REGISTRADO,
            fechaRegistro: Date = Date(),
            tokenFcm: String = ""
        ): Usuario = when (rol) {
            RolUsuario.VISITANTE -> Visitante()
            RolUsuario.CIUDADANO -> Ciudadano(
                id, nombre, email, fotoPerfil, activo, estado, fechaRegistro, tokenFcm
            )
            RolUsuario.ADMINISTRADOR -> Administrador(
                id, nombre, email, fotoPerfil, activo, estado, fechaRegistro, tokenFcm
            )
        }

        /** Reconstruye el usuario desde un documento de Firestore. */
        fun desde(doc: DocumentSnapshot): Usuario = construir(
            id = doc.id,
            nombre = doc.getString(CAMPO_NOMBRE).orEmpty(),
            email = doc.getString(CAMPO_EMAIL).orEmpty(),
            fotoPerfil = doc.getString(CAMPO_FOTO).orEmpty(),
            rol = RolUsuario.desde(doc.getString(CAMPO_ROL)),
            activo = doc.getBoolean(CAMPO_ACTIVO) ?: true,
            estado = EstadoUsuario.desde(doc.getString(CAMPO_ESTADO)),
            fechaRegistro = doc.getTimestamp(CAMPO_FECHA_REGISTRO)?.toDate() ?: Date(),
            tokenFcm = doc.getString(CAMPO_TOKEN_FCM).orEmpty()
        )
    }
}