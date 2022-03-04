""" Custom rule to generate OSGi Manifest """

def _osgi_jar_impl(ctx):
    output = ctx.outputs.osgi_jar.path
    pom_version = ctx.var.get("pom_version", "LOCAL-SNAPSHOT")
    input_jar = ctx.attr.target[JavaInfo].outputs.jars[0].class_jar
    classpath_jars = ctx.attr.target[JavaInfo].compilation_info.compilation_classpath

    args = ctx.actions.args()
    args.add_joined("--classpath", classpath_jars, join_with = ":")
    args.add("--output_jar", output)
    args.add("--bundle_version", pom_version)
    args.add("--bundle_name", ctx.attr.bundle_name)
    args.add("--symbolic_name", ctx.attr.symbolic_name)
    if ctx.attr.fragment:
        args.add("--fragment")
    args.add_joined("--import_package", ctx.attr.import_package, join_with = ",")
    args.add_joined("--export_package", ctx.attr.export_package, join_with = ",")
    args.add("--input_jar", input_jar.path)

    ctx.actions.run(
        inputs = [input_jar] + classpath_jars.to_list(),
        executable = ctx.executable._osgi_wrapper_exe,
        arguments = [args],
        outputs = [ctx.outputs.osgi_jar],
        progress_message = "Generating OSGi bundle Manifest for %s" % ctx.attr.target.label,
    )

osgi_jar = rule(
    attrs = {
        "target": attr.label(),
        "export_package": attr.string_list(),
        "import_package": attr.string_list(),
        "bundle_name": attr.string(),
        "fragment": attr.bool(),
        "symbolic_name": attr.string(),
        "_osgi_wrapper_exe": attr.label(
            executable = True,
            cfg = "host",
            allow_files = True,
            default = Label("//tools:osgi_wrapper"),
        ),
    },
    fragments = ["java"],
    outputs = {
        "osgi_jar": "lib%{name}.jar",
    },
    implementation = _osgi_jar_impl,
)
