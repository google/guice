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

import com.google.inject.Module;
import com.google.inject.Binder;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ExecutingVisitorTest extends VisitableBinderTest {

  protected void checkModule(Module module, BinderVisitor<?>... visitors) {
    // record the commands
    VisitableBinder original = new VisitableBinder(earlyRequestProvider);
    module.configure(original);

    // test executing visitor by executing the recorded commands on a second binder
    final VisitableBinder copy = new VisitableBinder(earlyRequestProvider);
    ExecutingVisitor copyingVisitor = new ExecutingVisitor() {
      public Binder binder() {
        return copy;
      }
    };
    for (Command command : original.getCommands()) {
      command.acceptVisitor(copyingVisitor);
    }

    // verify that the second binder is consistent with expectations
    assertEquals(copy.getCommands().size(), visitors.length);
    for (int i = 0; i < visitors.length; i++) {
      BinderVisitor<?> visitor = visitors[i];
      Command command = copy.getCommands().get(i);
      command.acceptVisitor(visitor);
    }
  }
}
