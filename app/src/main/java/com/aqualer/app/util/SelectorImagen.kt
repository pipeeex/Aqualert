package com.aqualer.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aqualer.app.R
import java.io.File

/**
 * Selector de evidencia fotografica (UC6: "Tomar o seleccionar foto").
 *
 * Encapsula los tres contratos que exige Android para esto:
 *   - Pedir el permiso de camara en tiempo de ejecucion
 *   - Lanzar la camara escribiendo en una Uri propia de la aplicacion
 *   - Abrir el selector de fotos del sistema
 *
 * La galeria usa PickVisualMedia, el selector moderno de Android: no
 * requiere permiso de almacenamiento porque el sistema entrega solo
 * la imagen elegida, sin dar acceso al resto de la galeria.
 */
class SelectorImagen(
    private val actividad: AppCompatActivity,
    private val alElegir: (Uri) -> Unit
) {

    private lateinit var lanzadorCamara: ActivityResultLauncher<Uri>
    private lateinit var lanzadorGaleria: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var lanzadorPermiso: ActivityResultLauncher<String>

    /** Destino de la foto que esta tomando la camara. */
    private var uriCaptura: Uri? = null

    /**
     * Registra los contratos. Debe llamarse en onCreate, antes de que
     * la Activity alcance el estado STARTED.
     */
    fun registrar() {
        lanzadorCamara = actividad.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { exitosa ->
            val uri = uriCaptura
            if (exitosa && uri != null) alElegir(uri)
        }

        lanzadorGaleria = actividad.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) alElegir(uri)
        }

        lanzadorPermiso = actividad.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedido ->
            if (concedido) abrirCamara()
            else explicarPermisoDenegado()
        }
    }

    /** Pregunta al usuario de donde quiere tomar la evidencia. */
    fun mostrarOpciones() {
        val opciones = arrayOf(
            actividad.getString(R.string.rep_tomar_foto),
            actividad.getString(R.string.rep_elegir_galeria)
        )

        AlertDialog.Builder(actividad)
            .setTitle(R.string.rep_foto_evidencia)
            .setItems(opciones) { _, indice ->
                if (indice == 0) solicitarCamara() else abrirGaleria()
            }
            .setNegativeButton(R.string.msg_cancelar, null)
            .show()
    }

    // ==========================================================
    //  Camara
    // ==========================================================

    private fun solicitarCamara() {
        val concedido = ContextCompat.checkSelfPermission(
            actividad, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (concedido) abrirCamara()
        else lanzadorPermiso.launch(Manifest.permission.CAMERA)
    }

    private fun abrirCamara() {
        val uri = crearUriTemporal(actividad)
        uriCaptura = uri
        lanzadorCamara.launch(uri)
    }

    /**
     * Crea el archivo donde la camara escribira la foto.
     *
     * Va en la cache de la aplicacion y se expone mediante FileProvider:
     * la camara recibe permiso solo sobre ese archivo, no sobre el
     * almacenamiento de la aplicacion.
     */
    private fun crearUriTemporal(contexto: Context): Uri {
        val carpeta = File(contexto.cacheDir, "fotos").apply { mkdirs() }
        val archivo = File(carpeta, "captura_${System.currentTimeMillis()}.jpg")

        return FileProvider.getUriForFile(
            contexto,
            "${contexto.packageName}.fileprovider",
            archivo
        )
    }

    private fun explicarPermisoDenegado() {
        AlertDialog.Builder(actividad)
            .setTitle(R.string.rep_foto_evidencia)
            .setMessage(R.string.msg_permiso_camara)
            .setPositiveButton(R.string.rep_elegir_galeria) { _, _ -> abrirGaleria() }
            .setNegativeButton(R.string.msg_cancelar, null)
            .show()
    }

    // ==========================================================
    //  Galeria
    // ==========================================================

    private fun abrirGaleria() {
        lanzadorGaleria.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}