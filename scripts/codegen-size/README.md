# Codegen size

These tools measure Java source, class, and jar sizes for an AWS service.
The `Codegen Size` GitHub workflow compares EC2 client sizes with the base commit and posts a pull request comment.
See `.github/workflows/codegen-size.yml` and `codegen-size-comment.yml`.

* `CodegenSize.java` generates code for one service and compiles it with the running JDK (`--release 21 -g`).
  Its JSON report contains totals, category totals, and sizes for each class.
  With `--baseline`, it also writes a Markdown comparison.
* `codegen-size.init.gradle` adds two tasks to `:codegen:codegen-plugin`.
  `codegenSizeClasspath` writes the classpath for the plugin and generated code.
  `codegenSizeModel` copies an AWS model jar at the version pinned by the `aws-api-models` BOM.
* `measure.sh` builds the plugin in a specified checkout and runs the measurement tool.

The tool from the pull request runs with each checkout's classpath.
The baseline checkout does not need to contain these scripts.

## Running locally

```sh
./gradlew -I scripts/codegen-size/codegen-size.init.gradle :codegen:codegen-plugin:codegenSizeModel \
    -PcodegenSizeModelFile=build/codegen-size/ec2.jar

git worktree add /tmp/smithy-java-base main
scripts/codegen-size/measure.sh /tmp/smithy-java-base build/codegen-size/ec2.jar build/codegen-size/base.json
scripts/codegen-size/measure.sh . build/codegen-size/ec2.jar build/codegen-size/pr.json \
    --baseline build/codegen-size/base.json --markdown build/codegen-size/report.md
```

The `report` command accepts these options:

* `--modes client,server` selects generation modes. The default is `client`.
* `--service <shape id>` selects a service when the model contains several services.
* `--dump <dir>` writes generated sources, classes, and the jar for inspection.

`CodegenSize.java compare base.json pr.json` produces Markdown from two reports.
This command only needs `smithy-model` on the classpath.
