package org.romciosoft.csp.examples;

import org.romciosoft.csp.CSP;
import org.romciosoft.csp.ChannelHandle;
import org.romciosoft.io.AsyncAction;
import org.romciosoft.io.IOActionExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

public class StatelessService {
    static class ServiceRequest<RequestPayload, ResponsePayload> {
        RequestPayload request;
        ChannelHandle.SendPort<ResponsePayload> responseChannel;

        ServiceRequest(RequestPayload request, ChannelHandle.SendPort<ResponsePayload> responseChannel) {
            this.request = request;
            this.responseChannel = responseChannel;
        }
    }

    static class ServiceHandle<Req, Rep> {
        ChannelHandle.SendPort<ServiceRequest<Req, Rep>> reqChannel;

        ServiceHandle(ChannelHandle.SendPort<ServiceRequest<Req, Rep>> reqChannel) {
            this.reqChannel = reqChannel;
        }

        AsyncAction<Rep> call(Req requestPayload) {
            return CSP.<Rep>newChannel()
                    .bind(ch ->
                            reqChannel.send(new ServiceRequest<Req, Rep>(requestPayload, ch.getSendPort()))
                                    .then(ch.getReceivePort().receive()));
        }
    }

    static <Req, Rep> AsyncAction<Void> serviceBody(ChannelHandle.ReceivePort<ServiceRequest<Req, Rep>> reqIn,
                                                    Function<Req, Rep> fun) {
        return reqIn.receive().bind(req ->
                AsyncAction.fork(req.responseChannel.send(fun.apply(req.request)))
                .then(serviceBody(reqIn, fun)));
    }

    static <Req, Rep> AsyncAction<ServiceHandle<Req, Rep>> startService(Function<Req, Rep> fun) {
        return CSP.<ServiceRequest<Req, Rep>>newChannel().bind(ch ->
                AsyncAction.fork(serviceBody(ch.getReceivePort(), fun))
                        .then(AsyncAction.unit(new ServiceHandle<Req, Rep>(ch.getSendPort()))));
    }

    static <T> T performActionBlocking(IOActionExecutor exe, AsyncAction<T> action) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        action.bind(result -> AsyncAction.wrap(() -> {
            future.complete(result);
            return null;
        })).getIOAction(exe).perform();
        return future.get();
    }

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService schSvc = Executors.newScheduledThreadPool(10);
        IOActionExecutor exe = new IOActionExecutor(schSvc);
        ServiceHandle<String, String> capService =
                performActionBlocking(exe, startService(String::toUpperCase));
        System.out.println(performActionBlocking(exe, capService.call("hello world")));
        schSvc.shutdown();
    }
}
