package org.romciosoft.io;

import org.junit.Test;

import static org.junit.Assert.*;

public class IOActionTest {
    @Test
    public void ioActionBinding() throws Exception {
        IOAction<Integer> action = IOAction.unit(40).bind(x -> IOAction.unit(x + 2));
        assertEquals((Integer) 42, action.perform());
    }

    @Test
    public void ioActionExceptionHandling() throws Exception {
        class MyException extends RuntimeException {
            String msg;
            MyException(String msg) {
                this.msg = msg;
            }
        }
        IOAction<String> action = () -> {
            throw new MyException("foo");
        };
        action = action.wrapException(MyException.class, e -> IOAction.unit(e.msg));
        assertEquals("foo", action.perform());
    }
}
