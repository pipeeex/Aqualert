package com.aqualer.app.ui.reportes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqualer.app.data.model.Comentario
import com.aqualer.app.data.model.HistorialEstado
import com.aqualer.app.data.model.Reporte
import com.aqualer.app.data.model.Usuario
import com.aqualer.app.data.service.AuthService
import com.aqualer.app.data.service.ComentariosService
import com.aqualer.app.data.service.ReportesService
import com.aqualer.app.util.Recurso
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de un reporte.
 * Los comentarios se escuchan en tiempo real, de modo que si otra
 * persona comenta mientras la pantalla esta abierta, aparece solo.
 */
class DetalleReporteViewModel(
    private val reportesService: ReportesService = ReportesService(),
    private val comentariosService: ComentariosService = ComentariosService(),
    private val authService: AuthService = AuthService()
) : ViewModel() {

    private val _reporte = MutableLiveData<Recurso<Reporte>>()
    val reporte: LiveData<Recurso<Reporte>> = _reporte

    private val _comentarios = MutableLiveData<List<Comentario>>()
    val comentarios: LiveData<List<Comentario>> = _comentarios

    private val _historial = MutableLiveData<List<HistorialEstado>>()
    val historial: LiveData<List<HistorialEstado>> = _historial

    /** Resultado de publicar, ocultar o eliminar un comentario. */
    private val _operacion = MutableLiveData<Recurso<String>>()
    val operacion: LiveData<Recurso<String>> = _operacion

    private val _usuario = MutableLiveData<Usuario>()
    val usuario: LiveData<Usuario> = _usuario

    /** Listener en tiempo real; se cierra al destruir la pantalla. */
    private var escuchaComentarios: ListenerRegistration? = null

    init {
        viewModelScope.launch {
            _usuario.value = authService.obtenerUsuarioActual()
        }
    }

    // ==========================================================
    //  Carga
    // ==========================================================

    /** UC4: Ver detalle de reporte. */
    fun cargar(idReporte: String) {
        _reporte.value = Recurso.Cargando
        viewModelScope.launch {
            _reporte.value = reportesService.obtener(idReporte)
        }
        cargarHistorial(idReporte)
        escucharComentarios(idReporte)
    }

    /** Linea de tiempo de las transiciones del reporte. */
    private fun cargarHistorial(idReporte: String) {
        viewModelScope.launch {
            _historial.value = reportesService.listarHistorial(idReporte)
                .datosONull()
                .orEmpty()
        }
    }

    /** Escucha los comentarios publicados del reporte. */
    private fun escucharComentarios(idReporte: String) {
        escuchaComentarios?.remove()
        escuchaComentarios = comentariosService.observarPorReporte(idReporte) { lista ->
            _comentarios.postValue(lista)
        }
    }

    // ==========================================================
    //  Comentarios
    // ==========================================================

    /**
     * Solo el Ciudadano registrado puede publicar comentarios.
     */
    fun publicarComentario(idReporte: String, contenido: String) {
        val actual = _usuario.value
        if (actual == null || !actual.rol.puedeComentar) {
            _operacion.value = Recurso.Error("Debes iniciar sesion para comentar")
            return
        }

        _operacion.value = Recurso.Cargando
        viewModelScope.launch {
            val comentario = Comentario(
                idReporte = idReporte,
                uidUsuario = actual.id,
                nombreUsuario = actual.nombre,
                fotoUsuario = actual.fotoPerfil,
                contenido = contenido
            )

            _operacion.value = when (
                val resultado = comentariosService.publicar(comentario)
            ) {
                is Recurso.Exito -> Recurso.Exito("Comentario publicado")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }
        }
    }

    /** eliminar(): disponible para el autor del comentario o el Administrador. */
    fun eliminarComentario(comentario: Comentario) {
        val actual = _usuario.value
        val autorizado = comentariosService.puedeEliminar(
            comentario = comentario,
            uidUsuario = actual?.id.orEmpty(),
            esAdministrador = actual?.rol?.puedeModerar == true
        )

        if (!autorizado) {
            _operacion.value = Recurso.Error("Solo puedes eliminar tus propios comentarios")
            return
        }

        viewModelScope.launch {
            _operacion.value = when (
                val resultado = comentariosService.eliminar(comentario)
            ) {
                is Recurso.Exito -> Recurso.Exito("Comentario eliminado")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }
        }
    }

    /** moderarContenido(): exclusivo del Administrador. */
    fun ocultarComentario(comentario: Comentario) {
        if (_usuario.value?.rol?.puedeModerar != true) {
            _operacion.value = Recurso.Error("Solo un administrador puede moderar")
            return
        }

        viewModelScope.launch {
            _operacion.value = when (
                val resultado = comentariosService.ocultar(comentario)
            ) {
                is Recurso.Exito -> Recurso.Exito("Comentario oculto")
                is Recurso.Error -> resultado
                else -> Recurso.Cargando
            }
        }
    }

    // ==========================================================
    //  Permisos de la pantalla
    // ==========================================================

    /** Indica si el usuario puede editar este reporte. */
    fun puedeEditar(reporte: Reporte): Boolean {
        val actual = _usuario.value ?: return false
        return actual.id == reporte.uidUsuario || actual.rol.puedeModerar
    }

    /** Indica si el usuario puede escribir comentarios. */
    fun puedeComentar(): Boolean = _usuario.value?.rol?.puedeComentar == true

    /** Indica si el usuario puede moderar los comentarios. */
    fun puedeModerar(): Boolean = _usuario.value?.rol?.puedeModerar == true

    /** Indica si el comentario pertenece al usuario actual. */
    fun esAutorDelComentario(comentario: Comentario): Boolean =
        _usuario.value?.id == comentario.uidUsuario

    override fun onCleared() {
        super.onCleared()
        // Evita dejar abierta la conexion de tiempo real
        escuchaComentarios?.remove()
        escuchaComentarios = null
    }
}