package org.romciosoft.csp;

import org.romciosoft.io.IOActionExecutor;
import org.romciosoft.io.Promise;
import org.romciosoft.monad.Maybe;

import java.util.Collections;
import java.util.List;

public class CSP {
    private CSP() {
    }

    private static <T> void sortSelectOptions(List<SelectOption<T>> selectOptions) {
        Collections.sort(selectOptions, (option1, option2) -> option1.getChannel().compareTo(option2.getChannel()));
    }

    private static <T> void lockChannels(List<SelectOption<T>> sortedOptions) {
        for (SelectOption option : sortedOptions) {
            option.getChannel().lock();
        }
    }

    private static <T> void unlockChannels(List<SelectOption<T>> sortedOptions) {
        for (SelectOption option : sortedOptions) {
            option.getChannel().unlock();
        }
    }

    static <T> Promise<SelectResult<T>> processSelect(IOActionExecutor executor, List<SelectOption<T>> selectOptions) throws Exception {
        sortSelectOptions(selectOptions);
        lockChannels(selectOptions);
        for (SelectOption<T> option : selectOptions) {
            switch (option.type) {
                case SEND:
                    if (option.getChannel().trySend(option.value)) {
                        return Promise.newPromise(executor, SelectResult.sent(option.sendPort)).perform();
                    }
                    break;
                case RECEIVE:
                    Maybe<T> response = option.getChannel().tryReceive();
                    if (response.isPresent()) {
                        return Promise.newPromise(executor, SelectResult.received(option.rcvPort, response.getValue())).perform();
                    }
                    break;
            }
        }
        Promise<SelectResult<T>> pro = Promise.<SelectResult<T>>newPromise(executor).perform();
        for (SelectOption<T> option : selectOptions) {
            switch (option.type) {
                case SEND:
                    option.getChannel().offerSender(option.value, pro);
                    break;
                case RECEIVE:
                    option.getChannel().offerReceiver(pro);
            }
        }
        unlockChannels(selectOptions);
        return pro;
    }

    public static <T> SelectBuilder<T> select() {
        return new SelectBuilder<>();
    }
}