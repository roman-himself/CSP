package org.romciosoft.csp;

import org.romciosoft.io.AsyncAction;

public class ChannelHandle<T> {
    public static class SendPort<T> {
        Channel<T> channel;

        SendPort(Channel<T> channel) {
            this.channel = channel;
        }

        public AsyncAction<Void> send(T value) {
            return CSP.<Void>select().send(this, value, AsyncAction.unit(null)).build();
        }
    }

    public static class ReceivePort<T> {
        Channel<T> channel;

        ReceivePort(Channel<T> channel) {
            this.channel = channel;
        }

        public AsyncAction<T> receive() {
            return CSP.<T>select().receive(this, AsyncAction::unit).build();
        }
    }

    private SendPort<T> sendPort;
    private ReceivePort<T> receivePort;

    ChannelHandle(Channel<T> sndChannel, Channel<T> rcvChannel) {
        sendPort = new SendPort<>(sndChannel);
        receivePort = new ReceivePort<>(rcvChannel);
    }

    ChannelHandle(SendPort<T> sendPort, ReceivePort<T> receivePort) {
        this.sendPort = sendPort;
        this.receivePort = receivePort;
    }

    public SendPort<T> getSendPort() {
        return sendPort;
    }

    public ReceivePort<T> getReceivePort() {
        return receivePort;
    }
}
