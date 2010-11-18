/**
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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import junit.extensions.TestDecorator;
import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

/**
 * Builds a test suite whose tests are loaded in a private classloader and run them with a security
 * manager.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class StrictContainerTestSuiteBuilder {

  private final ClassLoader classLoader = new NonSystemClassLoader();
  private final SecurityManager securityManager;
  private final TestSuite testSuite = new TestSuite("StrictContainer");

  public StrictContainerTestSuiteBuilder(SecurityManager securityManager) {
    this.securityManager = securityManager;
  }

  public void add(String testClassName) {
    try {
      Class<?> testClass = classLoader.loadClass(testClassName);
      testSuite.addTest(securityManaged(new TestSuite(testClass)));
    } catch (Exception e) {
      testSuite.addTest(new SuiteConstructionError(testClassName, e));
    }
  }

  public void addSuite(String suiteClassname) {
    try {
      Class<?> suiteClass = classLoader.loadClass(suiteClassname);
      Test testSuite = (Test) suiteClass.getMethod("suite").invoke(null);
      this.testSuite.addTest(securityManaged(testSuite));
    } catch (Exception e) {
      testSuite.addTest(new SuiteConstructionError(suiteClassname, e));
    }
  }

  public TestSuite build() {
    return testSuite;
  }

  /**
   * A classloader that reloads everything outside of the JDK.
   */
  static class NonSystemClassLoader extends URLClassLoader {
    public NonSystemClassLoader() {
      super(new URL[0]);

      for (final String element : System.getProperty("java.class.path").split(File.pathSeparator)) {
        try {
          // is it a remote/local URL?
          addURL(new URL(element));
        } catch (MalformedURLException e1) {
          // nope - perhaps it's a filename?
          try {
            addURL(new File(element).toURI().toURL());
          } catch (MalformedURLException e2) {
            throw new RuntimeException(e1);
          }
        }
      }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      // check our local cache to avoid duplicates
      synchronized (this) {
        Class<?> clazz = findLoadedClass(name);
        if (clazz != null) {
          return clazz;
        }
      }

      if (name.startsWith("java.")
          || name.startsWith("javax.")
          || name.startsWith("junit.")
          || name.startsWith("sun.")
          || name.startsWith("com.sun.")
          || name.contains("cglib")) {
        return super.loadClass(name, resolve);
      }

      Class<?> clazz = findClass(name);
      if (resolve) {
        resolveClass(clazz);
      }
      return clazz;
    }
  }

  /**
   * Returns a test that sets up and tears down a security manager while it's run.
   */
  public Test securityManaged(Test test) {
    if (test instanceof TestSuite) {
      TestSuite suite = (TestSuite) test;
      TestSuite result = new TestSuite(suite.getName());

      @SuppressWarnings("unchecked") // a test suite's elements are tests
      Enumeration<Test> children = (Enumeration<Test>) suite.tests();
      for (Test child : Collections.list(children)) {
        result.addTest(securityManaged(child));
      }
      return result;

    } else {
      return new TestDecorator(test) {
        public void run(TestResult testResult) {
          SecurityManager originalSecurityManager = System.getSecurityManager();
          System.setSecurityManager(securityManager);
          try {
            basicRun(testResult);
          } finally {
            testResult.endTest(this);
            System.setSecurityManager(originalSecurityManager);
          }
        }
      };
    }
  }

  /**
   * A simple test that always fails with an exception.
   */
  private static class SuiteConstructionError implements Test {
    private String className;
    private final Exception cause;

    public SuiteConstructionError(String className, Exception cause) {
      this.className = className;
      this.cause = cause;
    }

    public void run(TestResult testResult) {
      testResult.addError(this, cause);
    }

    public int countTestCases() {
      return 1;
    }

    @Override public String toString() {
      return className;
    }
  }
}
