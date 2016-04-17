package org.romciosoft.csp;

import org.romciosoft.data.IPersistentQueue;
import org.romciosoft.data.PersistentQueue;
import org.romciosoft.io.AsyncAction;

class BufferedChannel {
    private static class IOEvent<T> {
        Type type;
        T value;

        private IOEvent(Type type, T value) {
            this.type = type;
            this.value = value;
        }

        static <T> IOEvent<T> sent() {
            return new IOEvent<>(Type.SEND, null);
        }

        static <T> IOEvent<T> received(T value) {
            return new IOEvent<>(Type.RECEIVE, value);
        }

        enum Type {
            SEND, RECEIVE
        }
    }

    private static class BufferProcessContext<T> {
        private int bufferSize, bufferLimit;
        private IPersistentQueue<T> buffer;
        private boolean senderBusy, receiverBusy;
        private ChannelHandle.ReceivePort<IOEvent<T>> evtIn;
        private ChannelHandle.SendPort<T> senderOut;
        private ChannelHandle.SendPort<Void> receiverOut;

        BufferProcessContext(ChannelHandle.ReceivePort<IOEvent<T>> evtIn, ChannelHandle.SendPort<T> senderOut, ChannelHandle.SendPort<Void> receiverOut, int bufferLimit) {
            this.evtIn = evtIn;
            this.senderOut = senderOut;
            this.receiverOut = receiverOut;
            this.bufferLimit = bufferLimit;
            bufferSize = 0;
            senderBusy = false;
            receiverBusy = true;
            buffer = PersistentQueue.empty();
        }

        private BufferProcessContext(BufferProcessContext<T> ctx) {
            bufferSize = ctx.bufferSize;
            bufferLimit = ctx.bufferLimit;
            buffer = ctx.buffer;
            senderBusy = ctx.senderBusy;
            receiverBusy = ctx.receiverBusy;
            evtIn = ctx.evtIn;
            senderOut = ctx.senderOut;
            receiverOut = ctx.receiverOut;
        }

        ChannelHandle.ReceivePort<IOEvent<T>> evtIn() {
            return evtIn;
        }

        ChannelHandle.SendPort<T> senderOut() {
            return senderOut;
        }

        ChannelHandle.SendPort<Void> receiverOut() {
            return receiverOut;
        }

        boolean isUnbuffered() {
            return bufferLimit == 0;
        }

        boolean isNearFull() {
            return bufferSize == bufferLimit - 1;
        }

        boolean isEmpty() {
            return bufferSize == 0;
        }

        boolean senderBusy() {
            return senderBusy;
        }

        boolean receiverBusy() {
            return receiverBusy;
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

        BufferProcessContext<T> senderBusy(boolean busy) {
            BufferProcessContext<T> ctx = new BufferProcessContext<>(this);
            ctx.senderBusy = busy;
            return ctx;
        }

        BufferProcessContext<T> receiverBusy(boolean busy) {
            BufferProcessContext<T> ctx = new BufferProcessContext<>(this);
            ctx.receiverBusy = busy;
            return ctx;
        }
    }

    private static <T> AsyncAction<Void> inProcessBody(ChannelHandle.SendPort<IOEvent<T>> toMiddle, ChannelHandle.ReceivePort<Void> fromMiddle, ChannelHandle.ReceivePort<T> fromOutside) {
        return fromOutside.receive().bind(received ->
                toMiddle.send(IOEvent.received(received))
                        .then(fromMiddle.receive().then(inProcessBody(toMiddle, fromMiddle, fromOutside))));
    }

    private static <T> AsyncAction<Void> outProcessBody(ChannelHandle.SendPort<IOEvent<T>> toMiddle, ChannelHandle.ReceivePort<T> fromMiddle, ChannelHandle.SendPort<T> toOutside) {
        return fromMiddle.receive().bind(val -> toOutside.send(val).then(toMiddle.send(IOEvent.sent()).then(outProcessBody(toMiddle, fromMiddle, toOutside))));
    }

    private static <T> AsyncAction<Void> middleProcessBody(BufferProcessContext<T> ctx) {
        return ctx.evtIn().receive().bind(evtIn -> {
            switch (evtIn.type) {
                case SEND:
                    if (ctx.isUnbuffered()) {
                        return ctx.receiverOut().send(null).then(middleProcessBody(ctx.senderBusy(false).receiverBusy(true)));
                    }
                    AsyncAction<Void> result = AsyncAction.unit(null);
                    boolean sentFromBuffer = false;
                    if (!ctx.isEmpty()) {
                        result = result.then(ctx.senderOut().send(ctx.peek()));
                        sentFromBuffer = true;
                    }
                    if (!ctx.receiverBusy()) {
                        result = result.then(ctx.receiverOut().send(null));
                    }
                    if (sentFromBuffer) {
                        return result.then(middleProcessBody(ctx.senderBusy(true).receiverBusy(true).poll()));
                    } else {
                        return result.then(middleProcessBody(ctx.senderBusy(false)));
                    }
                case RECEIVE:
                    if (ctx.isUnbuffered()) {
                        return ctx.senderOut().send(evtIn.value).then(middleProcessBody(ctx.senderBusy(true).receiverBusy(false)));
                    }
                    if (!ctx.senderBusy()) {
                        return ctx.senderOut().send(evtIn.value).then(ctx.receiverOut().send(null)).then(middleProcessBody(ctx.senderBusy(true)));
                    }
                    if (!ctx.isNearFull()) {
                        return ctx.receiverOut().send(null).then(middleProcessBody(ctx.offer(evtIn.value)));
                    } else {
                        return middleProcessBody(ctx.offer(evtIn.value).receiverBusy(false));
                    }
                default:
                    return AsyncAction.wrap(() -> {
                        throw new Exception();
                    });
            }
        });
    }
}
