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

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Executes commands against a binder.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandReplayer {

  /**
   * Returns a module that executes the specified commands
   * using this executing visitor.
   */
  public final Module createModule(final Iterable<Command> commands) {
    return new Module() {
      public void configure(Binder binder) {
        replay(binder, commands);
      }
    };
  }

  /**
   * Replays {@code commands} against {@code binder}.
   */
  public final void replay(final Binder binder, Iterable<Command> commands) {
    checkNotNull(binder, "binder");
    checkNotNull(commands, "commands");

    Command.Visitor<Void> visitor = new Command.Visitor<Void>() {
      public Void visitAddMessage(AddMessageCommand command) {
        replayAddMessageError(binder, command);
        return null;
      }

      public Void visitBindInterceptor(BindInterceptorCommand command) {
        replayBindInterceptor(binder, command);
        return null;
      }

      public Void visitBindScope(BindScopeCommand command) {
        replayBindScope(binder, command);
        return null;
      }

      public Void visitRequestInjection(RequestInjectionCommand command) {
        replayRequestInjection(binder, command);
        return null;
      }

      public Void visitRequestStaticInjection(RequestStaticInjectionCommand command) {
        replayRequestStaticInjection(binder, command);
        return null;
      }

      public Void visitBindConstant(BindConstantCommand command) {
        replayBindConstant(binder, command);
        return null;
      }

      public Void visitConvertToTypes(ConvertToTypesCommand command) {
        replayConvertToTypes(binder, command);
        return null;
      }

      public <T> Void visitBind(BindCommand<T> command) {
        replayBind(binder, command);
        return null;
      }

      public <T> Void visitGetProvider(GetProviderCommand<T> command) {
        replayGetProvider(binder, command);
        return null;
      }
    };

    for (Command command : commands) {
      command.acceptVisitor(visitor);
    }
  }

  public void replayAddMessageError(final Binder binder, final AddMessageCommand command) {
    binder.withSource(command.getSource()).addError(command.getMessage());
  }

  public void replayBindInterceptor(final Binder binder, final BindInterceptorCommand command) {
    List<MethodInterceptor> interceptors = command.getInterceptors();
    binder.withSource(command.getSource()).bindInterceptor(
        command.getClassMatcher(), command.getMethodMatcher(),
        interceptors.toArray(new MethodInterceptor[interceptors.size()]));
  }

  public void replayBindScope(final Binder binder, final BindScopeCommand command) {
    binder.withSource(command.getSource()).bindScope(
        command.getAnnotationType(), command.getScope());
  }

  public void replayRequestInjection(final Binder binder,
      final RequestInjectionCommand command) {
    List<Object> objects = command.getInstances();
    binder.withSource(command.getSource())
        .requestInjection(objects.toArray());
  }

  public void replayRequestStaticInjection(final Binder binder,
      final RequestStaticInjectionCommand command) {
    List<Class> types = command.getTypes();
    binder.withSource(command.getSource())
        .requestStaticInjection(types.toArray(new Class[types.size()]));
  }

  public void replayBindConstant(final Binder binder, final BindConstantCommand command) {
    AnnotatedConstantBindingBuilder constantBindingBuilder
        = binder.withSource(command.getSource()).bindConstant();

    Key<Object> key = command.getKey();
    ConstantBindingBuilder builder = key.getAnnotation() != null
        ? constantBindingBuilder.annotatedWith(key.getAnnotation())
        : constantBindingBuilder.annotatedWith(key.getAnnotationType());

    command.getTarget().execute(builder);
  }

  public void replayConvertToTypes(final Binder binder, final ConvertToTypesCommand command) {
    binder.withSource(command.getSource())
        .convertToTypes(command.getTypeMatcher(), command.getTypeConverter());
  }

  public <T> void replayBind(final Binder binder, final BindCommand<T> command) {
    LinkedBindingBuilder<T> lbb = binder.withSource(command.getSource()).bind(command.getKey());

    BindTarget<T> bindTarget = command.getTarget();
    ScopedBindingBuilder sbb = bindTarget != null
        ? bindTarget.execute(lbb)
        : lbb;

    BindScoping scoping = command.getScoping();
    if (scoping != null) {
      scoping.execute(sbb);
    }
  }

  public <T> void replayGetProvider(final Binder binder, final GetProviderCommand<T> command) {
    binder.withSource(command.getSource()).getProvider(command.getKey());
  }
}
