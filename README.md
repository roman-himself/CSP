# What is it #
* An implementation of Communicating Sequential Processes in Java that rips off unashamedly the ideas found in other languages like Go and Haskell.
* Made for fun and exercise above all else.
* Aims to enable the modeling of an arbitralily large number of concurrent, coordinating processes and run that model on a thread pool consisting of a reasonable number of threads.
* Achieves the above by means of explicit, programatic continuations, inspired by Haskell's IO actions, facilitated by Java 8's lambda syntax.

# An overview of the concurrency model #
* Mostly mimics that of Go: https://www.golang-book.com/books/intro/10
* A number of processes coordinate by sending/receiving messages on typed channels.
* New processes, once started, are anonymous and are affected only via channels. This is in contrast to actor systems, where processes are named and addressed directly.
* Channels are unbuffered by default, ie. a process that sends a message on a channel is blocked until another process receives on that channel.
* Buffered channels consist simply of two unbuffered channels and a process in between that handles the message buffer.
* Readiness selection: a process may select from a number of send/receive operations on different channels. Whichever of these operations becomes available first, is performed. If multiple operations are available at the same time, only one of them will be performed.

# A more or less linear walkthrough of the implementation #
## Monads ##
Keeping in mind the [monad tutorial fallacy](https://byorgey.wordpress.com/2009/01/12/abstraction-intuition-and-the-monad-tutorial-fallacy/) I will not attempt to give a comprehensive explanation of what a monad is and will instead stick to a simplified idea of monads as they apply to this project.

So with the above in mind, here's our working definition of a monad:

	public interface Monad<T extends Monad<?, ?>, U> {
	    <V> Monad<T, V> bind(Function<U, Monad<T, V>> fun);

	    default <V> Monad<T, V> then(Monad<T, V> monad) {
	        return bind(x -> monad);
	    }
	}

Not so hard now, is it? A monad is a kind of wrapper (the kind here being `T`) over a value of type `U` that supports `bind` operation.

`bind` takes a function of the contained `U` value to a new wrapper of type `T` over another value and returns a new wrapper.

Here's Haskell's `Maybe`, which is roughly equivalent to `java.util.Optional`, implemented in Java:

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

## IO Actions ##
IO Actions are Haskell's way of representing effectful computations, ie. those that cause some IO effects (like printing to stdout) and/or depend on the result of these effects (like reading from stdin). Their closest OO equivalent would be GoF's Command Pattern, with Haskell's IO Actions having the added bonus of being monads and therefore supporting purely functional composition using the `bind` operator.

So, meet `IOAction<T>`:

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

`IOAction<T>` is a functional interface over a block of code that produces a result of type `T`, much like `java.util.concurrent.Callable`. It has the static factory method `<T> IOAction<T> unit(T value)` that produces an empty `IOAction` that just yields the supplied value.

It is also a Monad, meaning it supports `bind` with a function of its result producing an explicit continuation. So for example, running:

	IOAction.unit("World").bind(
	  x -> (IOAction<Void>) () -> {
	    System.out.println("Hello " + x + '!');
	    return null;
	  }).perform();

will print "Hello World!" to the console.

## IOActionExecutor ##
`IOActionExecutor` is a simple wrapper around a `java.util.concurrent.ScheduledExecutorService` that's used by Promises as explained later on. I'll just paste it here rather than delve into explanations.

	public class IOActionExecutor {
	    private ScheduledExecutorService executorService;

	    public IOActionExecutor(ScheduledExecutorService executorService) {
	        this.executorService = executorService;
	    }

	    public IOAction<Void> submit(IOAction<Void> action) {
	        return () -> {
	            executorService.submit(() -> action.perform());
	            return null;
	        };
	    }

	    public IOAction<Void> schedule(IOAction<Void> action, long delay, TimeUnit unit) {
	        return () -> {
	            executorService.schedule(() -> action.perform(), delay, unit);
	            return null;
	        };
	    }
	}

## Promise ##
The purpose of `Promise` is akin to that of `java.util.concurrent.Future` -- it is a box that will be filled with a value at some later time. The difference is in obtaining that value -- rather than having a blocking `get()`, a `Promise<T>` accepts callbacks to its value in the form of `Function<T, IOAction<Void>>`.

`Promise<T>` has the following public api:
* `static <T> IOAction<Promise<T>> newPromise(IOActionExecutor exe)` -- returns an `IOAction` that yields the newly created `Promise`. The `IOActionExecutor` is for submitting callback actions.
* `static <T> IOAction<Promise<T>> newPromise(IOActionExecutor exe, T value)` -- returns an `IOAction` yielding a `Promise` that is already delivered with the supplied value.
* `IOAction<Void> addCallback(Function<T, IOAction<Void>> cbk)` -- upon performing, `cbk` will be queued if the `Promise` hasn't been delivered yet or immediately submitted if it has been delivered.
* `IOAction<Void> deliver(T value)` -- delivers the promise and submits callback actions. If the `Promise` is already delivered when the `IOAction` is performed, the action will throw an `IllegalStateException`.
* `IOAction<Boolean> tryDeliver(T value)` -- same as above, but has different behavior in case when the `Promise` is already delivered. The `Boolean` yielded by returned `IOAction` indicates whether or not the delivery was successful.

## AsyncAction ##
`AsyncAction<T>` is another IO monad and a functional interface that, given an instance of `IOActionExecutor`, returns an `IOAction<Promise<T>>`. This means it is like an `IOAction`, only instead of yielding its result directly it yields a `Promise` that will contain the result at some later time.

Like `IOAction`, `AsyncAction` supports binding a continuation to its result, once it is available, using the `bind` operator.

It is actually short enough to quote it here entirely and let it speak for itself:

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
            return exe -> Promise.<T>newPromise(exe).bind(pro -> exe.submit(ioAction.bind(pro::deliver)).then(IOAction.unit(pro)));
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

At this point it is hopefully becoming clear why we needed all those monads, io actions and promises to make a system that's all about processes and channels. Recall from the section on concurrency model that a process sending on a channel is blocked until another one receives on that channel (the reverse is obviously also true). An `AsyncAction` is effectively "blocked", ie. its continuation is unable to proceed, until the `Promise` yielded from its `IOAction` is delivered.
A channel object therefore acts as a meeting point, containing queues for the promises of sending and receiving processes and matching senders with receivers as they become available.

## Channel API ##
### CSP ###
`CSP` contains static methods that act as the entry point to everything channel:
* `static <T> AsyncAction<ChannelHandle<T>> newChannel(int bufferSize)` -- returns an `AsyncAction` that yields the handle to a newly created typed channel with the given buffer size. Will throw an `IllegalArgumentException` if `bufferSize` is negative.
* `static <T> AsyncAction<ChannelHandle<T>> newChannel()` -- delegates to `newChannel(0)`.
* `static <T> SelectBuilder<T> select()` -- returns a new `SelectBuilder` for specifying operations for readiness selection.

### SelectBuilder ###
`SelectBuilder<T>` accumulates a list of possible operations to perform on channels once any of them are available:
* `SelectBuilder<T> send(ChannelHandle.SendPort<T> sendPort, T value)` -- adds a send operation on the specified channel, returns `this`.
* `SelectBuilder<T> receive(ChannelHandle.ReceivePort<T> receivePort)` -- adds a receive operation, returns `this`.
* `AsyncAction<SelectResult<T>> build()` -- returns an `AsyncAction` that yields the result of readiness selection from previously specified possible operations.

### SelectResult ###
Intuitively enough, `SelectResult<T>` represents the result of a select:
* `SelectResult.Type getType()` -- `SENT` or `RECEIVED`.
* `ChannelHandle.SendPort<T> getSendPort()` -- if `getType() == SENT`, returns the `SendPort` on which sending a message succeeded, otherwise throws `IllegalAccessError`
* `ChannelHandle.ReceivePort<T> getReceivePort()` -- if `getType() == RECEIVED` returns the `ReceivePort` on which the message was received, otherwise throws `IllegalAccessError`.
* `T getReceivedValue()` -- if `getType() == RECEIVED` returns the value received by the select, otherwise throws `IllegalAccessError`

### ChannelHandle ###
A `ChannelHandle<T>` is obtained from `CSP.<T>newChannel()` and consists of one `ChannelHandle.SendPort<T>` and one `ChannelHandle.ReceivePort<T>`, which go into `SelectBuilder`'s methods. Additionally, `SendPort` and `ReceivePort` have `send(T value)` and `receive()` methods which are shorthand forms for appropriate selects.