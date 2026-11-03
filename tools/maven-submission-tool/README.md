# Maven submission tool

Generate submission pointers for [PVR Labs Maven](https://github.com/PVRLabs/maven-repo)
from an existing public GitHub Release containing Maven JARs and POMs.

## 1. Download

Java 17+ is required. Download the executable JAR:

```sh
curl -fL -o maven-submission-tool.jar \
  https://github.com/PVRLabs/maven-repo/releases/download/maven-submission-tool-0.1.0/maven-submission-tool-0.1.0.jar
```

Check the [GitHub Releases page](https://github.com/PVRLabs/maven-repo/releases)
for newer versions.

## 2. Run

Pass the public GitHub Release URL for the library you want to submit:

```sh
java -jar maven-submission-tool.jar \
  https://github.com/PVRLabs/coilbase/releases/tag/v0.1.0-preview1 \
  --output ./submission
```

For usage information, run `java -jar maven-submission-tool.jar --help`.

## 3. Submit

Inspect `submission/artifacts/`, checking Maven coordinates, release URLs, and
SHA-256 digests against the intended immutable release. Copy the generated
pointers into the matching `artifacts/` paths in your `maven-repo` checkout.
Open a PR with the source release, project description, and license information,
following the [submission policy](../../CONTRIBUTING.md). The
[manual publishing guide](../../docs/publishing-a-library.md) remains the fallback.

## Supported POMs and limitations

Only literal Maven coordinates and ordinary parent group/version inheritance are
supported. Property expressions require a resolved consumer POM. Parent and BOM
POMs must be uploaded with Maven filenames. Missing POMs and existing output files
are errors. Release assets must be public; GitHub API rate limits apply.

POMs must declare modelVersion `4.0.0` and use either the Maven 4.0.0 namespace
or no namespace. Coordinate and parent elements must use that same namespace.
Duplicate coordinate fields, nested values, and coordinate property expressions
are rejected. When a referenced parent is included in the release, its coordinates
and `pom` packaging must agree with the reference. External parents are not
resolved; ensure they are available to consumers before submitting.

Asset matching requires unique `artifactId-version` filenames across the release.
Artifacts with the same artifact ID and version in different group IDs cannot be
submitted together by this tool: their required Maven asset filenames collide.
This is reported as an unsupported ambiguity. Use separate releases or prepare
those pointers manually using the [publishing guide](../../docs/publishing-a-library.md).

## Development

Build from this directory with Maven and Java 17+:

```sh
mvn clean package
```

The executable JAR is `target/maven-submission-tool-0.1.0.jar`.
The independent Maven coordinates are `pvrlabs:maven-submission-tool:0.1.0`.
The JAR bundles Gson; publish the generated `dependency-reduced-pom.xml` as the
consumer POM so it omits that bundled dependency.

Run `mvn test` for the offline fixture suite (Python 3 is required for the
compatibility test). Tests pass generated pointers and POMs to the existing
publisher validators; no GitHub requests are made. Maven may download test and
build dependencies on the first run.

### Bootstrap the first release

Build and manually create release tag `maven-submission-tool-0.1.0`, attaching
`target/maven-submission-tool-0.1.0.jar` and `dependency-reduced-pom.xml` renamed
`maven-submission-tool-0.1.0.pom`. Bootstrap its pointers manually using the
[publishing guide](../../docs/publishing-a-library.md). Future releases can use
the previous tool version to generate their pointers. This project has its own
version and no dependency on publication state or the Python publisher.
