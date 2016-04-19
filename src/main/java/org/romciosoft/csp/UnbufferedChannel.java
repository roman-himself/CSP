package org.romciosoft.csp;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

class UnbufferedChannel<T> implements Channel<T> {
    private static class SndQItem<T> {
        SelectToken<T> token;
        T value;

        SndQItem(SelectToken<T> token, T value) {
            this.token = token;
            this.value = value;
        }

        static <T> SndQItem<T> of(SelectToken<T> token, T value) {
            return new SndQItem<>(token, value);
        }
    }

    private ChannelHandle<T> handle = new ChannelHandle<>(this, this);
    private ReentrantLock lock = new ReentrantLock();
    private Queue<SndQItem<T>> sndQueue = new LinkedList<>();
    private Queue<SelectToken<T>> rcvQueue = new LinkedList<>();

    @Override
    public boolean send(SelectToken<T> token, T value) throws Exception {
        try {
            lock.lock();
            if (rcvQueue.isEmpty()) {
                sndQueue.offer(SndQItem.of(token, value));
                return false;
            }
            SelectToken.MatchResult result;
            do {
                result = SelectToken.match(handle, value, token, rcvQueue.peek());
                if (result.receiverWasDone || result.matched) {
                    rcvQueue.poll();
                }
                if (result.senderWasDone || result.matched) {
                    break;
                }
            } while(!rcvQueue.isEmpty());
            if (!result.matched && !result.senderWasDone) {
                sndQueue.offer(SndQItem.of(token, value));
            }
            return result.matched;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean receive(SelectToken<T> token) throws Exception {
        try {
            lock.lock();
            if (sndQueue.isEmpty()) {
                rcvQueue.offer(token);
                return false;
            }
            SelectToken.MatchResult result;
            do {
                SndQItem<T> sndQItem = sndQueue.peek();
                result = SelectToken.match(handle, sndQItem.value, sndQItem.token, token);
                if (result.senderWasDone || result.matched) {
                    sndQueue.poll();
                }
                if (result.receiverWasDone || result.matched) {
                    break;
                }
            } while(!sndQueue.isEmpty());
            if (!result.matched && !result.receiverWasDone) {
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
