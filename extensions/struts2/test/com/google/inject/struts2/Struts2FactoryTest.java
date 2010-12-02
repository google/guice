/**
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.struts2;

import java.util.Date;

import junit.framework.TestCase;

import org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

/**
 * Test for Struts2Factory
 *
 * @author benmccann.com
 */
public class Struts2FactoryTest extends TestCase {

  private static final Date TODAY = new Date();
  
  public static class TestListener extends GuiceServletContextListener {

    private final Module module;
    
    public TestListener(Module module) {
      this.module = module;
    }
    
    @Override
    protected Injector getInjector() {
      return Guice.createInjector(
          new Struts2GuicePluginModule(),
          new ServletModule() {
            @Override
            protected void configureServlets() {      
              // Struts 2 setup
              bind(StrutsPrepareAndExecuteFilter.class).in(Singleton.class);
              filter("/*").through(StrutsPrepareAndExecuteFilter.class);
            }
          },
          module
      );
    }
    
  }
  
  public void testStruts2Factory() {
    Struts2Factory s2Factory = new Struts2Factory();
    TestListener testListener = new TestListener(new AbstractModule() {
      @Override
      protected void configure() {
      }

      @Provides @SuppressWarnings("unused")
      Date provideDate() {
        return TODAY;
      }
    });
    assertEquals(TODAY, testListener.getInjector().getInstance(Date.class));
    assertEquals(TODAY, s2Factory.buildBean(Date.class, null));
  }

}
