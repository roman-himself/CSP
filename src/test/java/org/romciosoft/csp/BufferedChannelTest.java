package org.romciosoft.csp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import org.romciosoft.io.AsyncAction;
import org.romciosoft.io.IOActionExecutor;

import java.util.concurrent.*;

public class BufferedChannelTest {
    private ScheduledExecutorService scheduledExecutorService;
    private IOActionExecutor ioActionExecutor;

    @Before
    public void init() {
        scheduledExecutorService = Executors.newScheduledThreadPool(10);
        ioActionExecutor = new IOActionExecutor(scheduledExecutorService);
    }

    @After
    public void close() {
        scheduledExecutorService.shutdown();
    }

    @Test
    public void size1Buffer() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        CSP.<Integer>newChannel(1).bind(ch ->
                ch.getSendPort().send(40)
                        .then(ch.getReceivePort().receive()
                                .bind(x -> AsyncAction.wrap(() -> {
                                    future.complete(x + 2);
                                    return null;
                                }))))
                .getIOAction(ioActionExecutor).perform();
        assertEquals((Integer) 42, future.get());
    }

    @Test(expected = TimeoutException.class)
    public void size1BufferLock() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        CSP.<Integer>newChannel(1).bind(ch ->
                ch.getSendPort().send(40)
                        .then(ch.getSendPort().send(2))
                        .then(AsyncAction.wrap(() -> {
                            future.complete(42);
                            return null;
                        })))
                .getIOAction(ioActionExecutor).perform();
        future.get(500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void size2Buffer() throws Exception {
        CompletableFuture<String> future1 = new CompletableFuture<>();
        CompletableFuture<String> future2 = new CompletableFuture<>();
        CSP.<String>newChannel(2).bind(ch ->
                ch.getSendPort().send("foo")
                        .then(ch.getSendPort().send("bar"))
                        .then(ch.getReceivePort().receive()
                                .bind(x -> AsyncAction.wrap(() -> {
                                    future1.complete(x);
                                    return null;
                                })))
                        .then(ch.getReceivePort().receive()
                                .bind(x -> AsyncAction.wrap(() -> {
                                    future2.complete(x);
                                    return null;
                                }))))
                .getIOAction(ioActionExecutor).perform();
        assertEquals("foo", future1.get());
        assertEquals("bar", future2.get());
    }
}
