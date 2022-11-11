package me.ahoo.cosky.config

/**
 * ConfigData .
 *
 * @author ahoo wang
 */
object TestData {
    const val NAMESPACE = "ben_cfg"
    const val DATA = """
        spring:
          cloud:
            cosky:
              discovery:
                registry:
                  weight: 8
          web:
            resources:
              static-locations: file:./cosky-dashboard/dist/dashboard/
          webflux:
            static-path-pattern: /dashboard/**
        cosky:
          security:
            enabled: true
            audit-log:
              action: write
        cosid:
          namespace: cosky
          machine:
            enabled: true
            distributor:
              type: redis
          snowflake:
            enabled: true
            share:
              converter:
                type: radix
    """
}
