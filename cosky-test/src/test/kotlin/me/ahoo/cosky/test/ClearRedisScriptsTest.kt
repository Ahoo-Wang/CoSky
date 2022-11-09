package me.ahoo.cosky.test

import me.ahoo.cosky.test.ClearRedisScripts.clear
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ClearRedisScriptsTest .
 *
 * @author ahoo wang
 */
class ClearRedisScriptsTest : AbstractReactiveRedisTest() {
    @BeforeEach
    override fun afterPropertiesSet() {
        super.afterPropertiesSet()
    }

    @AfterEach
    override fun destroy() {
        super.destroy()
    }

    @Test
    fun clear() {
        clear(redisTemplate, "").block()
    }
}
