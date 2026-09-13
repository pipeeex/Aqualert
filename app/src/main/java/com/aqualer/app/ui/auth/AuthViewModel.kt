package com.aqualer.app.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqualer.app.data.model.ResultadoValidacion
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.data.service.AuthService
import com.aqualer.app.util.Recurso
import com.aqualer.app.util.Validador
import kotlinx.coroutines.launch

/**
 * ViewModel del Modulo de Autenticacion (AuthUI del diagrama de componentes).
 *
 * Sigue el flujo del diagrama de colaboracion del Proceso de Autenticacion:
 *   1   ingresar credenciales  -> la Activity recoge los datos
 *   1.1 validar formulario     -> Validador
 *   1.2 login()                -> AuthService
 *   1.8 mostrar inicio         -> la Activity observa el LiveData
 *
 * Separar la logica de la Activity cumple el RNF008 (mantenibilidad)
 * y evita perder el estado al rotar la pantalla.
 */
class AuthViewModel(
    private val authService: AuthService = AuthService()
) : ViewModel() {

    /** Resultado del inicio de sesion o del registro. */
    private val _estadoAuth = MutableLiveData<Recurso<Usuario>>()
    val estadoAuth: LiveData<Recurso<Usuario>> = _estadoAuth

    /** Resultado del envio del correo de recuperacion. */
    private val _estadoRecuperacion = MutableLiveData<Recurso<Unit>>()
    val estadoRecuperacion: LiveData<Recurso<Unit>> = _estadoRecuperacion

    /** Errores de validacion del formulario, antes de llamar al servicio. */
    private val _erroresValidacion = MutableLiveData<List<String>>()
    val erroresValidacion: LiveData<List<String>> = _erroresValidacion

    /** Indica si ya existe una sesion abierta (lo usa el Splash). */
    fun haySesionActiva(): Boolean = authService.haySesionActiva()

    /**
     * iniciarSesion(): valida el formulario y autentica contra Firebase.
     */
    fun iniciarSesion(email: String, password: String) {
        when (val validacion = Validador.validarLogin(email, password)) {
            is ResultadoValidacion.Invalido -> {
                _erroresValidacion.value = validacion.errores
                return
            }
            else -> Unit
        }

        _estadoAuth.value = Recurso.Cargando
        viewModelScope.launch {
            _estadoAuth.value = authService.iniciarSesion(email, password)
        }
    }

    /**
     * registrarse(): valida el formulario, crea la credencial
     * y guarda el perfil en la coleccion "usuarios".
     */
    fun registrar(
        nombre: String,
        email: String,
        password: String,
        confirmacion: String
    ) {
        when (val validacion = Validador.validarRegistro(nombre, email, password, confirmacion)) {
            is ResultadoValidacion.Invalido -> {
                _erroresValidacion.value = validacion.errores
                return
            }
            else -> Unit
        }

        _estadoAuth.value = Recurso.Cargando
        viewModelScope.launch {
            _estadoAuth.value = authService.registrar(nombre, email, password)
        }
    }

    /** "Recuperar contrasena": extension del caso de uso Iniciar sesion. */
    fun recuperarPassword(email: String) {
        if (!Validador.emailValido(email)) {
            _erroresValidacion.value = listOf("Ingresa un correo valido")
            return
        }

        _estadoRecuperacion.value = Recurso.Cargando
        viewModelScope.launch {
            _estadoRecuperacion.value = authService.recuperarPassword(email)
        }
    }

    /** cerrarSesion(): ACTIVO --> REGISTRADO. */
    fun cerrarSesion(alTerminar: () -> Unit = {}) {
        viewModelScope.launch {
            authService.cerrarSesion()
            alTerminar()
        }
    }

    /** Carga el perfil del usuario autenticado (o un Visitante si no hay sesion). */
    fun cargarUsuarioActual() {
        _estadoAuth.value = Recurso.Cargando
        viewModelScope.launch {
            _estadoAuth.value = Recurso.Exito(authService.obtenerUsuarioActual())
        }
    }

    /** Limpia los errores ya mostrados para que no se repitan al rotar. */
    fun limpiarErrores() {
        _erroresValidacion.value = emptyList()
    }
}