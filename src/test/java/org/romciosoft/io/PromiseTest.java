package org.romciosoft.io;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
}
