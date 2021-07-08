/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.core.util;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import me.ahoo.cosky.core.TestRedisClient;
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
