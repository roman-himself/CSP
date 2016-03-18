package org.romciosoft.csp;

import org.romciosoft.io.AsyncAction;

import java.util.ArrayList;
import java.util.List;

public class SelectBuilder<T> {
    List<SelectOption<T>> options = new ArrayList<>();

    SelectBuilder() {
    }

    public SelectBuilder<T> send(ChannelHandle.SendPort<T> sendPort, T value) {
        options.add(SelectOption.send(sendPort, value));
        return this;
    }

    public SelectBuilder<T> receive(ChannelHandle.ReceivePort<T> receivePort) {
        options.add(SelectOption.receive(receivePort));
        return this;
    }

    public AsyncAction<SelectResult<T>> build() {
        return exe -> () -> CSP.processSelect(exe, options);
    }
}
