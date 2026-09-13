package com.aqualer.app.data.service

import com.aqualer.app.data.estado.EstadoUsuario
import com.aqualer.app.data.estado.RolUsuario
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.data.model.Visitante
import com.aqualer.app.util.Constantes
import com.aqualer.app.util.Recurso
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Servicio de Autenticacion (AuthSvc del diagrama de componentes).
 *
 * Implementa el Proceso de Autenticacion del diagrama de colaboracion:
 *   1.2 login()            -> iniciarSesion()
 *   1.3 autenticar()       -> FirebaseAuth
 *   1.4 consultar usuario  -> coleccion "usuarios"
 *   1.5 datos usuario
 *   1.6 token sesion       -> sesion gestionada por Firebase Auth
 *
 * Cumple el RNF002: las contrasenas nunca viajan ni se guardan en
 * Firestore; Firebase Authentication las almacena cifradas.
 */
class AuthService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val coleccionUsuarios get() = db.collection(Constantes.COL_USUARIOS)

    /** Indica si hay una sesion abierta. */
    fun haySesionActiva(): Boolean = auth.currentUser != null

    /** UID del usuario autenticado, o null si es visitante. */
    fun uidActual(): String? = auth.currentUser?.uid

    /**
     * registrarse() de la clase Usuario.
     *
     * Transicion del diagrama de estados:
     *   NO_REGISTRADO --registrarse()--> REGISTRADO --iniciarSesion()--> ACTIVO
     *
     * Crea la credencial en Firebase Auth y el perfil en la coleccion
     * "usuarios" con rol CIUDADANO (el actor principal del sistema).
     */
    suspend fun registrar(
        nombre: String,
        email: String,
        password: String
    ): Recurso<Usuario> = try {
        val credencial = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val uid = credencial.user?.uid
            ?: return Recurso.Error("No se pudo crear la cuenta")

        // El usuario nace REGISTRADO y queda ACTIVO porque la sesion se abre sola
        val usuario = Usuario.construir(
            id = uid,
            nombre = nombre.trim(),
            email = email.trim(),
            rol = RolUsuario.CIUDADANO,
            activo = true,
            estado = EstadoUsuario.ACTIVO,
            fechaRegistro = Date()
        )

        coleccionUsuarios.document(uid).set(usuario.aMapa()).await()
        Recurso.Exito(usuario)

    } catch (e: FirebaseAuthUserCollisionException) {
        Recurso.Error("Ya existe una cuenta registrada con este correo", e)
    } catch (e: FirebaseAuthWeakPasswordException) {
        Recurso.Error("La contrasena es demasiado debil", e)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Recurso.Error("El correo ingresado no es valido", e)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * iniciarSesion() de la clase Usuario.
     *
     * Transicion: REGISTRADO --iniciarSesion()--> ACTIVO
     *
     * Un usuario en estado BLOQUEADO no puede acceder a la plataforma,
     * por eso se cierra la sesion inmediatamente si el perfil esta bloqueado.
     */
    suspend fun iniciarSesion(email: String, password: String): Recurso<Usuario> = try {
        val credencial = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val uid = credencial.user?.uid
            ?: return Recurso.Error("No se pudo iniciar sesion")

        when (val perfil = obtenerPerfil(uid)) {
            is Recurso.Exito -> {
                val usuario = perfil.datos
                if (!usuario.estado.puedeAcceder || !usuario.activo) {
                    auth.signOut()
                    Recurso.Error("Tu cuenta esta bloqueada. Contacta al administrador.")
                } else {
                    val activo = usuario.cambiarEstado(EstadoUsuario.ACTIVO)
                    coleccionUsuarios.document(uid)
                        .update(Usuario.CAMPO_ESTADO, EstadoUsuario.ACTIVO.valor)
                        .await()
                    Recurso.Exito(activo)
                }
            }
            is Recurso.Error -> {
                auth.signOut()
                perfil
            }
            else -> Recurso.Error("No se pudo cargar el perfil")
        }

    } catch (e: FirebaseAuthInvalidUserException) {
        Recurso.Error("No existe una cuenta con este correo", e)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Recurso.Error("Correo o contrasena incorrectos", e)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * cerrarSesion() de la clase Usuario.
     * Transicion: ACTIVO --cerrarSesion()--> REGISTRADO
     */
    suspend fun cerrarSesion() {
        val uid = uidActual()
        if (uid != null) {
            runCatching {
                coleccionUsuarios.document(uid)
                    .update(Usuario.CAMPO_ESTADO, EstadoUsuario.REGISTRADO.valor)
                    .await()
            }
        }
        auth.signOut()
    }

    /**
     * "Recuperar contrasena" del diagrama de casos de uso extendidos
     * (extension de Iniciar sesion).
     */
    suspend fun recuperarPassword(email: String): Recurso<Unit> = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Recurso.Exito(Unit)
    } catch (e: FirebaseAuthInvalidUserException) {
        Recurso.Error("No existe una cuenta con este correo", e)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Carga el perfil desde la coleccion "usuarios".
     * Devuelve la subclase correcta: Ciudadano o Administrador.
     */
    suspend fun obtenerPerfil(uid: String): Recurso<Usuario> = try {
        val doc = coleccionUsuarios.document(uid).get().await()
        if (doc.exists()) {
            Recurso.Exito(Usuario.desde(doc))
        } else {
            Recurso.Error("El perfil del usuario no existe")
        }
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /**
     * Devuelve el usuario de la sesion actual.
     * Si no hay sesion, devuelve un Visitante (actor no registrado).
     */
    suspend fun obtenerUsuarioActual(): Usuario {
        val uid = uidActual() ?: return Visitante()
        return obtenerPerfil(uid).datosONull() ?: Visitante()
    }

    /** editarPerfil() de la clase Usuario. */
    suspend fun actualizarPerfil(
        uid: String,
        nombre: String,
        fotoUrl: String
    ): Recurso<Unit> = try {
        coleccionUsuarios.document(uid).update(
            mapOf(
                Usuario.CAMPO_NOMBRE to nombre.trim(),
                Usuario.CAMPO_FOTO to fotoUrl
            )
        ).await()
        Recurso.Exito(Unit)
    } catch (e: Exception) {
        Recurso.Error(mensajeDeError(e), e)
    }

    /** Guarda el token de notificaciones push del dispositivo. */
    suspend fun actualizarTokenFcm(uid: String, token: String) {
        runCatching {
            coleccionUsuarios.document(uid)
                .update(Usuario.CAMPO_TOKEN_FCM, token)
                .await()
        }
    }

    /**
     * Traduce las excepciones tecnicas a mensajes que el usuario entienda
     * (RNF001 usabilidad, RNF005 mensajes ante fallos de conexion).
     */
    private fun mensajeDeError(e: Exception): String {
        val texto = e.message.orEmpty()
        return when {
            texto.contains("network", true) ||
                    texto.contains("host", true) ->
                "Sin conexion a internet. Revisa tu red e intenta de nuevo."

            texto.contains("PERMISSION_DENIED", true) ->
                "No tienes permisos para realizar esta accion"

            texto.contains("blocked", true) ->
                "Demasiados intentos. Espera unos minutos e intenta de nuevo."

            else -> "Ocurrio un error. Intenta nuevamente."
        }
    }
}