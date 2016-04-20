package org.romciosoft.csp.examples;

import org.romciosoft.csp.CSP;
import org.romciosoft.csp.ChannelHandle;
import org.romciosoft.io.AsyncAction;
import org.romciosoft.io.IOAction;
import org.romciosoft.io.IOActionExecutor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class PingPong {
    static class PingMessage {
        static enum Type {
            PING, FINISHED
        }

        Type type;
        UUID id;

        private PingMessage(Type type, UUID id) {
            this.type = type;
            this.id = id;
        }

        static PingMessage ping() {
            return new PingMessage(Type.PING, UUID.randomUUID());
        }

        static PingMessage finished() {
            return new PingMessage(Type.FINISHED, null);
        }
    }

    static class PongMessage {
        UUID id;

        PongMessage(UUID id) {
            this.id = id;
        }
    }

    private static AsyncAction<Void> pingBody(ChannelHandle.SendPort<PingMessage> pingOut,
                                              ChannelHandle.ReceivePort<PongMessage> pongIn,
                                              int howMany) {
        if (howMany == 0) {
            return pingOut.send(PingMessage.finished());
        }
        PingMessage ping = PingMessage.ping();
        return AsyncAction.wrap(() -> {
            System.out.println("sending ping: " + ping.id);
            return null;
        })
                .then(pingOut.send(ping))
                .then(pongIn.receive()
                        .bind(pong -> AsyncAction.wrap(() -> {
                            System.out.println("received pong: " + pong.id);
                            return null;
                        })))
                .then(pingBody(pingOut, pongIn, howMany - 1));
    }

    private static AsyncAction<Void> pongBody(ChannelHandle.ReceivePort<PingMessage> pingIn,
                                              ChannelHandle.SendPort<PongMessage> pongOut,
                                              CompletableFuture<Void> complete) {
        return pingIn.receive().bind(ping -> {
            if (ping.type == PingMessage.Type.FINISHED) {
                return AsyncAction.wrap((IOAction<Void>) () -> {
                    System.out.println("received finish signal");
                    complete.complete(null);
                    return null;
                });
            }
            return AsyncAction.wrap(() -> {
                System.out.println("received ping: " + ping.id + ", sending pong");
                return null;
            })
                    .then(pongOut.send(new PongMessage(ping.id)))
                    .then(pongBody(pingIn, pongOut, complete));
        });
    }

    private static AsyncAction<Void> pingPongBody(int howMany, CompletableFuture<Void> complete) {
        return CSP.<PingMessage>newChannel().bind(
                pingChannel -> CSP.<PongMessage>newChannel().bind(
                        pongChannel ->
                                AsyncAction.fork(pingBody(pingChannel.getSendPort(), pongChannel.getReceivePort(), howMany))
                                        .then(AsyncAction.fork(pongBody(pingChannel.getReceivePort(), pongChannel.getSendPort(), complete)))));
    }

    public static void main(String[] args) throws Exception {
        CompletableFuture<Void> complete = new CompletableFuture<>();
        ScheduledExecutorService schSvc = Executors.newScheduledThreadPool(10);
        pingPongBody(10, complete).getIOAction(new IOActionExecutor(schSvc)).perform();
        complete.get();
        schSvc.shutdown();
    }
}
