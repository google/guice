/**
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

package com.google.inject.util;

import net.sf.cglib.reflect.FastClass;
import com.google.inject.util.GuiceNamingPolicy;

/**
 * Gives Guice classes custom names.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class GuiceFastClass {

  public static FastClass create(Class type) {
    return create(type.getClassLoader(), type);
  }

  public static FastClass create(ClassLoader loader, Class type) {
    FastClass.Generator generator = new FastClass.Generator();
    generator.setType(type);
    generator.setClassLoader(loader);
    generator.setNamingPolicy(new GuiceNamingPolicy());
    return generator.create();
  }
}
