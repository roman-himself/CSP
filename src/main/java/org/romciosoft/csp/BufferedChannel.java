package org.romciosoft.csp;

import org.romciosoft.data.IPersistentQueue;
import org.romciosoft.data.PersistentQueue;
import org.romciosoft.io.AsyncAction;
import org.romciosoft.io.Promise;

class BufferedChannel {
    private static class BufferProcessContext<T> {
        private int bufferSize, bufferLimit;
        private IPersistentQueue<T> buffer;
        private ChannelHandle.ReceivePort<T> from;
        private ChannelHandle.SendPort<T> to;

        BufferProcessContext(ChannelHandle.ReceivePort<T> from, ChannelHandle.SendPort<T> to, int bufferLimit) {
            this.from = from;
            this.to = to;
            this.bufferLimit = bufferLimit;
            bufferSize = 0;
            buffer = PersistentQueue.empty();
        }

        private BufferProcessContext(BufferProcessContext<T> ctx) {
            bufferSize = ctx.bufferSize;
            bufferLimit = ctx.bufferLimit;
            buffer = ctx.buffer;
            from = ctx.from;
            to = ctx.to;
        }

        boolean isFull() {
            return bufferSize == bufferLimit;
        }

        boolean isEmpty() {
            return bufferSize == 0;
        }

        T peek() {
            return buffer.peek();
        }

        BufferProcessContext<T> offer(T val) {
            BufferProcessContext<T> ctx = new BufferProcessContext<>(this);
            ctx.buffer = buffer.offer(val);
            ctx.bufferSize = bufferSize + 1;
            return ctx;
        }

        BufferProcessContext<T> poll() {
            BufferProcessContext<T> ctx = new BufferProcessContext<>(this);
            ctx.buffer = buffer.poll();
            ctx.bufferSize = bufferSize - 1;
            return ctx;
        }
    }

    private static <T> AsyncAction<Void> bufferProcessBody(BufferProcessContext<T> ctx) {
        SelectBuilder2<Void> select = CSP.select();
        if (!ctx.isFull()) {
            select.receive(ctx.from, rcved -> bufferProcessBody(ctx.offer(rcved)));
        }
        if (!ctx.isEmpty()) {
            select.send(ctx.to, ctx.peek(), () -> bufferProcessBody(ctx.poll()));
        }
        return select.build();
    }

    static <T> AsyncAction<ChannelHandle<T>> bufferedChannel(int bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("buffer size must be at least one");
        }
        return exe -> () -> {
            Channel<T> chIn = new UnbufferedChannel<>();
            Channel<T> chOut = new UnbufferedChannel<>();
            BufferProcessContext<T> ctx = new BufferProcessContext<>(
                    chIn.getHandle().getReceivePort(),
                    chOut.getHandle().getSendPort(),
                    bufferSize);
            AsyncAction.fork(
                    bufferProcessBody(ctx)
            ).getIOAction(exe).perform();
            return Promise.newPromise(exe,
                    new ChannelHandle<>(
                            chIn.getHandle().getSendPort(),
                            chOut.getHandle().getReceivePort()))
                    .perform();
        };
    }
}
