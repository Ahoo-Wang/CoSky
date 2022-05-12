package me.ahoo.cosky.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * ClearRedisScriptsTest .
 *
 * @author ahoo wang
 */
public class ClearRedisScriptsTest extends AbstractReactiveRedisTest {
    
    @BeforeEach
    @Override
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
    }
    
    @AfterEach
    @Override
    public void destroy() {
        super.destroy();
    }
    

    @Test
    public void clear() {
        ClearRedisScripts.clear(redisTemplate, "").block();
    }
}
