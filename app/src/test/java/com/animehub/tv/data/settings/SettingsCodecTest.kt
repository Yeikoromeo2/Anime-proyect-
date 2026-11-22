package com.animehub.tv.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun `los mapas se guardan y recuperan sin perder URLs con signos igual`() {
        val map = mapOf("latanime" to "https://latanime.org/?a=b", "animeninja" to "Voe")
        assertEquals(map, SettingsRepository.decodeMap(SettingsRepository.encodeMap(map)))
    }
}
