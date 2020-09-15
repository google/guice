/*
 * Copyright (C) 2020 Apple Inc.
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

package com.google.inject.spi;

import com.google.inject.TypeLiteral;
import junit.framework.TestCase;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class NullableTypeAnnotationTest extends TestCase {

    @Retention(RUNTIME)
    @Target({TYPE_USE})
    public @interface Nullable {}

    public static class NullableConstructor {
        public NullableConstructor(@Nullable String param) {

        }
    }

    public void testTypeTargetedNullable() throws Exception {
        InjectionPoint ip = new InjectionPoint(TypeLiteral.get(NullableConstructor.class), NullableConstructor.class.getConstructor(String.class));
        assertTrue("TYPE_USE targeted @Nullable type annotation not detected", ip.getDependencies().get(0).isNullable());
    }
}
