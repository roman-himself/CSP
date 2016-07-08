package org.romciosoft.csp;

import org.romciosoft.io.AsyncAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SelectBuilder2<T> {
    public interface LazyWrapper<T> {
        T unwrap();
    }

    private Map<ChannelHandle.SendPort<?>, LazyWrapper<AsyncAction<T>>> sndMap = new HashMap<>();
    private Map<ChannelHandle.ReceivePort<?>, Function<?, AsyncAction<T>>> rcvMap = new HashMap<>();
    private List<SelectOption<?>> options = new ArrayList<>();

    SelectBuilder2() {
    }

    public <U> SelectBuilder2<T> send(ChannelHandle.SendPort<U> sndPort, U value, LazyWrapper<AsyncAction<T>> then) {
        sndMap.put(sndPort, then);
        options.add(SelectOption.send(sndPort, value));
        return this;
    }

    public <U> SelectBuilder2<T> send(ChannelHandle.SendPort<U> sndPort, U value, AsyncAction<T> then) {
        return send(sndPort, value, () -> then);
    }

    public <U> SelectBuilder2<T> receive(ChannelHandle.ReceivePort<U> rcvPort, Function<U, AsyncAction<T>> then) {
        rcvMap.put(rcvPort, then);
        options.add(SelectOption.receive(rcvPort));
        return this;
    }

    public AsyncAction<T> build() {
        AsyncAction<SelectResult<?>> action = exe -> () -> CSP.processSelect(exe, (List) options);
        return action.bind(result -> {
            switch (result.getType()) {
                case SENT:
                    return sndMap.get(result.getSendPort()).unwrap();
                case RECEIVED:
                    return ((Function<Object, AsyncAction<T>>) rcvMap.get(result.getReceivePort())).apply(result.getReceivedValue());
                default:
                    throw new AssertionError();
            }
        });
    }
}
