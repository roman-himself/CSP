package org.romciosoft.csp;

import org.romciosoft.io.Promise;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class SelectToken<T> {
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

    SelectToken(Promise<SelectResult<T>> promise) {
        this.promise = promise;
        id = COUNTER.incrementAndGet();
    }

    private static <T> void lockBoth(SelectToken<T> one, SelectToken<T> two) {
        if (one.id < two.id) {
            one.lock.lock();
            two.lock.lock();
        } else {
            two.lock.lock();
            one.lock.lock();
        }
    }

    private static <T> void unlockBoth(SelectToken<T> one, SelectToken<T> two) {
        if (one.id < two.id) {
            one.lock.unlock();
            two.lock.unlock();
        } else {
            two.lock.unlock();
            one.lock.unlock();
        }
    }

    static <T> MatchResult match(ChannelHandle<T> handle, T value, SelectToken<T> sender, SelectToken<T> receiver) throws Exception {
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
