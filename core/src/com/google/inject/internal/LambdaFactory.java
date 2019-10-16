package com.google.inject.internal;

public final class LambdaFactory {
  private LambdaFactory() {}

  public static LambdaFactory create() {
    return new LambdaFactory();
  }
}
