package com.animehub.tv.data.iptv

import com.animehub.tv.domain.model.Channel
import com.animehub.tv.domain.model.ChannelVariant
import com.animehub.tv.domain.model.Iptv
import com.animehub.tv.domain.model.IptvList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/** Lectura de los canales en vivo de la API del servidor (`get_live_categories` + `get_live_streams`). Sin red ni contexto. */
object LiveApiParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Category(val id: String, val name: String)

    fun parseCategories(text: String): List<Category> =
        json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = o.str("category_name")?.trim().orEmpty()
            // La categoría "land" (portada, sin nombre) y las sin nombre no son estantes.
            if (name.isEmpty() || o.str("type") == "land") null else o.str("category_id")?.let { Category(it, name) }
        }

    /**
     * Canales de la API: la dirección es `url` o `direct_source` (solo si el servidor la entrega y es http/https);
     * sin ella el canal se muestra igual, con la dirección vacía (queda "sin señal").
     */
    fun parseLive(id: String, name: String, categoriesText: String, streamsText: String): IptvList {
        val categories = parseCategories(categoriesText)
        val names = categories.associate { it.id to it.name }
        val channels = (json.parseToJsonElement(streamsText) as JsonArray).mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val sid = o.str("stream_id") ?: return@mapNotNull null
            val title = o.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val url = listOf("url", "direct_source").firstNotNullOfOrNull { k -> o.str(k)?.takeIf { it.startsWith("http://") || it.startsWith("https://") } }.orEmpty()
            Channel(
                key = "$id:$sid", name = title, logo = o.str("stream_icon")?.takeIf { it.startsWith("http") },
                group = names[o.str("category_id")] ?: Iptv.OTHERS,
                variants = listOf(ChannelVariant(url, 0)),
            )
        }
        return IptvList(id, name, channels, false, categories.map { it.name })
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
}
