# What is it #
* An implementation in Java of Communicating Sequential Processes that rips off unashamedly the ideas found in other languages like Go and Haskell.
* Made for fun and exercise above all else.
* Aims to enable the modeling of an arbitralily large number of concurrent, coordinating processes and run that model on a thread pool consisting of a reasonable number of threads.
* Achieves the above by means of explicit, programatic continuations, inspired by Haskell's IO actions, facilitated by Java 8's lambda syntax.

# An overview of the concurrency model #
* Mostly mimics that of Go: https://www.golang-book.com/books/intro/10
* A number of processes coordinate by sending/receiving messages on typed channels.
* New processes, once started, are anonymous and are affected only via channels they were passed on creation.
* Channels are unbuffered by default, ie. a process that sends a message on a channel is blocked until another process receives on that channel.
* Buffered channels consist simply of two unbuffered channels and a process in between that handles the message buffer.
* Readiness selection: a process may select from a number of send/receive operations on different channels. Whichever of these operations becomes available first, is performed.

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

`bind` takes a function of the contained `U` value to a new wrapper of type `T` over another value and returns that new wrapper.

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

	IOAction.<String>unit("World").bind(
	  x -> () -> 
	    System.out.println("Hello " + x + '!')).perform();

will print "Hello World!" to the console.

## IOActionExecutor ##
`IOActionExecutor` is a simple wrapper over a `java.util.concurrent.ScheduledExecutorService` that's used by Promises explained later on. I'll just paste it here rather than delve into explanations.

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
* `static <T> IOAction<Promise<T>> newPromise(IOActionExecutor exe)` -- an `IOAction` that yields a newly created `Promise`. The `IOActionExecutor` is for submitting callback actions.
* `IOAction<Void> addCallback(Function<T, IOAction<Void>> cbk)` -- upon performing, `cbk` will be queued if the `Promise` hasn't been delivered yet or immediately submitted if it has been delivered.
* `IOAction<Void> deliver(T value)` -- delivers the promise and submits callback actions. If the `Promise` is already delivered when the `IOAction` is performed, the action will throw an `IllegalStateException`.
* `IOAction<Boolean> tryDeliver(T value)` -- same as above, but has different behavior in case when the `Promise` is already delivered. The `Boolean` yielded by returned `IOAction` indicates whether or not the delivery was successful.