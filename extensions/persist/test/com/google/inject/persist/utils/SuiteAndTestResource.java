/*
 * Copyright (C) 2010 Google, Inc.
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
package com.google.inject.persist.utils;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 *
 * @author Krzysztof Sierszeń (krzysztof.sierszen@digitalnewagency.com)
 */
public abstract class SuiteAndTestResource extends ExternalResource {

  public enum Lifecycle {
    SUITE,
    TEST
  }

  private boolean isSuite;

  private final Lifecycle lifecycle;

  protected SuiteAndTestResource(Lifecycle lifecycle) {
    this.lifecycle = lifecycle;
  }

  @Override
  protected void before() throws Throwable {
    if (isSuite) {
      beforeSuite();
    } else {
      if (Lifecycle.TEST.equals(lifecycle)) {
        beforeSuite();
      }
      beforeTest();
    }
  }

  protected abstract void beforeTest();

  protected abstract void beforeSuite();

  @Override
  protected void after() {
    if (isSuite) {
      afterSuite();
    } else {
      afterTest();
      if (Lifecycle.TEST.equals(lifecycle)) {
        afterSuite();
      }
    }
  }

  protected abstract void afterTest();

  protected abstract void afterSuite();

  @Override
  public Statement apply(Statement base, Description description) {
    isSuite = description.isSuite();
    return super.apply(base, description);
  }
}
