package org.romciosoft.csp;

public class SelectResult<T> {
    public enum Type {
        SENT, RECEIVED
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
        return new SelectResult<>(Type.SENT, null, sendPort, null);
    }

    static <T> SelectResult<T> received(ChannelHandle.ReceivePort<T> receivePort, T value) {
        return new SelectResult<>(Type.RECEIVED, value, null, receivePort);
    }

    public Type getType() {
        return type;
    }

    public ChannelHandle.SendPort<T> getSendPort() {
        if (type != Type.SENT) {
            throw new IllegalAccessError("cannot get send port on RECEIVED select result");
        }
        return sendPort;
    }

    public ChannelHandle.ReceivePort<T> getReceivePort() {
        if (type != Type.RECEIVED) {
            throw new IllegalAccessError("cannot get receive port on SENT select result");
        }
        return receivePort;
    }

    public T getReceivedValue() {
        if (type != Type.RECEIVED) {
            throw new IllegalAccessError("cannot get received value on SENT select result");
        }
        return value;
    }
}
