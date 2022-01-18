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

def guice_test_suites(name, deps, srcs = None, args = [], suffix = "", sizes = None):
    """
    Generates tests for test file in srcs ending in "Test.java"

    Args:
      name: name of the test suite to generate
      srcs: list of test source files, uses 'glob(["**/*Test.java"])' if not specified
      deps: list of runtime dependencies requried to run the test
      args: list of flags to pass to the test
      suffix: suffix to apend to the generated test name
      sizes: not used, exists only so that the opensource guice_test_suites mirror exactly the internal one
    """
    test_files = srcs or native.glob(["**/*Test.java"])
    jvm_flags = []

    # transform flags to JVM options used externally
    for arg in args:
        if arg.startswith("--"):
            jvm_flags.append(arg.replace("--", "-D"))
        else:
            jvm_flags.append(arg)

    package_name = native.package_name()

    # strip the path prefix from package name so that we get the correct test class name
    # "core/test/com/google/inject" becomes "com/google/inject"
    # "extensions/service/test/com/google/inject/service" becomes "com/google/inject/service"
    if package_name.startswith("core/test/") or package_name.startswith("extensions/"):
        package_name = package_name.rpartition("/test/")[2]

    test_names = []
    for test_file in test_files:
        name = test_file.replace(".java", "")
        test_name = name + suffix
        test_names.append(test_name)
        test_class = (package_name + "/" + name).replace("/", ".")

        native.java_test(
            name = test_name,
            test_class = test_class,
            runtime_deps = deps,
            jvm_flags = jvm_flags,
        )

    native.test_suite(
        name = name + suffix + "_suite",
        tests = test_names,
    )
