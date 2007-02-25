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

package com.google.inject.tools.jmx;

import com.google.inject.Container;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Binding;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Provides a JMX interface to Guice.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Manager {

  /**
   * Registers the bindings for the container with the platform MBean server.
   * Consider using the name of your root {@link Module} class as the domain.
   */
  public static void manage(
      String domain,
      Container container) {
    manage(ManagementFactory.getPlatformMBeanServer(), domain, container);
  }

  /**
   * Registers the bindings for the container with the given MBean server.
   * Consider using the name of your root {@link Module} class as the domain.
   */
  public static void manage(MBeanServer server, String domain,
      Container container) {
    // Register each binding independently.
    for (Binding<?> binding : container.getBindings().values()) {
      // Construct the name manually so we can ensure proper ordering of the
      // key/value pairs.
      StringBuilder name = new StringBuilder();
      name.append(domain).append(":");
      Key<?> key = binding.getKey();
      name.append("type=").append(quote(key.getType().toString()));
      Annotation annotation = key.getAnnotation();
      if (annotation != null) {
        name.append(",annotation=").append(quote(annotation.toString()));
      }
      else {
        Class<? extends Annotation> annotationType = key.getAnnotationType();
        if (annotationType != null) {
          name.append(",annotation=")
              .append(quote("@" + annotationType.getName()));
        }
      }

      try {
        server.registerMBean(new ManagedBinding(binding),
            new ObjectName(name.toString()));
      }
      catch (MalformedObjectNameException e) {
        throw new RuntimeException("Bad object name: "
            + name.toString(), e);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  static String quote(String value) {
    // JMX seems to have a comma bug.
    return ObjectName.quote(value).replace(',', ';');
  }

  /**
   * Run with no arguments for usage instructions.
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: java -Dcom.sun.management.jmxremote "
          + Manager.class.getName() + " [module class name]");
      System.err.println("Then run 'jconsole' to connect.");
      System.exit(1);
    }

    Module module = (Module) Class.forName(args[0]).newInstance();
    Container container = Guice.createContainer(module);

    manage(args[0], container);

    System.out.println("Press Ctrl+C to exit...");

    // Sleep forever.
    Thread.sleep(Long.MAX_VALUE);
  }
}
