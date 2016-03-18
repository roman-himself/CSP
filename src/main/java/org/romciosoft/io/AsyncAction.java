package org.romciosoft.io;

import org.romciosoft.monad.Monad;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface AsyncAction<T> extends Monad<AsyncAction<?>, T> {
    static <T> AsyncAction<T> fromMonad(Monad<AsyncAction<?>, T> monad) {
        return (AsyncAction<T>) monad;
    }

    IOAction<Promise<T>> getIOAction(IOActionExecutor exe);

    default <U> AsyncAction<U> bind(Function<T, Monad<AsyncAction<?>, U>> fun) {
        return exe -> Promise.<U>newPromise(exe).bind(responsePromise ->
                getIOAction(exe).bind(leftPromise ->
                        leftPromise.addCallback(leftValue ->
                                fromMonad(fun.apply(leftValue)).getIOAction(exe).bind(rightPromise ->
                                        rightPromise.addCallback(responsePromise::deliver))))
                        .then(IOAction.unit(responsePromise)));
    }

    default <U> AsyncAction<U> then(Monad<AsyncAction<?>, U> monad) {
        return bind(x -> monad);
    }

    static <T> AsyncAction<T> wrap(IOAction<T> ioAction) {
        return exe -> ioAction.bind(result -> Promise.newPromise(exe, result));
    }

    static <T> AsyncAction<T> unit(T value) {
        return wrap(IOAction.unit(value));
    }

    static AsyncAction<Void> fork(AsyncAction<Void> action) {
        return exe -> exe.submit(action.getIOAction(exe).then(IOAction.unit(null))).then(Promise.newPromise(exe, null));
    }

    static AsyncAction<Void> delay(long delay, TimeUnit unit) {
        return exe -> Promise.<Void>newPromise(exe).bind(response -> exe.schedule(response.deliver(null), delay, unit).then(IOAction.unit(response)));
    }
}
