package org.romciosoft.data;

public class PersistentStack<T> implements IPersistentStack<T> {
    private static class Sentinel<T> implements IPersistentStack<T> {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public T peek() {
            throw new IllegalAccessError("empty stack");
        }

        @Override
        public IPersistentStack<T> push(T val) {
            return new PersistentStack<>(this, val);
        }

        @Override
        public IPersistentStack<T> pop() {
            throw new IllegalAccessError("empty stack");
        }
    }

    private static final Sentinel<?> EMPTY = new Sentinel<>();

    private IPersistentStack<T> parent;
    private T val;

    private PersistentStack(IPersistentStack<T> parent, T val) {
        this.parent = parent;
        this.val = val;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public T peek() {
        return val;
    }

    @Override
    public IPersistentStack<T> push(T val) {
        return new PersistentStack<>(this, val);
    }

    @Override
    public IPersistentStack<T> pop() {
        return parent;
    }

    @SuppressWarnings("unchecked")
    public static <T> IPersistentStack<T> empty() {
        return (IPersistentStack<T>) EMPTY;
    }

    public static <T> IPersistentStack<T> reverse(IPersistentStack<T> from) {
        IPersistentStack<T> to = empty();
        while (!from.isEmpty()) {
            to = to.push(from.peek());
            from = from.pop();
        }
        return to;
    }
}
