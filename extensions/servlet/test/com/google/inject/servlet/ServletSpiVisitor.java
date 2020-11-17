/*
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

package com.google.inject.servlet;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import junit.framework.AssertionFailedError;

/**
 * A visitor for testing the servlet SPI extension.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class ServletSpiVisitor extends DefaultBindingTargetVisitor<Object, Integer>
    implements ServletModuleTargetVisitor<Object, Integer> {

  int otherCount = 0;
  int currentCount = 0;
  List<Params> actual = Lists.newArrayList();

  /* The set of classes that are allowed to be "other" bindings. */
  Set<Class<?>> allowedClasses;

  ServletSpiVisitor(boolean forInjector) {
    ImmutableSet.Builder<Class<?>> builder = ImmutableSet.builder();
    // always ignore these things...
    builder.add(
        ServletRequest.class,
        ServletResponse.class,
        ManagedFilterPipeline.class,
        ManagedServletPipeline.class,
        FilterPipeline.class,
        ServletContext.class,
        HttpServletRequest.class,
        Filter.class,
        HttpServletResponse.class,
        HttpSession.class,
        Map.class,
        HttpServlet.class,
        InternalServletModule.BackwardsCompatibleServletContextProvider.class,
        GuiceFilter.class);
    if (forInjector) {
      // only ignore these if this is for the live injector, any other time it'd be an error!
      builder.add(Injector.class, Stage.class, Logger.class);
    }
    this.allowedClasses = builder.build();
  }

  @Override
  public Integer visit(InstanceFilterBinding binding) {
    actual.add(new Params(binding, binding.getFilterInstance()));
    return currentCount++;
  }

  @Override
  public Integer visit(InstanceServletBinding binding) {
    actual.add(new Params(binding, binding.getServletInstance()));
    return currentCount++;
  }

  @Override
  public Integer visit(LinkedFilterBinding binding) {
    actual.add(new Params(binding, binding.getLinkedKey()));
    return currentCount++;
  }

  @Override
  public Integer visit(LinkedServletBinding binding) {
    actual.add(new Params(binding, binding.getLinkedKey()));
    return currentCount++;
  }

  @Override
  protected Integer visitOther(Binding<? extends Object> binding) {
    if (!allowedClasses.contains(binding.getKey().getTypeLiteral().getRawType())) {
      throw new AssertionFailedError("invalid other binding: " + binding);
    }
    otherCount++;
    return currentCount++;
  }

  static class Params {
    private final String pattern;
    private final Object keyOrInstance;
    private final Map<String, String> params;
    private final UriPatternType patternType;

    Params(ServletModuleBinding binding, Object keyOrInstance) {
      this.pattern = binding.getPattern();
      this.keyOrInstance = keyOrInstance;
      this.params = binding.getInitParams();
      this.patternType = binding.getUriPatternType();
    }

    Params(
        String pattern,
        Object keyOrInstance,
        Map<String, String> params,
        UriPatternType patternType) {
      this.pattern = pattern;
      this.keyOrInstance = keyOrInstance;
      this.params = params;
      this.patternType = patternType;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Params) {
        Params o = (Params) obj;
        return Objects.equal(pattern, o.pattern)
            && Objects.equal(keyOrInstance, o.keyOrInstance)
            && Objects.equal(params, o.params)
            && Objects.equal(patternType, o.patternType);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(pattern, keyOrInstance, params, patternType);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(Params.class)
          .add("pattern", pattern)
          .add("keyOrInstance", keyOrInstance)
          .add("initParams", params)
          .add("patternType", patternType)
          .toString();
    }
  }
}
