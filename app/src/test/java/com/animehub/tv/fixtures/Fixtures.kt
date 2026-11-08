package com.animehub.tv.fixtures

/** Lee los HTML guardados en src/test/resources/fixtures. */
object Fixtures {
    fun read(path: String): String {
        val stream = Fixtures::class.java.getResourceAsStream("/fixtures/$path")
            ?: error("No existe el fixture $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
