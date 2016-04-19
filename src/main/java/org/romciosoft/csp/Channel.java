package org.romciosoft.csp;

import org.romciosoft.io.Promise;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

interface Channel<T> {
    boolean send(SelectToken<T> token, T value) throws Exception;
    boolean receive(SelectToken<T> token) throws Exception;

    ChannelHandle<T> getHandle();
}
