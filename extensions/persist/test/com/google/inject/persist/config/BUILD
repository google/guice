# Copyright 2011 Google Inc. All Rights Reserved.
# Author: sameb@google.com (Sam Berlin)

package(default_visibility = ["//:src"])

# A genrule to take everything under META-INF and move it the
# root of the classpath.
genrule(
    name = "metainf",
    srcs = glob(["META-INF/*"]),
    outs = ["jpa-manifest.jar"],
    cmd = "$(location @local_jdk//:jar) cvf $@ -C " +
          "extensions/persist/test/com/google/inject/persist/config META-INF",
    tools = ["@local_jdk//:jar"],
)
