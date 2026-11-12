package com.aqualer.app.ui.admin

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqualer.app.data.estado.EstadoReporte
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.data.service.AuthService
import com.aqualer.app.data.service.ReportesService
import com.aqualer.app.util.Recurso
import kotlinx.coroutines.launch

/**
 * ViewModel del panel del Administrador.
 *
 * Cubre las operaciones de la clase Administrador del diagrama:
 *   consultarTodosReportes(), filtrarReportes(), verificarReporte()
 *
 * y los casos de uso de "Administracion de reportes":
 *   Gestionar reportes --include--> Filtrar por estado
 *                      --include--> Cambiar estado del reporte
 *                      --include--> Registrar observacion
 */
class AdminViewModel(
    private val reportesService: ReportesService = ReportesService(),
    private val authService: AuthService = AuthService()
) : ViewModel() {

    private val _reportes = MutableLiveData<Recurso<List<Reporte>>>()
    val reportes: LiveData<Recurso<List<Reporte>>> = _reportes

    private val _operacion = MutableLiveData<Recurso<String>>()
    val operacion: LiveData<Recurso<String>> = _operacion

    private val _administrador = MutableLiveData<Usuario>()
    val administrador: LiveData<Usuario> = _administrador

    /** Listado completo, para filtrar sin volver a consultar la red. */
    private var todos: List<Reporte> = emptyList()

    /** Estado por el que se esta filtrando; null significa "todos". */
    private var filtroActivo: EstadoReporte? = null

    init {
        viewModelScope.launch {
            _administrador.value = authService.obtenerUsuarioActual()
        }
    }

    /**
     * consultarTodosReportes(): a diferencia del listado publico,
     * el administrador ve tambien los rechazados y los que estan
     * en revision.
     */
    fun cargar() {
        _reportes.value = Recurso.Cargando
        viewModelScope.launch {
            when (val resultado = reportesService.listar(limite = LIMITE_ADMIN)) {
                is Recurso.Exito -> {
                    todos = resultado.datos
                    aplicarFiltro()
                }
                is Recurso.Error -> _reportes.value = resultado
                else -> Unit
            }
        }
    }

    /** filtrarReportes(): "Filtrar por estado" del diagrama. */
    fun filtrarPor(estado: EstadoReporte?) {
        filtroActivo = estado
        aplicarFiltro()
    }

    private fun aplicarFiltro() {
        val filtrados = filtroActivo
            ?.let { activo -> todos.filter { it.estado == activo } }
            ?: todos

        _reportes.value = Recurso.Exito(filtrados)
    }

    /** Estados presentes en los reportes, para armar los filtros. */
    fun estadosDisponibles(): List<EstadoReporte> =
        todos.map { it.estado }.distinct().sortedBy { it.ordinal }

    /**
     * cambiarEstado() con registro de observacion.
     *
     * La transicion se valida contra la maquina de estados antes de
     * escribir, se deja traza en historial_estados y se notifica al
     * autor del reporte.
     */
    fun cambiarEstado(
        reporte: Reporte,
        nuevoEstado: EstadoReporte,
        observacion: String,
        contexto: Context? = null
    ) {
        val admin = _administrador.value
        if (admin == null || !admin.rol.puedeModerar) {
            _operacion.value = Recurso.Error("No tienes permisos de administrador")
            return
        }

        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            val resultado = reportesService.cambiarEstado(
                reporte = reporte,
                nuevoEstado = nuevoEstado,
                uidUsuario = admin.id,
                nombreUsuario = admin.nombre,
                observacion = observacion.trim(),
                contexto = contexto
            )

            _operacion.value = when (resultado) {
                is Recurso.Exito -> Recurso.Exito("Estado actualizado correctamente")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }

            if (resultado is Recurso.Exito) cargar()
        }
    }

    /** eliminar() del Administrador: borrado logico con traza. */
    fun eliminar(reporte: Reporte) {
        val admin = _administrador.value ?: return

        viewModelScope.launch {
            val resultado = reportesService.eliminar(
                reporte = reporte,
                uidUsuario = admin.id,
                nombreUsuario = admin.nombre
            )

            _operacion.value = when (resultado) {
                is Recurso.Exito -> Recurso.Exito("Reporte eliminado")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }

            if (resultado is Recurso.Exito) cargar()
        }
    }

    companion object {
        /** El administrador necesita ver mas reportes que el listado publico. */
        private const val LIMITE_ADMIN = 100L
    }
}