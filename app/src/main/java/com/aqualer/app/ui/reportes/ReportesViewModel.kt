package com.aqualer.app.ui.reportes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.data.service.AuthService
import com.aqualer.app.data.service.ReportesService
import com.aqualer.app.util.Recurso
import kotlinx.coroutines.launch
import android.net.Uri
import com.aqualer.app.data.service.StorageService
import com.aqualer.app.data.model.ResultadoValidacion

/**
 * Expone las cuatro operaciones del CRUD que definen la clase Reporte:
 * crear(), actualizar(), eliminar() y la consulta del listado.
 */
class ReportesViewModel(
    private val reportesService: ReportesService = ReportesService(),
    private val authService: AuthService = AuthService(),
    private val storageService: StorageService = StorageService()
) : ViewModel() {

    /** Listado de reporte. */
    private val _listado = MutableLiveData<Recurso<List<Reporte>>>()
    val listado: LiveData<Recurso<List<Reporte>>> = _listado

    /** Reporte cargado para edicion. */
    private val _reporte = MutableLiveData<Recurso<Reporte>>()
    val reporte: LiveData<Recurso<Reporte>> = _reporte

    /** Resultado de guardar, actualizar o eliminar. */
    private val _operacion = MutableLiveData<Recurso<String>>()
    val operacion: LiveData<Recurso<String>> = _operacion

    /** Usuario autenticado, necesario para registrar la autoria. */
    private val _usuario = MutableLiveData<Usuario>()
    val usuario: LiveData<Usuario> = _usuario

    /** Etapa en curso, para informar que esta pasando durante la espera. */
    private val _etapa = MutableLiveData<String?>()
    val etapa: LiveData<String?> = _etapa

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
    fun crearReporte(reporte: Reporte, uriImagen: Uri?, contexto: Context) {
        val actual = _usuario.value
        if (actual == null || !actual.rol.puedeCrearReportes) {
            _operacion.value = Recurso.Error("Debes iniciar sesion para crear un reporte")
            return
        }

        // 1. Texto y ubicacion
        val validacion = reporte.validar(incluirImagen = false)
        if (validacion is ResultadoValidacion.Invalido) {
            _operacion.value = Recurso.Error(validacion.primerError)
            return
        }

        // 2. Evidencia
        if (uriImagen == null) {
            _operacion.value = Recurso.Error("Debes adjuntar una foto de evidencia")
            return
        }

        // 3. Ya con todo correcto, se sube
        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            _etapa.value = "subiendo"

            val url = when (val subida = storageService.subirImagen(contexto, uriImagen)) {
                is Recurso.Exito -> subida.datos
                is Recurso.Error -> {
                    _etapa.value = null
                    _operacion.value = subida
                    return@launch
                }
                else -> return@launch
            }

            _etapa.value = "analizando"

            val completo = reporte.copy(
                uidUsuario = actual.id,
                nombreUsuario = actual.nombre,
                imagenUrl = url
            )

            _operacion.value = when (val r = reportesService.crear(completo, contexto)) {
                is Recurso.Exito -> Recurso.Exito("Reporte analizado y publicado correctamente")
                is Recurso.Error -> r
                else -> Recurso.Cargando
            }
            _etapa.value = null
        }
    }


    /** editarYReintentar(): reenvia a clasificacion un reporte rechazado. */
    fun reintentarClasificacion(reporte: Reporte, contexto: Context? = null) {
        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            _operacion.value = when (
                val resultado = reportesService.reintentarClasificacion(reporte, contexto)
            ) {
                is Recurso.Exito -> Recurso.Exito("Reporte reenviado y publicado")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }
        }
    }

    // ===================== UPDATE =====================

    /** actualizar(): guarda los cambios de un reporte existente. */
    fun actualizarReporte(reporte: Reporte, uriImagen: Uri?, contexto: Context) {

        val validacion = reporte.validar(incluirImagen = false)
        if (validacion is ResultadoValidacion.Invalido) {
            _operacion.value = Recurso.Error(validacion.primerError)
            return
        }

        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            var url = reporte.imagenUrl
            if (uriImagen != null) {
                _etapa.value = "subiendo"
                when (val subida = storageService.subirImagen(contexto, uriImagen)) {
                    is Recurso.Exito -> url = subida.datos
                    is Recurso.Error -> {
                        _etapa.value = null
                        _operacion.value = subida
                        return@launch
                    }
                    else -> return@launch
                }
            }

            _etapa.value = null

            _operacion.value = when (
                val r = reportesService.actualizar(reporte.copy(imagenUrl = url))
            ) {
                is Recurso.Exito -> Recurso.Exito("Reporte actualizado correctamente")
                is Recurso.Error -> r
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