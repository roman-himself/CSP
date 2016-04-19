package org.romciosoft.csp;

interface Channel<T> {
    boolean send(SelectToken<T> token, T value) throws Exception;
    boolean receive(SelectToken<T> token) throws Exception;

    ChannelHandle<T> getHandle();
}
