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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.Objects;
import org.aopalliance.intercept.MethodInterceptor;

import java.util.List;

/**
 * Executes commands against a binder.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandReplayer implements Command.Visitor<Void> {
  private Binder binder;

  /**
   * Returns the current binder in use.
   */
  public Binder getBinder() {
    if (binder == null) {
      throw new IllegalStateException("No binder exists at this time!");
    }

    return binder;
  }

  /**
   * Returns a module that executes the specified commands
   * using this executing visitor.
   */
  public Module createModule(final Iterable<Command> commands) {
    return new Module() {
      public void configure(Binder binder) {
        replay(binder, commands);
      }
    };
  }

  /**
   * Replays {@code commands} against {@code binder}.
   */
  public void replay(Binder binder, Iterable<Command> commands) {
    Objects.nonNull(binder, "binder");
    Objects.nonNull(commands, "commands");

    Binder oldBinder = this.binder;
    this.binder = binder;
    try {
      for (Command command : commands) {
        command.acceptVisitor(this);
      }
    } finally {
      this.binder = oldBinder;
    }
  }

  public Void visitAddMessageError(AddMessageErrorCommand command) {
    getBinder().addError(command.getMessage(), command.getArguments().toArray());
    return null;
  }

  public Void visitAddError(AddThrowableErrorCommand command) {
    getBinder().addError(command.getThrowable());
    return null;
  }

  public Void visitBindInterceptor(BindInterceptorCommand command) {
    List<MethodInterceptor> interceptors = command.getInterceptors();
    getBinder().bindInterceptor(command.getClassMatcher(), command.getMethodMatcher(),
        interceptors.toArray(new MethodInterceptor[interceptors.size()]));
    return null;
  }

  public Void visitBindScope(BindScopeCommand command) {
    getBinder().bindScope(command.getAnnotationType(), command.getScope());
    return null;
  }

  public Void visitRequestStaticInjection(RequestStaticInjectionCommand command) {
    List<Class> types = command.getTypes();
    getBinder().requestStaticInjection(types.toArray(new Class[types.size()]));
    return null;
  }

  public Void visitConstantBinding(BindConstantCommand command) {
    AnnotatedConstantBindingBuilder constantBindingBuilder = getBinder().bindConstant();

    Key<Object> key = command.getKey();
    ConstantBindingBuilder builder = key.getAnnotation() != null
        ? constantBindingBuilder.annotatedWith(key.getAnnotation())
        : constantBindingBuilder.annotatedWith(key.getAnnotationType());

    command.getTarget().execute(builder);
    return null;
  }

  public Void visitConvertToTypes(ConvertToTypesCommand command) {
    getBinder().convertToTypes(command.getTypeMatcher(), command.getTypeConverter());
    return null;
  }

  public <T> Void visitBinding(BindCommand<T> command) {
    LinkedBindingBuilder<T> lbb = getBinder().bind(command.getKey());

    BindTarget<T> bindTarget = command.getTarget();
    ScopedBindingBuilder sbb = bindTarget != null
        ? bindTarget.execute(lbb)
        : lbb;

    BindScoping scoping = command.getScoping();
    if (scoping != null) {
      scoping.execute(sbb);
    }

    return null;
  }

  public <T> Void visitGetProviderCommand(GetProviderCommand<T> command) {
    getBinder().getProvider(command.getKey());
    return null;
  }
}
