package com.aqualer.app.ui.chatbot

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aqualer.app.data.model.MensajeChat
import com.aqualer.app.databinding.ItemMensajeBotBinding
import com.aqualer.app.databinding.ItemMensajeUsuarioBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adaptador de la conversacion con el asistente.
 *
 * Usa dos tipos de vista segun quien emitio el mensaje: la burbuja
 * del usuario se alinea a la derecha con el color de acento, y la del
 * asistente a la izquierda junto al logo de Aqualert.
 */
class MensajesAdapter :
    ListAdapter<MensajeChat, RecyclerView.ViewHolder>(COMPARADOR) {

    private val formatoHora = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).esDelUsuario) TIPO_USUARIO else TIPO_BOT

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val inflador = LayoutInflater.from(parent.context)

        return if (viewType == TIPO_USUARIO) {
            UsuarioViewHolder(ItemMensajeUsuarioBinding.inflate(inflador, parent, false))
        } else {
            BotViewHolder(ItemMensajeBotBinding.inflate(inflador, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensaje = getItem(position)
        when (holder) {
            is UsuarioViewHolder -> holder.enlazar(mensaje)
            is BotViewHolder -> holder.enlazar(mensaje)
        }
    }

    inner class UsuarioViewHolder(
        private val binding: ItemMensajeUsuarioBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun enlazar(mensaje: MensajeChat) {
            binding.txtMensaje.text = mensaje.contenido
            binding.txtHora.text = formatoHora.format(mensaje.fecha)
        }
    }

    inner class BotViewHolder(
        private val binding: ItemMensajeBotBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun enlazar(mensaje: MensajeChat) {
            binding.txtMensaje.text = mensaje.contenido
            binding.txtHora.text = formatoHora.format(mensaje.fecha)
        }
    }

    companion object {
        private const val TIPO_USUARIO = 1
        private const val TIPO_BOT = 2

        private val COMPARADOR = object : DiffUtil.ItemCallback<MensajeChat>() {
            override fun areItemsTheSame(anterior: MensajeChat, nuevo: MensajeChat): Boolean =
                anterior.id == nuevo.id &&
                        anterior.fecha == nuevo.fecha &&
                        anterior.contenido == nuevo.contenido

            override fun areContentsTheSame(anterior: MensajeChat, nuevo: MensajeChat): Boolean =
                anterior == nuevo
        }
    }
}