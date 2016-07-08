package org.romciosoft.csp;

import org.romciosoft.io.AsyncAction;
import org.romciosoft.io.IOActionExecutor;
import org.romciosoft.io.Promise;
import java.util.Iterator;
import java.util.List;

public class CSP {
    private CSP() {
    }

    static <T> Promise<SelectResult<T>> processSelect(IOActionExecutor executor, List<SelectOption<? extends T>> selectOptions) throws Exception {
        Promise<SelectResult<T>> pro = Promise.<SelectResult<T>>newPromise(executor).perform();
        SelectToken<T> token = new SelectToken<>(pro);
        boolean madeIt = false;
        Iterator<SelectOption<? extends T>> itr = selectOptions.iterator();
        while (!madeIt && itr.hasNext()) {
            SelectOption<? extends T> option = itr.next();
            switch (option.type) {
                case SEND:
                    madeIt = ((Channel<T>) option.getChannel()).send(token, option.value);
                    break;
                case RECEIVE:
                    madeIt = ((Channel<T>) option.getChannel()).receive(token);
                    break;
                default:
                    throw new AssertionError();
            }
        }
        return pro;
    }

    public static <T> SelectBuilder2<T> select() {
        return new SelectBuilder2<>();
    }

    public static <T> AsyncAction<ChannelHandle<T>> newChannel(int bufferSize) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("negative buffer size");
        }
        if (bufferSize == 0) {
            return AsyncAction.unit(new UnbufferedChannel<T>().getHandle());
        }
        return BufferedChannel.bufferedChannel(bufferSize);
    }

    public static <T> AsyncAction<ChannelHandle<T>> newChannel() {
        return newChannel(0);
    }
}