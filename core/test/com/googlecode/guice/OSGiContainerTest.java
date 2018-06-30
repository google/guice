/*
 * Copyright (C) 2009 Google Inc.
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

package com.googlecode.guice;

import aQute.bnd.main.bnd;
import com.googlecode.guice.bundle.OSGiTestActivator;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.ServiceLoader;
import junit.framework.TestCase;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * Run various tests inside one or more OSGi containers.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public class OSGiContainerTest extends TestCase {

  // build properties passed from Ant
  static final String VERSION = System.getProperty("version", "snapshot");
  static final String BUILD_DIR = System.getProperty("build.dir", "build");

  static final String BUILD_DIST_DIR = BUILD_DIR + "/dist";
  static final String BUILD_TEST_DIR = BUILD_DIR + "/test";

  static final String GUICE_JAR = BUILD_DIST_DIR + "/guice-" + VERSION + ".jar";

  /*if[AOP]*/
  static final String AOPALLIANCE_JAR =
      System.getProperty("aopalliance.jar", "lib/aopalliance.jar");
  /*end[AOP]*/
  static final String JAVAX_INJECT_JAR =
      System.getProperty("javax.inject.jar", "lib/javax.inject.jar");
  static final String GUAVA_JAR = System.getProperty("guava.jar", "lib/guava-25.1-android.jar");

  // dynamically build test bundles
  @Override
  protected void setUp() throws Exception {

    // verify properties
    assertTrue(failMsg(), new File(BUILD_DIR).isDirectory());
    assertTrue(failMsg(), new File(GUICE_JAR).isFile());

    /*if[AOP]*/
    assertTrue(failMsg(), new File(AOPALLIANCE_JAR).isFile());
    /*end[AOP]*/
    assertTrue(failMsg(), new File(JAVAX_INJECT_JAR).isFile());
    assertTrue(failMsg(), new File(GUAVA_JAR).isFile());

    Properties instructions = new Properties();

    /*if[AOP]*/
    // aopalliance is an API bundle --> export the full API
    instructions.setProperty("Export-Package", "org.aopalliance.*");
    buildBundle("aopalliance", instructions, AOPALLIANCE_JAR);
    instructions.clear();
    /*end[AOP]*/

    // javax.inject is an API bundle --> export the full API
    instructions.setProperty("Export-Package", "javax.inject.*");
    buildBundle("javax.inject", instructions, JAVAX_INJECT_JAR);
    instructions.clear();

    // early versions of guava did not ship with OSGi metadata
    instructions.setProperty("Export-Package", "com.google.common.*");
    instructions.setProperty("Import-Package", "*;resolution:=optional");
    buildBundle("guava", instructions, GUAVA_JAR);
    instructions.clear();

    // strict imports to make sure test bundle only has access to these packages
    instructions.setProperty(
        "Import-Package",
        "org.osgi.framework,"
            /*if[AOP]*/
            + "org.aopalliance.intercept,"
            /*end[AOP]*/
            + "com.google.inject(|.binder|.matcher|.name)");

    // test bundle should only contain the local test classes, nothing else
    instructions.setProperty("Bundle-Activator", OSGiTestActivator.class.getName());
    instructions.setProperty("Private-Package", OSGiTestActivator.class.getPackage().getName());
    buildBundle("osgitests", instructions, BUILD_TEST_DIR);
    instructions.clear();
  }

  // build an OSGi bundle at runtime
  private static void buildBundle(String name, Properties instructions, String classpath)
      throws IOException {

    // write BND instructions to temporary test directory
    String bndFileName = BUILD_TEST_DIR + '/' + name + ".bnd";
    OutputStream os = new BufferedOutputStream(new FileOutputStream(bndFileName));
    instructions.store(os, "BND instructions");
    os.close();

    // assemble bundle, use -failok switch to avoid early exit
    bnd.main(new String[] {"-failok", "build", "-classpath", classpath, bndFileName});
  }

  private String failMsg() {
    return "This test may fail if it is not run from ant, or if it is not run after ant has "
        + "compiled & built jars. This is because the test is validating that the Guice jar "
        + "is properly setup to load in an OSGi container";
  }

  //This test may fail if it is not run from ant, or if it is not run after ant has
  //compiled & built jars. This is because the test is validating that the Guice jar
  //is properly setup to load in an OSGi container
  public void testGuiceWorksInOSGiContainer() throws Throwable {

    // ask framework to clear cache on startup
    Properties properties = new Properties();
    properties.setProperty("org.osgi.framework.storage", BUILD_TEST_DIR + "/bundle.cache");
    properties.setProperty("org.osgi.framework.storage.clean", "onFirstInit");

    // test each available OSGi framework in turn
    for (FrameworkFactory frameworkFactory : ServiceLoader.load(FrameworkFactory.class)) {
      Framework framework = frameworkFactory.newFramework(properties);

      framework.start();
      BundleContext systemContext = framework.getBundleContext();

      // load all the necessary bundles and start the OSGi test bundle
      /*if[AOP]*/
      systemContext.installBundle("reference:file:" + BUILD_TEST_DIR + "/aopalliance.jar");
      /*end[AOP]*/
      systemContext.installBundle("reference:file:" + BUILD_TEST_DIR + "/javax.inject.jar");
      systemContext.installBundle("reference:file:" + BUILD_TEST_DIR + "/guava.jar");
      systemContext.installBundle("reference:file:" + GUICE_JAR);
      systemContext.installBundle("reference:file:" + BUILD_TEST_DIR + "/osgitests.jar").start();

      framework.stop();
    }
  }
}
