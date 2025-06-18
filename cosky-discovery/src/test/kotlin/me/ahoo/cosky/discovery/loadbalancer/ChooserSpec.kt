package me.ahoo.cosky.discovery.loadbalancer

import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstance.Companion.withWeight
import me.ahoo.cosky.discovery.TestServiceInstance
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.util.StopWatch
import java.util.concurrent.TimeUnit

abstract class ChooserSpec {
    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ChooserSpec::class.java)
    }

    abstract fun createChooser(instances: List<ServiceInstance>): LoadBalancer.Chooser

    @Test
    fun choose() {
        val serviceId = "ChooserSpec"
        val totalTimes = 1000_000_0
        val expected = 0.99
        val totalWeight = 10
        val instance1Weight = 2
        val instance2Weight = 3
        val instance3Weight = 5
        val instance1 = TestServiceInstance.createInstance(serviceId).withWeight(weight = instance1Weight)
        val instance2 = TestServiceInstance.createInstance(serviceId).withWeight(weight = instance2Weight)
        val instance3 = TestServiceInstance.createInstance(serviceId).withWeight(weight = instance3Weight)
        val instance1ExpectedHits = totalTimes * instance1Weight / totalWeight
        val instance2ExpectedHits = totalTimes * instance2Weight / totalWeight
        val instance3ExpectedHits = totalTimes * instance3Weight / totalWeight
        val instances = listOf(
            instance1,
            instance2,
            instance3,
        )
        val chooser = createChooser(instances)
        val instance = chooser.choose()
        instance.assert().isNotNull()

        var instance1Hits = 0
        var instance2Hits = 0
        var instance3Hits = 0
        StopWatch().apply {
            start()
            repeat(totalTimes) {
                when (chooser.choose()) {
                    instance1 -> {
                        instance1Hits++
                    }

                    instance2 -> {
                        instance2Hits++
                    }

                    else -> {
                        instance3Hits++
                    }
                }
            }
            stop()
            val instance1HitRate = instance1Hits * 1.0 / instance1ExpectedHits
            val instance2HitRate = instance2Hits * 1.0 / instance2ExpectedHits
            val instance3HitRate = instance3Hits * 1.0 / instance3ExpectedHits
            instance1HitRate.assert().isGreaterThan(expected)
            instance2HitRate.assert().isGreaterThan(expected)
            instance3HitRate.assert().isGreaterThan(expected)
            log.info(
                "totalTimes: {},instance1Count: {},instance2Count: {},instance3Count: {},time: {}ms",
                totalTimes,
                instance1Hits,
                instance2Hits,
                instance3Hits,
                TimeUnit.MILLISECONDS.convert(totalTimeMillis, TimeUnit.NANOSECONDS),
            )
        }
    }
}
