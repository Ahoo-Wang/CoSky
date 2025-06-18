package me.ahoo.cosky.spring.cloud.support

import io.mockk.every
import io.mockk.mockk
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment

internal class AppSupportTest {

    @Test
    fun getAppName() {
        val environment = mockk<Environment>()
        every { environment.getProperty("spring.application.name") } returns "appName"
        AppSupport.getAppName(environment).assert().isEqualTo("appName")
    }
}
