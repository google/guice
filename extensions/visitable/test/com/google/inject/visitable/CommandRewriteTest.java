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

package com.google.inject.visitable;

import com.google.inject.*;
import junit.framework.TestCase;

import java.util.List;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandRewriteTest extends TestCase {

  public void testRewriteBindings() {
    // create a module the binds String.class and CharSequence.class
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("Pizza");
        bind(CharSequence.class).toInstance("Wine");
      }
    };

    // record the commands from that module
    CommandRecorder commandRecorder = new CommandRecorder(new FutureInjector());
    List<Command> commands = commandRecorder.recordCommands(module);

    // create a rewriter that rewrites the binding to 'Wine' with a binding to 'Beer'
    CommandReplayer rewriter = new CommandReplayer() {
      @Override public <T> void replayBind(Binder binder, BindCommand<T> command) {
        if ("Wine".equals(command.getTarget().get(null))) {
          binder.bind(CharSequence.class).toInstance("Beer");
        } else {
          super.replayBind(binder, command);
        }
      }
    };

    // create a module from the original list of commands and the rewriter
    Module rewrittenModule = rewriter.createModule(commands);

    // it all works
    Injector injector = Guice.createInjector(rewrittenModule);
    assertEquals("Pizza", injector.getInstance(String.class));
    assertEquals("Beer", injector.getInstance(CharSequence.class));
  }
}
