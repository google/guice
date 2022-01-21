# Copyright (C) 2022 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""starlark marcors to generate test suites."""

_TEMPLATE = """package {VAR_PACKAGE};
import org.junit.runners.Suite;
import org.junit.runner.RunWith;

@RunWith(Suite.class)
@Suite.SuiteClasses({{{VAR_CLASSES}}})
public class {VAR_NAME} {{}}
"""

def _impl(ctx):
    classes = ",".join(sorted(ctx.attr.test_classes))

    ctx.actions.write(
        output = ctx.outputs.out,
        content = _TEMPLATE.format(
            VAR_PACKAGE = ctx.attr.package_name,
            VAR_CLASSES = classes,
            VAR_NAME = ctx.attr.name,
        ),
    )

_gen_suite = rule(
    attrs = {
        "test_classes": attr.string_list(),
        "package_name": attr.string(),
    },
    outputs = {"out": "%{name}.java"},
    implementation = _impl,
)

def guice_test_suites(name, deps, srcs = None, args = [], suffix = "", sizes = None, jvm_flags = []):
    """
    Generates tests for test file in srcs ending in "Test.java"

    Args:
      name: name of the test suite to generate
      srcs: list of test source files, uses 'glob(["**/*Test.java"])' if not specified
      deps: list of runtime dependencies requried to run the test
      args: list of flags to pass to the test
      jvm_flags: list of JVM flags to pass to the test
      suffix: suffix to apend to the generated test name
      sizes: not used, exists only so that the opensource guice_test_suites mirror exactly the internal one
    """

    flags = []
    flags.extend(jvm_flags)

    # transform flags to JVM options used externally
    for arg in args:
        if arg.startswith("--"):
            flags.append(arg.replace("--", "-D"))
        else:
            flags.append(arg)

    package_name = native.package_name()

    # strip the path prefix from package name so that we get the correct test class name
    # "core/test/com/google/inject" becomes "com/google/inject"
    # "extensions/service/test/com/google/inject/service" becomes "com/google/inject/service"
    if package_name.startswith("core/test/") or package_name.startswith("extensions/"):
        package_name = package_name.rpartition("/test/")[2]

    test_files = srcs or native.glob(["**/*Test.java"])
    test_classes = []
    for src in test_files:
        test_name = src.replace(".java", "")
        test_classes.append((package_name + "/" + test_name + ".class").replace("/", "."))

    suite_name = name + suffix
    _gen_suite(
        name = suite_name,
        test_classes = test_classes,
        package_name = package_name.replace("/", "."),
    )

    native.java_test(
        name = "AllTestsSuite" + suffix,
        test_class = (package_name + "/" + suite_name).replace("/", "."),
        jvm_flags = flags,
        srcs = [":" + suite_name],
        deps = deps + [
            "//third_party/java/junit",
        ],
    )
