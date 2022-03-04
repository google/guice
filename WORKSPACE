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
    sha256 = "a8168646fbec5fe020b73ee6f2c0cd7480efb101f4d4c18d4cd703a56e4748ae",
    strip_prefix = "bazel-common-bf8e5ef95b118d1716b0cb4982cf15b6ed1c896f",
    urls = ["https://github.com/google/bazel-common/archive/bf8e5ef95b118d1716b0cb4982cf15b6ed1c896f.zip"],
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
        "com.google.errorprone:error_prone_annotations:2.0.18",
        "com.google.guava:guava:31.0.1-jre",
        "commons-logging:commons-logging:1.2",
        "javax.inject:javax.inject:1",
        "javax.persistence:javax.persistence-api:2.2",
        "javax.servlet:servlet-api:2.5",
        "org.apache.struts:struts2-core:2.3.37",
        "org.apache.struts.xwork:xwork-core:2.3.37",
        "org.ow2.asm:asm:9.2",
        "org.springframework:spring-core:5.3.14",
        "org.springframework:spring-beans:5.3.14",
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
            "31.0.1-jre",
            testonly = True,
        ),
        maven.artifact(
            "com.google.truth",
            "truth",
            "0.45",
            testonly = True,
        ),
        maven.artifact(
            "javax.inject",
            "javax.inject-tck",
            "1",
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
            "org.hibernate.javax.persistence",
            "hibernate-jpa-2.0-api",
            "1.0.0.Final",
            testonly = True,
        ),
        maven.artifact(
            "org.hibernate",
            "hibernate-core",
            "5.6.3.Final",
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
