package me.ahoo.cosky.core.util;

import lombok.var;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
class SystemsTest {

    @Test
    public void getCurrentProcessName() {
        var currentProcessName = Systems.getCurrentProcessName();
        Assertions.assertNotNull(currentProcessName);
    }

    @Test
    public void getCurrentProcessId() {
        var processId = Systems.getCurrentProcessId();
        Assertions.assertTrue(processId > 0);
    }
}
