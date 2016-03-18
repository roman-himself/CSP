package org.romciosoft.data;

public interface IPersistentQueue<T> {
    boolean isEmpty();
    T peek();
    IPersistentQueue<T> poll();
    IPersistentQueue<T> offer(T val);
}
