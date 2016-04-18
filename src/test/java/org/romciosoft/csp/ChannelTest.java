package org.romciosoft.csp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.romciosoft.io.AsyncAction;
import org.romciosoft.io.IOActionExecutor;

import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;

public class ChannelTest {
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
    public void channelPassing() throws Exception {
        ChannelHandle<Integer> chHandle = new UnbufferedChannel<Integer>().getHandle();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AsyncAction.fork(chHandle.getSendPort().send(40)).then(
                chHandle.getReceivePort().receive()
                        .bind(x -> AsyncAction.wrap(() -> {
                            future.complete(x + 2);
                            return null;
                        }))).getIOAction(ioActionExecutor).perform();
        assertEquals((Integer) 42, future.get());
    }

    @Test
    public void receiveThenSend() throws Exception {
        ChannelHandle<Integer> chHandle = new UnbufferedChannel<Integer>().getHandle();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AsyncAction.fork(chHandle.getReceivePort().receive().bind(x ->
                AsyncAction.wrap(() -> {
                    future.complete(x + 2);
                    return null;
                })))
                .then(AsyncAction.delay(500, TimeUnit.MILLISECONDS))
                .then(chHandle.getSendPort().send(40))
                .getIOAction(ioActionExecutor).perform();
        assertEquals((Integer) 42, future.get());
    }

    @Test(expected = TimeoutException.class)
    public void channelLock() throws Exception {
        ChannelHandle<Integer> chHandle = new UnbufferedChannel<Integer>().getHandle();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        chHandle.getSendPort().send(40)
                .then(chHandle.getReceivePort().receive()
                        .bind(x -> AsyncAction.wrap(() -> {
                            future.complete(x + 2);
                            return null;
                        }))).getIOAction(ioActionExecutor).perform();
        future.get(500, TimeUnit.MILLISECONDS);
    }
}
