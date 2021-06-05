/*
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;


import junit.framework.TestCase;

/**
 * @author 11712617@mail.sustech.edu.cn (Xinhao Xiang)
 */
public class SingletonTest extends TestCase {

    static final long DEADLOCK_TIMEOUT_SECONDS = 1;

    private final AbstractModule singletonsModule =
            new AbstractModule() {
                @Override
                protected void configure() {
                    bind(ScopesTest.AnnotatedSingleton.class);
                    bind(AnnotatedEagerSingleton.class);
                }
            };

    @Override
    protected void setUp() throws Exception {
        ScopesTest.AnnotatedSingleton.nextInstanceId = 0;
        AnnotatedEagerSingleton.nextInstanceId = 0;
    }

    public void testSingletonsInProductionStage() {
        Guice.createInjector(Stage.PRODUCTION, singletonsModule);
        assertEquals(1, ScopesTest.AnnotatedSingleton.nextInstanceId);
    }

    public void testSingletonsInDevelopmentStage() {
        Guice.createInjector(Stage.DEVELOPMENT, singletonsModule);
        assertEquals(0, ScopesTest.AnnotatedSingleton.nextInstanceId);
    }

    public void testStageEagerSingletonInDevelopmentStage() {
        Guice.createInjector(Stage.DEVELOPMENT, singletonsModule);
        assertEquals(1, AnnotatedEagerSingleton.nextInstanceId);
    }

    @EagerSingleton
    static class AnnotatedEagerSingleton {
        static int nextInstanceId;
        final int instanceId = nextInstanceId++;
    }
}
