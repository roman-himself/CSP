package org.romciosoft.csp;

import org.romciosoft.io.Promise;
import org.romciosoft.monad.Maybe;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;

class UnbufferedChannel<T> implements Channel<T> {
    private static class SndQItem<T> {
        Promise<SelectResult<T>> promise;
        T value;

        static <T> SndQItem<T> of(Promise<SelectResult<T>> promise, T value) {
            SndQItem<T> item = new SndQItem<>();
            item.promise = promise;
            item.value = value;
            return item;
        }
    }

    private ChannelHandle<T> handle = new ChannelHandle<>(this, this);
    private ReentrantLock lock = new ReentrantLock();
    private Queue<SndQItem<T>> sndQueue = new LinkedList<>();
    private Queue<Promise<SelectResult<T>>> rcvQueue = new LinkedList<>();

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void unlock() {
        lock.unlock();
    }

    @Override
    public boolean trySend(T value) throws Exception {
        SelectResult<T> selectResult = SelectResult.received(getHandle().getReceivePort(), value);
        while (!rcvQueue.isEmpty()) {
            Promise<SelectResult<T>> promise = rcvQueue.poll();
            if (promise.tryDeliver(selectResult).perform()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Maybe<T> tryReceive() throws Exception {
        SelectResult<T> selectResult = SelectResult.sent(getHandle().getSendPort());
        while (!sndQueue.isEmpty()) {
            SndQItem<T> sndQItem = sndQueue.poll();
            if (sndQItem.promise.tryDeliver(selectResult).perform()) {
                return Maybe.just(sndQItem.value);
            }
        }
        return Maybe.nothing();
    }

    @Override
    public void offerSender(T value, Promise<SelectResult<T>> promise) {
        sndQueue.offer(SndQItem.of(promise, value));
    }

    @Override
    public void offerReceiver(Promise<SelectResult<T>> promise) {
        rcvQueue.offer(promise);
    }

    @Override
    public ChannelHandle<T> getHandle() {
        return handle;
    }

    @Override
    public int compareTo(Channel<T> o) {
        return hashCode() - o.hashCode();
    }
}
