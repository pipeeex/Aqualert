package com.aqualer.app.ui.reportes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.data.service.AuthService
import com.aqualer.app.data.service.ReportesService
import com.aqualer.app.util.Recurso
import kotlinx.coroutines.launch

/**
 * ViewModel del Modulo de Reportes (ReportUI del diagrama de componentes).
 *
 * Expone las cuatro operaciones del CRUD que definen la clase Reporte:
 * crear(), actualizar(), eliminar() y la consulta del listado.
 */
class ReportesViewModel(
    private val reportesService: ReportesService = ReportesService(),
    private val authService: AuthService = AuthService()
) : ViewModel() {

    /** Listado de reportes (UC3: Consultar reportes). */
    private val _listado = MutableLiveData<Recurso<List<Reporte>>>()
    val listado: LiveData<Recurso<List<Reporte>>> = _listado

    /** Reporte cargado para edicion (UC4: Ver detalle). */
    private val _reporte = MutableLiveData<Recurso<Reporte>>()
    val reporte: LiveData<Recurso<Reporte>> = _reporte

    /** Resultado de guardar, actualizar o eliminar. */
    private val _operacion = MutableLiveData<Recurso<String>>()
    val operacion: LiveData<Recurso<String>> = _operacion

    /** Usuario autenticado, necesario para registrar la autoria. */
    private val _usuario = MutableLiveData<Usuario>()
    val usuario: LiveData<Usuario> = _usuario

    init {
        cargarUsuario()
    }

    private fun cargarUsuario() {
        viewModelScope.launch {
            _usuario.value = authService.obtenerUsuarioActual()
        }
    }

    // ===================== READ =====================

    /** Carga todos los reportes visibles. */
    fun cargarReportes() {
        _listado.value = Recurso.Cargando
        viewModelScope.launch {
            _listado.value = reportesService.listar()
        }
    }

    /** Carga solo los reportes del usuario autenticado. */
    fun cargarMisReportes() {
        val uid = authService.uidActual()
        if (uid == null) {
            _listado.value = Recurso.Error("Debes iniciar sesion para ver tus reportes")
            return
        }
        _listado.value = Recurso.Cargando
        viewModelScope.launch {
            _listado.value = reportesService.listarPorUsuario(uid)
        }
    }

    /** Carga un reporte puntual para mostrarlo en el formulario. */
    fun cargarReporte(id: String) {
        _reporte.value = Recurso.Cargando
        viewModelScope.launch {
            _reporte.value = reportesService.obtener(id)
        }
    }

    // ===================== CREATE =====================

    /** crear(): guarda un reporte nuevo asociado al usuario actual. */
    fun crearReporte(reporte: Reporte) {
        val actual = _usuario.value
        if (actual == null || !actual.rol.puedeCrearReportes) {
            _operacion.value = Recurso.Error("Debes iniciar sesion para crear un reporte")
            return
        }

        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            val conAutor = reporte.copy(
                uidUsuario = actual.id,
                nombreUsuario = actual.nombre
            )
            _operacion.value = when (val resultado = reportesService.crear(conAutor)) {
                is Recurso.Exito -> Recurso.Exito("Reporte creado correctamente")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }
        }
    }

    // ===================== UPDATE =====================

    /** actualizar(): guarda los cambios de un reporte existente. */
    fun actualizarReporte(reporte: Reporte) {
        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            _operacion.value = when (val resultado = reportesService.actualizar(reporte)) {
                is Recurso.Exito -> Recurso.Exito("Reporte actualizado correctamente")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }
        }
    }

    // ===================== DELETE =====================

    /** eliminar(): borrado logico, el reporte pasa al estado ELIMINADO. */
    fun eliminarReporte(reporte: Reporte) {
        val actual = _usuario.value

        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            val resultado = reportesService.eliminar(
                reporte = reporte,
                uidUsuario = actual?.id.orEmpty(),
                nombreUsuario = actual?.nombre.orEmpty()
            )
            _operacion.value = when (resultado) {
                is Recurso.Exito -> Recurso.Exito("Reporte eliminado")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }
        }
    }

    /** Comprueba si el usuario actual puede editar o borrar el reporte. */
    fun puedeEditar(reporte: Reporte): Boolean {
        val actual = _usuario.value ?: return false
        return actual.id == reporte.uidUsuario || actual.rol.puedeModerar
    }
}