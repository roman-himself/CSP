package org.romciosoft.csp;

interface Channel<T> {
    boolean send(SelectToken<? extends T> token, T value) throws Exception;
    boolean receive(SelectToken<? extends T> token) throws Exception;

    ChannelHandle<T> getHandle();
}
