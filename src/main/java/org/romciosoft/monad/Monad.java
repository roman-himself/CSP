package org.romciosoft.monad;

import java.util.function.Function;

public interface Monad<T extends Monad<?, ?>, U> {
    <V> Monad<T, V> bind(Function<U, Monad<T, V>> fun);

    default <V> Monad<T, V> then(Monad<T, V> monad) {
        return bind(x -> monad);
    }
}
