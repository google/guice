package com.google.inject;

import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OutOfScopeExceptionTest {
    @Test
    public void testMessageConstructor() {
        String errorMessage = "This is an out of scope exception.";
        OutOfScopeException exception = new OutOfScopeException(errorMessage);
        assertEquals(errorMessage, exception.getMessage());
        assertNull(exception.getCause());
    }
    @Test
    public void testMessageAndCauseConstructor() {
        String errorMessage = "This is an out of scope exception.";
        Throwable cause = new Throwable("This is the cause of the out of scope exception.");
        OutOfScopeException exception = new OutOfScopeException(errorMessage, cause);
        assertEquals(errorMessage, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
    @Test
    public void testCauseConstructor() {
        Throwable cause = new Throwable("This is the cause of the out of scope exception.");
        OutOfScopeException exception = new OutOfScopeException(cause);
        assertEquals(cause.toString(), exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
