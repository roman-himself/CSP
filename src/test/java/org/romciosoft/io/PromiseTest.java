package org.romciosoft.io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;

public class PromiseTest {
    private ScheduledExecutorService scheduledExecutorService;
    private IOActionExecutor ioActionExecutor;

    @Before
    public void init() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        ioActionExecutor = new IOActionExecutor(scheduledExecutorService);
    }

    @After
    public void close() {
        scheduledExecutorService.shutdown();
    }

    @Test
    public void promiseCallback() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        IOAction<Void> action = Promise.<Integer>newPromise(ioActionExecutor)
                .bind(pro -> pro.addCallback(x -> () -> {
                    future.complete(x + 2);
                    return null;
                }).then(pro.deliver(40)));
        action.perform();
        assertEquals((Integer) 42, future.get());
    }

    @Test(expected = IllegalStateException.class)
    public void alreadyCompletedException() throws Exception {
        Promise.newPromise(ioActionExecutor)
                .bind(pro -> pro.deliver(null).then(pro.deliver(null))).perform();
    }

    @Test
    public void failedTryDeliver() throws Exception {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Promise.newPromise(ioActionExecutor, 42).bind(pro -> pro.tryDeliver(42).bind(res -> (IOAction<Void>) () -> {
            result.complete(res);
            return null;
        })).perform();
        assertEquals(false, result.get());
    }
}
