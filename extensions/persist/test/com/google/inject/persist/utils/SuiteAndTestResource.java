package com.google.inject.persist.utils;

import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

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
