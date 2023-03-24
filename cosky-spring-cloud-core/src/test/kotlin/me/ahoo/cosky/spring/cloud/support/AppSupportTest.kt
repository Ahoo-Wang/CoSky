package me.ahoo.cosky.spring.cloud.support

import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment

internal class AppSupportTest {

    @Test
    fun getAppName() {
        val environment = mockk<Environment>()
        every { environment.getProperty("spring.application.name") } returns "appName"
        assertThat(AppSupport.getAppName(environment), equalTo("appName"))
    }
}
