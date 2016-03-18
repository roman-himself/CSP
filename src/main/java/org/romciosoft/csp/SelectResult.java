package org.romciosoft.csp;

public class SelectResult<T> {
    public static enum Type {
        SEND, RECEIVE
    }

    private Type type;
    private T value;
    private ChannelHandle.SendPort<T> sendPort;
    private ChannelHandle.ReceivePort<T> receivePort;


    private SelectResult(Type type, T value, ChannelHandle.SendPort<T> sendPort, ChannelHandle.ReceivePort<T> receivePort) {
        this.type = type;
        this.value = value;
        this.sendPort = sendPort;
        this.receivePort = receivePort;
    }

    static <T> SelectResult<T> sent(ChannelHandle.SendPort<T> sendPort) {
        return new SelectResult<>(Type.SEND, null, sendPort, null);
    }

    static <T> SelectResult<T> received(ChannelHandle.ReceivePort<T> receivePort, T value) {
        return new SelectResult<>(Type.RECEIVE, value, null, receivePort);
    }

    public Type getType() {
        return type;
    }

    public ChannelHandle.SendPort<T> getSendPort() {
        if (type != Type.SEND) {
            throw new IllegalAccessError();
        }
        return sendPort;
    }

    public ChannelHandle.ReceivePort<T> getReceivePort() {
        if (type != Type.RECEIVE) {
            throw new IllegalAccessError();
        }
        return receivePort;
    }

    public T getReceivedValue() {
        if (type != Type.RECEIVE) {
            throw new IllegalAccessError();
        }
        return value;
    }
}
