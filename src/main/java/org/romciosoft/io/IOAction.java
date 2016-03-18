package org.romciosoft.io;

import org.romciosoft.monad.Monad;

import java.util.function.Function;

public interface IOAction<T> extends Monad<IOAction<?>, T> {
    static <T> IOAction<T> fromMonad(Monad<IOAction<?>, T> monad) {
        return (IOAction<T>) monad;
    }

    T perform() throws Exception;

    default <U> IOAction<U> bind(Function<T, Monad<IOAction<?>, U>> fun) {
        return () -> fromMonad(fun.apply(perform())).perform();
    }

    default <U> IOAction<U> then(Monad<IOAction<?>, U> monad) {
        return bind(x -> monad);
    }

    static <T> IOAction<T> unit(T value) {
        return () -> value;
    }

    default <E extends Throwable> IOAction<T> wrapException(Class<E> cls, Function<E, IOAction<T>> handler) {
        return () -> {
            try {
                return perform();
            } catch (Throwable t) {
                if (cls.isAssignableFrom(t.getClass())) {
                    return handler.apply((E) t).perform();
                }
                throw t;
            }
        };
    }
}
