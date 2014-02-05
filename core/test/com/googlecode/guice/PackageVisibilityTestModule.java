package com.googlecode.guice;

import com.google.inject.Inject;
import com.google.inject.config.AbstractModule;

public class PackageVisibilityTestModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(PackagePrivateInterface.class).to(PackagePrivateImpl.class);
  }

  public static class PublicUserOfPackagePrivate {
    @Inject public PublicUserOfPackagePrivate(PackagePrivateInterface ppi) {}
    @Inject public void acceptPackagePrivateParameter(PackagePrivateInterface ppi) {}
  }

  interface PackagePrivateInterface {}

  static class PackagePrivateImpl implements PackagePrivateInterface {}
}
