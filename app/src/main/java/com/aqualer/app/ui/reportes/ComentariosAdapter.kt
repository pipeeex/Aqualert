package com.aqualer.app.ui.reportes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqualer.app.data.model.Comentario
import com.aqualer.app.databinding.ItemComentarioBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 *
 * El menu de acciones de cada comentario depende del rol:
 *   - El autor puede eliminar el suyo
 *   - El Administrador puede ocultarlo o eliminarlo (moderarContenido)
 *   - Los demas usuarios solo lo leen
 */
class ComentariosAdapter(
    private val esAutor: (Comentario) -> Boolean,
    private val puedeModerar: () -> Boolean,
    private val alEliminar: (Comentario) -> Unit,
    private val alOcultar: (Comentario) -> Unit
) : ListAdapter<Comentario, ComentariosAdapter.ComentarioViewHolder>(COMPARADOR) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComentarioViewHolder {
        val binding = ItemComentarioBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ComentarioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComentarioViewHolder, position: Int) {
        holder.enlazar(getItem(position))
    }

    inner class ComentarioViewHolder(
        private val binding: ItemComentarioBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun enlazar(comentario: Comentario) {
            binding.txtIniciales.text = iniciales(comentario.nombreUsuario)
            binding.txtNombre.text = comentario.nombreUsuario.ifBlank { "Usuario" }
            binding.txtContenido.text = comentario.contenido
            binding.txtFecha.text = tiempoRelativo(comentario.fecha)

            val autor = esAutor(comentario)
            val moderador = puedeModerar()

            // Solo se ofrece el menu a quien puede hacer algo con el comentario
            binding.btnAcciones.visibility =
                if (autor || moderador) View.VISIBLE else View.GONE

            binding.btnAcciones.setOnClickListener { vista ->
                val menu = androidx.appcompat.widget.PopupMenu(vista.context, vista)

                if (moderador) {
                    menu.menu.add(0, OPCION_OCULTAR, 0, "Ocultar comentario")
                }
                if (autor || moderador) {
                    menu.menu.add(0, OPCION_ELIMINAR, 1, "Eliminar comentario")
                }

                menu.setOnMenuItemClickListener { opcion ->
                    when (opcion.itemId) {
                        OPCION_OCULTAR -> { alOcultar(comentario); true }
                        OPCION_ELIMINAR -> { alEliminar(comentario); true }
                        else -> false
                    }
                }
                menu.show()
            }
        }

        /** Iniciales del autor para el avatar cuando no tiene foto. */
        private fun iniciales(nombre: String): String =
            nombre.trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "?" }

        /**
         * Muestra "hace 5 min" en lugar de una fecha completa.
         * Para un hilo de conversacion la antiguedad relativa comunica
         * mejor que la marca de tiempo exacta.
         */
        private fun tiempoRelativo(fecha: Date): String {
            val diferencia = System.currentTimeMillis() - fecha.time
            val minutos = TimeUnit.MILLISECONDS.toMinutes(diferencia)
            val horas = TimeUnit.MILLISECONDS.toHours(diferencia)
            val dias = TimeUnit.MILLISECONDS.toDays(diferencia)

            return when {
                minutos < 1 -> "hace un momento"
                minutos < 60 -> "hace $minutos min"
                horas < 24 -> "hace $horas h"
                dias < 7 -> "hace $dias d"
                else -> SimpleDateFormat("dd MMM yyyy", Locale("es", "CO")).format(fecha)
            }
        }
    }

    companion object {
        private const val OPCION_OCULTAR = 1
        private const val OPCION_ELIMINAR = 2

        private val COMPARADOR = object : DiffUtil.ItemCallback<Comentario>() {
            override fun areItemsTheSame(anterior: Comentario, nuevo: Comentario): Boolean =
                anterior.id == nuevo.id

            override fun areContentsTheSame(anterior: Comentario, nuevo: Comentario): Boolean =
                anterior == nuevo
        }
    }
}