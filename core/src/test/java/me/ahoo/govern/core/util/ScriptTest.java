package me.ahoo.govern.core.util;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import me.ahoo.govern.core.TestRedisClient;
import org.junit.jupiter.api.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ScriptTest {
    private RedisClient redisClient;
    private RedisCommands<String, String> redisCommands;

    @BeforeAll
    private void init() {
        redisClient = TestRedisClient.createClient();
        redisCommands = redisClient.connect().sync();
    }

    @Test
    void testReturnBoolean() {
        Boolean negativeOne = redisCommands.eval("return -1;", ScriptOutputType.BOOLEAN);
        Assertions.assertFalse(negativeOne);
        Boolean zero = redisCommands.eval("return 0;", ScriptOutputType.BOOLEAN);
        Assertions.assertFalse(zero);
        Boolean one = redisCommands.eval("return 1;", ScriptOutputType.BOOLEAN);
        Assertions.assertTrue(one);
        Boolean two = redisCommands.eval("return 2;", ScriptOutputType.BOOLEAN);
        Assertions.assertFalse(two);

        Boolean fa = redisCommands.eval("return false;", ScriptOutputType.BOOLEAN);
        Assertions.assertFalse(fa);
        Boolean tr = redisCommands.eval("return true;", ScriptOutputType.BOOLEAN);
        Assertions.assertTrue(tr);
    }

    @Test
    void testReturnLong() {
        Long negativeOne = redisCommands.eval("return -1;", ScriptOutputType.INTEGER);
        Assertions.assertEquals(-1, negativeOne);
        Long zero = redisCommands.eval("return 0;", ScriptOutputType.INTEGER);
        Assertions.assertEquals(0, zero);
        Long one = redisCommands.eval("return 1;", ScriptOutputType.INTEGER);
        Assertions.assertEquals(1, one);
        Long two = redisCommands.eval("return 2;", ScriptOutputType.INTEGER);
        Assertions.assertEquals(2, two);
    }

    @Test
    void testReturnStatus() {
        Object status = redisCommands.eval("return 'ok';", ScriptOutputType.STATUS);

        Assertions.assertEquals("ok", status);
    }

    @Test
    void testReturnValue() {
        Object val = redisCommands.eval("return 'ok';", ScriptOutputType.VALUE);

        Assertions.assertEquals("ok", val);
    }

    @AfterAll
    private void destroy() {
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
