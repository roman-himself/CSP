package org.romciosoft.csp;

import org.romciosoft.io.Promise;
import org.romciosoft.monad.Maybe;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

interface Channel<T> {
    boolean send(Token<T> token, T value) throws Exception;
    boolean receive(Token<T> token) throws Exception;

    ChannelHandle<T> getHandle();

    class Token<T> {
        private static final AtomicLong COUNTER = new AtomicLong(0);

        static class MatchResult {
            final boolean matched;
            final boolean senderWasDone;
            final boolean receiverWasDone;

            MatchResult(boolean matched, boolean senderWasDone, boolean receiverWasDone) {
                this.matched = matched;
                this.senderWasDone = senderWasDone;
                this.receiverWasDone = receiverWasDone;
            }
        }

        private final Promise<SelectResult<T>> promise;
        private boolean done = false;
        private final long id;
        private final Lock lock = new ReentrantLock();

        Token(Promise<SelectResult<T>> promise) {
            this.promise = promise;
            id = COUNTER.incrementAndGet();
        }

        private static <T> void lockBoth(Token<T> one, Token<T> two) {
            if (one.id < two.id) {
                one.lock.lock();
                two.lock.lock();
            } else {
                two.lock.lock();
                one.lock.lock();
            }
        }

        private static <T> void unlockBoth(Token<T> one, Token<T> two) {
            if (one.id < two.id) {
                one.lock.unlock();
                two.lock.unlock();
            } else {
                two.lock.unlock();
                one.lock.unlock();
            }
        }

        static <T> MatchResult match(ChannelHandle<T> handle, T value, Token<T> sender, Token<T> receiver) throws Exception {
            try {
                lockBoth(sender, receiver);
                if (sender.done ^ receiver.done) {
                    if (sender.done) {
                        return new MatchResult(false, true, false);
                    } else {
                        return new MatchResult(false, false, true);
                    }
                } else if (sender.done && receiver.done) {
                    return new MatchResult(false, true, true);
                }
                sender.promise.deliver(SelectResult.sent(handle.getSendPort())).perform();
                receiver.promise.deliver(SelectResult.received(handle.getReceivePort(), value)).perform();
                sender.done = true;
                receiver.done = true;
                return new MatchResult(true, false, false);
            } finally {
                unlockBoth(sender, receiver);
            }
        }
    }
}
