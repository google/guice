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
import com.google.inject.AbstractModule;

import java.util.List;
import java.util.ConcurrentModificationException;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandReplayerTest extends CommandRecorderTest {

  protected void checkModule(Module module, Command.Visitor<?>... visitors) {
    // get some commands to replay
    List<Command> commands = new CommandRecorder(earlyRequestProvider).recordCommands(module);

    // replay the recorded commands, and record them again!
    List<Command> replayedCommands = new CommandRecorder(earlyRequestProvider)
        .recordCommands(new CommandReplayer().createModule(commands));

    // verify that the replayed commands are as expected
    assertEquals(replayedCommands.size(), visitors.length);
    for (int i = 0; i < visitors.length; i++) {
      Command.Visitor<?> visitor = visitors[i];
      Command command = replayedCommands.get(i);
      command.acceptVisitor(visitor);
    }
  }

  /**
   * CommandReplayer can only replay a single module concurrently due to the
   * limit of one binder at a time. This test ensures that CommandReplayer
   * fails if two binders are being used concurrently.
   */
  public void testConcurrentUse() {
    final List<Command> commands = new CommandRecorder(earlyRequestProvider).recordCommands(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
          }
        }
    );

    CommandReplayer replayer = new CommandReplayer() {
      @Override public <T> Void visitBind(BindCommand<T> command) {
        try {
          new CommandRecorder(earlyRequestProvider).recordCommands(createModule(commands));
          fail();
        } catch(ConcurrentModificationException expected) {
        }
        return super.visitBind(command);
      }
    };

    new CommandRecorder(earlyRequestProvider)
        .recordCommands(replayer.createModule(commands));
  }
}
