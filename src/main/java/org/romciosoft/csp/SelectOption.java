package org.romciosoft.csp;


class SelectOption<T> {
    enum Type {
        SEND, RECEIVE
    }

    Type type;
    ChannelHandle.SendPort<? extends T> sendPort;
    ChannelHandle.ReceivePort<? extends T> rcvPort;
    T value;

    private SelectOption(Type type, ChannelHandle.SendPort<? extends T> sendPort, ChannelHandle.ReceivePort<? extends T> rcvPort, T value) {
        this.type = type;
        this.sendPort = sendPort;
        this.rcvPort = rcvPort;
        this.value = value;
    }

    static <T> SelectOption<T> send(ChannelHandle.SendPort<? extends T> port, T value) {
        return new SelectOption<>(Type.SEND, port, null, value);
    }

    static <T> SelectOption<T> receive(ChannelHandle.ReceivePort<? extends T> port) {
        return new SelectOption<>(Type.RECEIVE, null, port, null);
    }

    Channel<? extends T> getChannel() {
        switch (type) {
            case SEND:
                return sendPort.channel;
            case RECEIVE:
                return rcvPort.channel;
            default:
                throw new AssertionError();
        }
    }
}
