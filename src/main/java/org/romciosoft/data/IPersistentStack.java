package org.romciosoft.data;

public interface IPersistentStack<T> {
    boolean isEmpty();
    T peek();
    IPersistentStack<T> push(T val);
    IPersistentStack<T> pop();
}
