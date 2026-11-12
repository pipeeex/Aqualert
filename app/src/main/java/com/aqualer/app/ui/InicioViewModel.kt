package com.aqualer.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.data.service.AuthService
import com.aqualer.app.data.service.ReportesService
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de inicio.
 *
 * Reune lo que la persona necesita ver al entrar: quien es, cuantos
 * reportes ha aportado y a que puede acceder segun su rol.
 */
class InicioViewModel(
    private val authService: AuthService = AuthService(),
    private val reportesService: ReportesService = ReportesService()
) : ViewModel() {

    private val _usuario = MutableLiveData<Usuario>()
    val usuario: LiveData<Usuario> = _usuario

    /** Cantidad de reportes creados por el usuario autenticado. */
    private val _totalReportes = MutableLiveData<Int>()
    val totalReportes: LiveData<Int> = _totalReportes

    /** Carga el perfil y su aporte a la plataforma. */
    fun cargar() {
        viewModelScope.launch {
            val actual = authService.obtenerUsuarioActual()
            _usuario.value = actual

            // El visitante no tiene reportes propios que contar
            if (actual.rol.estaAutenticado && actual.id.isNotBlank()) {
                _totalReportes.value = reportesService
                    .listarPorUsuario(actual.id)
                    .datosONull()
                    ?.size ?: 0
            } else {
                _totalReportes.value = 0
            }
        }
    }

    /** cerrarSesion(): ACTIVO --> REGISTRADO. */
    fun cerrarSesion(alTerminar: () -> Unit) {
        viewModelScope.launch {
            authService.cerrarSesion()
            alTerminar()
        }
    }
}