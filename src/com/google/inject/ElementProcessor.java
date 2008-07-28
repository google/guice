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

import com.google.inject.internal.Errors;
import com.google.inject.spi.BindConstant;
import com.google.inject.spi.BindInterceptor;
import com.google.inject.spi.BindScope;
import com.google.inject.spi.ConvertToTypes;
import com.google.inject.spi.Element;
import com.google.inject.spi.GetProvider;
import com.google.inject.spi.Message;
import com.google.inject.spi.RequestInjection;
import com.google.inject.spi.RequestStaticInjection;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract base class for creating an injector from module elements.
 *
 * <p>Extending classes must return {@code true} from any overridden
 * {@code visit*()} methods, in order for the element processor to remove the
 * handled element.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
abstract class ElementProcessor implements Element.Visitor<Boolean> {

  protected Errors errors;

  protected ElementProcessor(Errors errors) {
    this.errors = errors;
  }

  public void processCommands(List<Element> elements) {
    Errors errorsAnyElement = this.errors;
    try {
      for (Iterator<Element> i = elements.iterator(); i.hasNext(); ) {
        Element element = i.next();
        this.errors = errorsAnyElement.withSource(element.getSource());
        Boolean allDone = element.acceptVisitor(this);
        if (allDone) {
          i.remove();
        }
      }
    } finally {
      this.errors = errorsAnyElement;
    }
  }

  public Boolean visitMessage(Message message) {
    return false;
  }

  public Boolean visitBindInterceptor(BindInterceptor bindInterceptor) {
    return false;
  }

  public Boolean visitBindScope(BindScope bindScope) {
    return false;
  }

  public Boolean visitRequestInjection(RequestInjection requestInjection) {
    return false;
  }

  public Boolean visitRequestStaticInjection(RequestStaticInjection requestStaticInjection) {
    return false;
  }

  public Boolean visitBindConstant(BindConstant bindConstant) {
    return false;
  }

  public Boolean visitConvertToTypes(ConvertToTypes convertToTypes) {
    return false;
  }

  public <T> Boolean visitBinding(Binding<T> binding) {
    return false;
  }

  public <T> Boolean visitGetProvider(GetProvider<T> getProvider) {
    return false;
  }
}
