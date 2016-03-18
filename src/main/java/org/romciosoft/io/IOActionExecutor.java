package org.romciosoft.io;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
