package org.romciosoft.io;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class Promise<T> {
    private boolean isCompleted;
    private T value;
    private Queue<Function<T, IOAction<Void>>> callbacks;
    private IOActionExecutor executor;
    private ReentrantLock lock = new ReentrantLock();

    private Promise(IOActionExecutor executor) {
        this.executor = executor;
        isCompleted = false;
        callbacks = new LinkedList<>();
    }

    private Promise(IOActionExecutor executor, T value) {
        this.executor = executor;
        this.value = value;
        isCompleted = true;
    }

    public static <T> IOAction<Promise<T>> newPromise(IOActionExecutor exe) {
        return () -> new Promise(exe);
    }

    public static <T> IOAction<Promise<T>> newPromise(IOActionExecutor exe, T value) {
        return () -> new Promise(exe, value);
    }

    private void _deliver(T value) throws Exception {
        try {
            lock.lock();
            if (isCompleted) {
                throw new IllegalStateException("Promise already delivered");
            }
            isCompleted = true;
            this.value = value;
            while (!callbacks.isEmpty()) {
                Function<T, IOAction<Void>> cbk = callbacks.poll();
                executor.submit(cbk.apply(value)).perform();
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean _tryDeliver(T value) throws Exception {
        try {
            lock.lock();
            if (isCompleted) {
                return false;
            }
            _deliver(value);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void _addCallback(Function<T, IOAction<Void>> cbk) throws Exception {
        try {
            lock.lock();
            if (isCompleted) {
                executor.submit(cbk.apply(value)).perform();
            } else {
                callbacks.offer(cbk);
            }
        } finally {
            lock.unlock();
        }
    }

    public IOAction<Void> deliver(T value) {
        return () -> {
            _deliver(value);
            return null;
        };
    }

    public IOAction<Boolean> tryDeliver(T value) {
        return () -> _tryDeliver(value);
    }

    public IOAction<Void> addCallback(Function<T, IOAction<Void>> cbk) {
        return () -> {
            _addCallback(cbk);
            return null;
        };
    }
}
