package org.romciosoft.data;

public class PersistentQueue<T> implements IPersistentQueue<T> {
    private static class Sentinel<T> implements IPersistentQueue<T> {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public T peek() {
            throw new IllegalAccessError("empty queue");
        }

        @Override
        public IPersistentQueue<T> poll() {
            throw new IllegalAccessError("empty queue");
        }

        @Override
        public IPersistentQueue<T> offer(T val) {
            return new PersistentQueue<>(PersistentStack.empty(), PersistentStack.<T>empty().push(val));
        }
    }

    private static final Sentinel<?> EMPTY = new Sentinel<>();

    private IPersistentStack<T> forward;
    private IPersistentStack<T> backward;

    private PersistentQueue(IPersistentStack<T> forward, IPersistentStack<T> backward) {
        this.forward = forward;
        this.backward = backward;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public T peek() {
        return backward.peek();
    }

    @Override
    public IPersistentQueue<T> poll() {
        if (!backward.pop().isEmpty()) {
            return new PersistentQueue<>(forward, backward.pop());
        }
        if (!forward.isEmpty()) {
            return new PersistentQueue<>(PersistentStack.empty(), PersistentStack.reverse(forward));
        }
        return empty();
    }

    @Override
    public IPersistentQueue<T> offer(T val) {
        return new PersistentQueue<>(forward.push(val), backward);
    }

    public static <T> IPersistentQueue<T> empty() {
        return (IPersistentQueue<T>) EMPTY;
    }
}
