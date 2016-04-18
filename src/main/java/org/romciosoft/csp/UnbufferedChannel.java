package org.romciosoft.csp;

import org.romciosoft.io.Promise;
import org.romciosoft.monad.Maybe;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class UnbufferedChannel<T> implements Channel<T> {
    private static class SndQItem<T> {
        Channel.Token<T> token;
        T value;

        SndQItem(Channel.Token<T> token, T value) {
            this.token = token;
            this.value = value;
        }

        static <T> SndQItem<T> of(Channel.Token<T> token, T value) {
            return new SndQItem<>(token, value);
        }
    }

    private ChannelHandle<T> handle = new ChannelHandle<>(this, this);
    private ReentrantLock lock = new ReentrantLock();
    private Queue<SndQItem<T>> sndQueue = new LinkedList<>();
    private Queue<Channel.Token<T>> rcvQueue = new LinkedList<>();

    @Override
    public boolean send(Token<T> token, T value) throws Exception {
        try {
            lock.lock();
            if (rcvQueue.isEmpty()) {
                sndQueue.offer(SndQItem.of(token, value));
                return false;
            }
            Token.MatchResult result;
            do {
                result = Channel.Token.match(handle, value, token, rcvQueue.peek());
                if (result.receiverWasDone) {
                    rcvQueue.poll();
                }
                if (result.senderWasDone || result.matched) {
                    break;
                }
            } while(!rcvQueue.isEmpty());
            if (!result.matched) {
                sndQueue.offer(SndQItem.of(token, value));
            }
            return result.matched;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean receive(Token<T> token) throws Exception {
        try {
            lock.lock();
            if (sndQueue.isEmpty()) {
                rcvQueue.offer(token);
                return false;
            }
            Token.MatchResult result;
            do {
                SndQItem<T> sndQItem = sndQueue.peek();
                result = Channel.Token.match(handle, sndQItem.value, sndQItem.token, token);
                if (result.senderWasDone) {
                    sndQueue.poll();
                }
                if (result.receiverWasDone || result.matched) {
                    break;
                }
            } while(!sndQueue.isEmpty());
            if (!result.matched) {
                rcvQueue.offer(token);
            }
            return result.matched;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ChannelHandle<T> getHandle() {
        return handle;
    }

}
