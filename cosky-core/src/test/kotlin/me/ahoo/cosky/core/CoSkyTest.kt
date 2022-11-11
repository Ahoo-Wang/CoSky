package me.ahoo.cosky.core

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test

internal class CoSkyTest {
    @Test
    fun getKeySeparator() {
        assertThat(CoSky.KEY_SEPARATOR, equalTo(":"))
    }

    @Test
    fun getCoSky() {
        assertThat(CoSky.COSKY, equalTo("cosky"))
    }
}
