package me.ahoo.cosky.core.util;

import me.ahoo.cosky.core.CoskyException;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.lang.Thread.currentThread;

/**
 * @author ahoo wang
 */
public final class Futures {
    private Futures() {
    }

    public static <T> T getUnChecked(Future<T> future, Duration timeout) {
        try {
            return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new CoskyException(e);
        } catch (TimeoutException e) {
            throw new CoskyException(e);
        } catch (ExecutionException e) {
            throw new CoskyException(e);
        }
    }
}
