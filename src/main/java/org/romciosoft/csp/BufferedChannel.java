package org.romciosoft.csp;

import org.romciosoft.data.IPersistentQueue;
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

    private static <T> AsyncAction<Void> inProcessBody(ChannelHandle.SendPort<IOEvent<T>> toMiddle, ChannelHandle.ReceivePort<Void> fromMiddle, ChannelHandle.ReceivePort<T> fromOutside) {
        return fromOutside.receive().bind(received ->
                toMiddle.send(IOEvent.received(received))
                        .then(fromMiddle.receive().then(inProcessBody(toMiddle, fromMiddle, fromOutside))));
    }

    private static <T> AsyncAction<Void> outProcessBody(ChannelHandle.SendPort<IOEvent<T>> toMiddle, ChannelHandle.ReceivePort<T> fromMiddle, ChannelHandle.SendPort<T> toOutside) {
        return fromMiddle.receive().bind(val -> toOutside.send(val).then(toMiddle.send(IOEvent.sent()).then(outProcessBody(toMiddle, fromMiddle, toOutside))));
    }

    private static <T> AsyncAction<Void> middleProcessBody(
            IPersistentQueue<T> buffer,
            int bufferSize,
            int bufferLimit,
            ChannelHandle.ReceivePort<IOEvent<T>> evtIn,
            ChannelHandle.SendPort<T> senderOut,
            ChannelHandle.SendPort<Void> receiverOut,
            boolean senderBusy,
            boolean receiverBusy
    ) {
        return evtIn.receive().bind(evt -> {
            AsyncAction<Void> result;
            switch (evt.type) {
                case SEND:
                    if (bufferSize == 0) {
                        return middleProcessBody(buffer, bufferSize, bufferLimit, evtIn, senderOut, receiverOut, false, receiverBusy);
                    }
                    result = senderOut.send(buffer.peek());
                    if (!receiverBusy) {
                        result = result.then(receiverOut.send(null));
                    }
                    return result.then(middleProcessBody(buffer.poll(), bufferSize - 1, bufferLimit, evtIn, senderOut, receiverOut, true, true));
                case RECEIVE:
                    if (!senderBusy) { // also empty buffer
                        result = senderOut.send(evt.value);
                        result = result.then(receiverOut.send(null));
                        return result.then(middleProcessBody(buffer, bufferSize, bufferLimit, evtIn, senderOut, receiverOut, true, true));
                    }
                    if (bufferSize == bufferLimit - 1) {
                        return middleProcessBody(buffer.offer(evt.value), bufferSize + 1, bufferLimit, evtIn, senderOut, receiverOut, true, false);
                    }
                    return receiverOut.send(null)
                            .then(middleProcessBody(buffer.offer(evt.value), bufferSize + 1, bufferLimit, evtIn, senderOut, receiverOut, true, true));
                default:
                    return null;
            }
        });
    }
}
