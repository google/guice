package com.google.inject.tools;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Jar;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** Java binary that runs bndlib to analyze a jar file to generate OSGi bundle manifest. */
@Command(name = "osgi_wrapper")
public final class OsgiWrapper implements Callable<Integer> {
  private static final String REMOVEHEADERS =
      Arrays.stream(
              new String[] {
                "Embed-Dependency",
                "Embed-Transitive",
                "Built-By",
                "Tool",
                "Created-By",
                "Build-Jdk",
                "Originally-Created-By",
                "Archiver-Version",
                "Include-Resource",
                "Private-Package",
                "Ignore-Package",
                "Bnd-LastModified"
              })
          .collect(Collectors.joining(","));

  private static final String DESCRIPTION =
      "Guice is a lightweight dependency injection framework for Java 8 and above";

  private static final String COPYRIGHT = "Copyright (C) 2022 Google Inc.";

  private static final String DOC_URL = "https://github.com/google/guice";

  @Option(
      names = {"--input_jar"},
      description = "The jar file to wrap with OSGi metadata")
  private File inputJar;

  @Option(
      names = {"--output_jar"},
      description = "Output path to the wrapped jar")
  private File outputJar;

  @Option(
      names = {"--classpath"},
      description = "The classpath that contains dependencies of the input jar, separated with :")
  private String classpath;

  @Option(
      names = {"--bundle_name"},
      description = "The name of the bundle")
  private String bundleName;

  @Option(
      names = {"--import_package"},
      description = "The imported packages from this bundle")
  private String importPackage;

  @Option(
      names = {"--fragment"},
      description = "Whether this bundle is a fragment")
  private boolean fragment;

  @Option(
      names = {"--export_package"},
      description = "The exported packages from this bundle")
  private String exportPackage;

  @Option(
      names = {"--symbolic_name"},
      description = "The symbolic name of the bundle")
  private String symbolicName;

  @Option(
      names = {"--bundle_version"},
      description = "The version of the bundle")
  private String bundleVersion;

  @Override
  public Integer call() throws Exception {
    Analyzer analyzer = new Analyzer();
    Jar bin = new Jar(inputJar);

    analyzer.setJar(bin);
    analyzer.setProperty(Analyzer.BUNDLE_NAME, bundleName);
    analyzer.setProperty(Analyzer.BUNDLE_SYMBOLICNAME, symbolicName);
    analyzer.setProperty(Analyzer.BUNDLE_VERSION, bundleVersion);
    analyzer.setProperty(Analyzer.IMPORT_PACKAGE, importPackage);
    analyzer.setProperty(Analyzer.EXPORT_PACKAGE, exportPackage);
    if (fragment) {
      analyzer.setProperty(Analyzer.FRAGMENT_HOST, "com.google.inject");
    }
    analyzer.setProperty(Analyzer.NOUSES, "true");
    analyzer.setProperty(Analyzer.BUNDLE_DESCRIPTION, DESCRIPTION);
    analyzer.setProperty(Analyzer.BUNDLE_COPYRIGHT, COPYRIGHT);
    analyzer.setProperty(Analyzer.BUNDLE_DOCURL, DOC_URL);
    analyzer.setProperty(Analyzer.REMOVEHEADERS, REMOVEHEADERS);

    for (String dep : Arrays.asList(classpath.split(":"))) {
      analyzer.addClasspath(new File(dep));
    }

    analyzer.analyze();

    Manifest manifest = analyzer.calcManifest();

    if (analyzer.isOk()) {
      analyzer.getJar().setManifest(manifest);
      if (analyzer.save(outputJar, true)) {
        return 0;
      }
    }
    return 1;
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new OsgiWrapper()).execute(args);
    System.exit(exitCode);
  }
}
