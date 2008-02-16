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

import java.util.List;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandReplayerTest extends CommandRecorderTest {

  protected void checkModule(Module module, Command.Visitor<?>... visitors) {
    // get some commands to replay
    CommandRecorder recorder = new CommandRecorder(earlyRequestProvider);
    recorder.recordCommands(module);
    List<Command> commands = recorder.getCommands();

    // replay the recorded commands, and record them again!
    recorder = new CommandRecorder(earlyRequestProvider);
    CommandReplayer copyingVisitor = new CommandReplayer();
    copyingVisitor.replay(recorder.getBinder(), commands);

    // verify that the replayed commands are as expected
    List<Command> replayedCommands = recorder.getCommands();
    assertEquals(replayedCommands.size(), visitors.length);
    for (int i = 0; i < visitors.length; i++) {
      Command.Visitor<?> visitor = visitors[i];
      Command command = replayedCommands.get(i);
      command.acceptVisitor(visitor);
    }
  }
}
