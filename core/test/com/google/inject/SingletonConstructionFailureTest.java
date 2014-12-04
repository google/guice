package com.google.inject;

import org.junit.Assert;
import org.junit.Test;

public class SingletonConstructionFailureTest {
    @Test
    public void testGuiceCreatesSingletonTwice() throws Exception {
        try {
            Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
                @Override
                protected void configure() {
                    bind (Boom.class).in(Scopes.SINGLETON);
                    bind (Object.class).to(Boom.class);
                }
            }).getInstance(Boom.class);
        } catch (CreationException expected) {
            Assert.assertEquals(1, expected.getErrorMessages().size());
        }
        Assert.assertEquals(1, Boom.nCalls);
    }

    public static class Boom {
        static int nCalls = 0;
        public Boom() {
            nCalls++;
            throw new IllegalArgumentException("kaboom!");
        }
    }
}
