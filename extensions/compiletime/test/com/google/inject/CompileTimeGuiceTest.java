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


package com.google.inject;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CompileTimeGuiceTest extends TestCase {

  private Module module = new AbstractModule() {
    protected void configure() {
      bind(Foo.class).to(RealFoo.class);
      bind(Bar.class).toInstance(new Bar() {});
      bind(List.class).to(ArrayList.class);
    }
  };

  public interface Bar { }
  public interface Foo { }
  public static class RealFoo implements Foo {
    @Inject public RealFoo(Bar bar) { }
  }

  public void test() {
    Injector injector = new CompileTimeGuice("test", Collections.singleton(module))
        .createInjector();
    
    assertTrue(injector.getInstance(Foo.class) instanceof RealFoo);
  }

  public void generateCode() throws IOException {
    new CompileTimeGuice("test", Collections.singleton(module))
        .generateCode(new File("/Users/jessewilson/svn/google-guice/generatedsrc"));

  }

  public static void main(String[] args) throws IOException {
    new CompileTimeGuiceTest().generateCode();
  }
}
