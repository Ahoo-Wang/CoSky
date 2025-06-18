package me.ahoo.cosky.core

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

internal class CoSkyTest {
    @Test
    fun getKeySeparator() {
        CoSky.KEY_SEPARATOR.assert().isEqualTo(":")
    }

    @Test
    fun getCoSky() {
        CoSky.COSKY.assert().isEqualTo("cosky")
    }

    @Test
    fun getVersion() {
        CoSky.VERSION.assert().isEmpty()
    }
}
