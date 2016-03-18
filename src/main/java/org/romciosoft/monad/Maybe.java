package org.romciosoft.monad;

import java.util.function.Function;

public class Maybe<T> implements Monad<Maybe<?>, T> {
    private static final Maybe<?> NOTHING = new Maybe<>(true, null);
    private boolean isNothing;
    private T value;

    public static <T> Maybe<T> fromMonad(Monad<Maybe<?>, T> monad) {
        return (Maybe<T>) monad;
    }

    private Maybe(boolean isNothing, T value) {
        this.isNothing = isNothing;
        this.value = value;
    }

    public static <T> Maybe<T> just(T value) {
        return new Maybe<>(false, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> Maybe<T> nothing() {
        return (Maybe<T>) NOTHING;
    }

    public boolean isPresent() {
        return !isNothing;
    }

    public boolean isNothing() {
        return isNothing;
    }

    public T getValue() {
        if (isNothing()) {
            throw new IllegalAccessError();
        }
        return value;
    }

    @Override
    public <V> Maybe<V> bind(Function<T, Monad<Maybe<?>, V>> fun) {
        if (isNothing()) {
            return nothing();
        }
        return fromMonad(fun.apply(value));
    }

    public <V> Maybe<V> then(Monad<Maybe<?>, V> monad) {
        return bind(x -> monad);
    }
}
