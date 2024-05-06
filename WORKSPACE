load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "4.2"

RULES_JVM_EXTERNAL_SHA = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven")

http_archive(
    name = "google_bazel_common",
    sha256 = "cba2aff0fb5e64dae880c8e1ead1b8d414a12b8e924315fac1a067de78a65e81",
    strip_prefix = "bazel-common-d59d067c04e973f3c4aa34f6628bed97d6664c3c",
    urls = ["https://github.com/google/bazel-common/archive/d59d067c04e973f3c4aa34f6628bed97d6664c3c.zip"],
)

load("@google_bazel_common//:workspace_defs.bzl", "google_common_workspace_rules")

google_common_workspace_rules()

maven_install(
    artifacts = [
        "aopalliance:aopalliance:1.0",
        "com.google.auto.value:auto-value:1.6.3",
        "com.google.code.findbugs:jsr305:3.0.1",
        "com.google.dagger:dagger:2.22.1",
        "com.google.dagger:dagger-producers:2.22.1",
        "com.google.errorprone:error_prone_annotations:2.18.0",
        "com.google.guava:guava:33.0.0-jre",
        "commons-logging:commons-logging:1.2",
        "jakarta.inject:jakarta.inject-api:2.0.1",
        "jakarta.persistence:jakarta.persistence-api:3.0.0",
        "jakarta.servlet:jakarta.servlet-api:5.0.0",
        "org.apache.struts:struts2-core:2.5.31",
        "org.apache.struts.xwork:xwork-core:2.3.37",
        "org.ow2.asm:asm:9.5",
        "org.springframework:spring-core:5.3.18",
        "org.springframework:spring-beans:5.3.18",
        "biz.aQute.bnd:bndlib:2.4.0",
        "info.picocli:picocli:4.6.3",
        maven.artifact(
            "biz.aQute",
            "bnd",
            "0.0.384",
            testonly = True,
        ),
        maven.artifact(
            "com.google.guava",
            "guava-testlib",
            "33.0.0-jre",
            testonly = True,
        ),
        maven.artifact(
            "com.google.truth",
            "truth",
            "1.4.0",
            testonly = True,
        ),
        maven.artifact(
            "com.google.truth.extensions",
            "truth-java8-extension",
            "1.4.0",
            testonly = True,
        ),
        maven.artifact(
            "jakarta.inject",
            "jakarta.inject-tck",
            "2.0.1",
            testonly = True,
        ),
        maven.artifact(
            "junit",
            "junit",
            "4.13.2",
            testonly = True,
        ),
        maven.artifact(
            "org.apache.felix",
            "org.apache.felix.framework",
            "3.0.5",
            testonly = True,
        ),
        maven.artifact(
            "org.easymock",
            "easymock",
            "3.1",
            testonly = True,
        ),
        maven.artifact(
            "org.hamcrest",
            "hamcrest",
            "2.2",
            testonly = True,
        ),
        maven.artifact(
            "org.hibernate",
            "hibernate-core-jakarta",
            "5.6.15.Final",
            testonly = True,
        ),
        maven.artifact(
            "org.hsqldb",
            "hsqldb-j5",
            "2.0.0",
            testonly = True,
        ),
        maven.artifact(
            "org.mockito",
            "mockito-core",
            "4.2.0",
            testonly = True,
        ),
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)
