package org.romciosoft.csp;

import org.romciosoft.io.AsyncAction;
import org.romciosoft.io.IOActionExecutor;
import org.romciosoft.io.Promise;
import java.util.Iterator;
import java.util.List;

public class CSP {
    private CSP() {
    }

    static <T> Promise<SelectResult<T>> processSelect(IOActionExecutor executor, List<SelectOption<T>> selectOptions) throws Exception {
        Promise<SelectResult<T>> pro = Promise.<SelectResult<T>>newPromise(executor).perform();
        SelectToken<T> token = new SelectToken<>(pro);
        boolean madeIt = false;
        Iterator<SelectOption<T>> itr = selectOptions.iterator();
        while (!madeIt && itr.hasNext()) {
            SelectOption<T> option = itr.next();
            switch (option.type) {
                case SEND:
                    madeIt = option.getChannel().send(token, option.value);
                    break;
                case RECEIVE:
                    madeIt = option.getChannel().receive(token);
                    break;
                default:
                    throw new AssertionError();
            }
        }
        return pro;
    }

    public static <T> SelectBuilder<T> select() {
        return new SelectBuilder<>();
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