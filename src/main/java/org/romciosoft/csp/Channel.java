package org.romciosoft.csp;

import org.romciosoft.io.Promise;
import org.romciosoft.monad.Maybe;

interface Channel<T> extends Comparable<Channel<T>> {
    void lock();
    void unlock();

    boolean trySend(T value) throws Exception;
    Maybe<T> tryReceive() throws Exception;

    void offerSender(T value, Promise<SelectResult<T>> promise);
    void offerReceiver(Promise<SelectResult<T>> promise);

    ChannelHandle<T> getHandle();
}
