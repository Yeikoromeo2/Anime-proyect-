package com.animehub.tv.util

/** Mezcla varias listas alternándolas (1º de cada una, 2º de cada una…), sin perder el orden de cada sitio. */
fun <T> interleave(lists: List<List<T>>): List<T> {
    val max = lists.maxOfOrNull { it.size } ?: 0
    return (0 until max).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
}
