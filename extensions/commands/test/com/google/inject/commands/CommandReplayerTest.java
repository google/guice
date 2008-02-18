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

package com.google.inject.commands;

import com.google.inject.Module;

import java.util.List;


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
}
