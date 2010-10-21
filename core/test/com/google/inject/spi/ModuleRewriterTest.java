/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.internal.util.Lists;
import com.google.inject.name.Names;
import java.util.List;
import junit.framework.TestCase;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ModuleRewriterTest extends TestCase {

  public void testRewriteBindings() {
    // create a module the binds String.class and CharSequence.class
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("Pizza");
        bind(CharSequence.class).toInstance("Wine");
      }
    };

    // record the elements from that module
    List<Element> elements = Elements.getElements(module);

    // create a rewriter that rewrites the binding to 'Wine' with a binding to 'Beer'
    List<Element> rewritten = Lists.newArrayList();
    for (Element element : elements) {
      element = element.acceptVisitor(new DefaultElementVisitor<Element>() {
        @Override public <T> Element visit(Binding<T> binding) {
          T target = binding.acceptTargetVisitor(Elements.<T>getInstanceVisitor());
          if ("Wine".equals(target)) {
            return null;
          }
          else {
            return binding;
          }
        }
      });
      if (element != null) {
        rewritten.add(element);
      }
    }

    // create a module from the original list of elements and the rewriter
    Module rewrittenModule = Elements.getModule(rewritten);

    // the wine binding is dropped
    Injector injector = Guice.createInjector(rewrittenModule);
    try {
      injector.getInstance(CharSequence.class);
      fail();
    } catch (ConfigurationException expected) {
    }
  }

  public void testGetProviderAvailableAtInjectMembersTime() {
    Module module = new AbstractModule() {
      public void configure() {
        final Provider<String> stringProvider = getProvider(String.class);

        bind(String.class).annotatedWith(Names.named("2")).toProvider(new Provider<String>() {
          private String value;

          @Inject void initialize() {
            value = stringProvider.get();
          }

          public String get() {
            return value;
          }
        });

        bind(String.class).toInstance("A");
      }
    };

    // the module works fine normally
    Injector injector = Guice.createInjector(module);
    assertEquals("A", injector.getInstance(Key.get(String.class, Names.named("2"))));

    // and it should also work fine if we rewrite it
    List<Element> elements = Elements.getElements(module);
    Module replayed = Elements.getModule(elements);
    Injector replayedInjector = Guice.createInjector(replayed);
    assertEquals("A", replayedInjector.getInstance(Key.get(String.class, Names.named("2"))));
  }
}
