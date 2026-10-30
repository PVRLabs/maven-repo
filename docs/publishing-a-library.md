# Publish a Java library

This walkthrough takes an existing Maven project from a tested build to a GitHub
Release and a pull request to PVR Labs Maven. Choose one immutable version for every
artifact in the release. For previews, `0.1.0-preview1` is a good starting point;
never publish `-SNAPSHOT` versions.

## Prepare and build the project

Check each public module's `groupId`, `artifactId`, and `version`, and confirm its
POM declares the dependencies consumers need. Use the same release version across
modules that are released together. Build and test the project from its root:

```sh
mvn clean verify
```

For a multi-module project, inspect the module list and dependency declarations in
the root POM and each module POM. Identify the modules consumers should use, then
follow their dependencies on other project modules. Publish every required internal
module, or confirm it is already available from a repository consumers can access.
Do not leave dependencies on local-only modules or `-SNAPSHOT` versions.

### Prepare the consumer POMs

Every published JAR needs its matching POM. For an ordinary Maven project, the
module's `pom.xml` is the starting point, but `mvn package` does not automatically
attach that POM to a GitHub Release. Collect the intended consumer POM and the built
JAR for each public module. A parent POM is published as a POM-only artifact; it
needs no JAR. If a consumer POM inherits from a parent or imports a BOM that is not
already available in another accessible repository, include that POM as a separate
artifact too.

For example, a project with this layout:

```text
widget/
├── pom.xml                 # org.example:widget-parent:0.1.0-preview1 (packaging pom)
├── widget-api/pom.xml      # org.example:widget-api:0.1.0-preview1
└── widget-client/pom.xml   # org.example:widget-client:0.1.0-preview1
```

If `widget-client` depends on `widget-api`, release these assets:

```text
widget-parent-0.1.0-preview1.pom
widget-api-0.1.0-preview1.pom
widget-api-0.1.0-preview1.jar
widget-client-0.1.0-preview1.pom
widget-client-0.1.0-preview1.jar
```

The root parent contributes only a POM. Find each JAR in its module's `target/`
after the build, and match it to that module's artifactId and version. Select the
POM intended for consumers: normally the module `pom.xml`, plus any required parent
or BOM POMs. If the project generates or flattens consumer POMs, publish that actual
consumer POM. Inspect each selected POM for unresolved build-specific properties such
as `${revision}` or `${project.version}` that would not be defined for a consumer.
Confirm its parent coordinates, dependency versions, and imported BOMs resolve from
the published artifacts or other repositories available to consumers, without the
original checkout. If your build generates consumer POMs with these values resolved,
use those generated POMs. `help:effective-pom` shows Maven's resolved model and is not
a direct replacement for a consumer POM. Do not upload every file from `target/`;
choose the required JARs and POMs deliberately and check that filenames match their
coordinates.

## Create a GitHub Release

Choose a new release tag, such as `widget-0.1.0-preview1`, and do not reuse or change
the tag or its assets after publication. Upload all required JAR and POM files listed
above. Maven filenames follow `<artifactId>-<version>.<extension>`; parent POMs use
their own artifactId and version too.

Calculate SHA-256 for each intended local file, then verify the uploaded GitHub
Release asset has the same digest. GitHub reports an asset digest in the release
asset details; alternatively, download the asset from its release URL and hash that
download. This confirms the URL pointer will refer to the exact file you tested.

```sh
shasum -a 256 widget-api-0.1.0-preview1.jar widget-api-0.1.0-preview1.pom
# Linux alternative:
sha256sum widget-api-0.1.0-preview1.jar widget-api-0.1.0-preview1.pom
```

Use the actual digest for the exact uploaded file. Compare the downloaded or
GitHub-reported digest with the local build file digest before creating its pointer.
This process needs no Maven
Central publication, signing, or GitHub Actions setup.

## Verify with a consumer

Before submitting, test resolution from a separate Maven project against a temporary
Maven-layout directory made from the selected release assets. Include all the release
POMs and JARs at paths derived from their coordinates, for example:

```text
org/example/widget/widget-parent/0.1.0-preview1/widget-parent-0.1.0-preview1.pom
org/example/widget/widget-api/0.1.0-preview1/widget-api-0.1.0-preview1.pom
org/example/widget/widget-api/0.1.0-preview1/widget-api-0.1.0-preview1.jar
org/example/widget/widget-client/0.1.0-preview1/widget-client-0.1.0-preview1.pom
org/example/widget/widget-client/0.1.0-preview1/widget-client-0.1.0-preview1.jar
```

Configure the separate consumer's `pom.xml` to use that directory and depend on the
library. For example, if the staging directory is `/tmp/widget-repo`:

```xml
<repositories>
  <repository>
    <id>widget-staging</id>
    <url>file:///tmp/widget-repo</url>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>org.example</groupId>
    <artifactId>widget-client</artifactId>
    <version>0.1.0-preview1</version>
  </dependency>
</dependencies>
```

Add Java source that imports and uses the library, then compile using a fresh local
cache so no unpublished project artifacts can satisfy resolution:

```sh
mvn -f /path/to/consumer/pom.xml \
  -Dmaven.repo.local=/tmp/widget-consumer-cache clean compile
```

Maven may download normal build plugins and external dependencies from their
repositories. Verify that the consumer compiles and that required transitive modules
resolve from the staged repository or another accessible repository. Fix missing
POMs or dependencies before submitting. Once PVR Labs Maven publishes the release,
repeat the consumer check using `https://pvrlabs.github.io/maven/` and a fresh local
cache. Treat the publication as successful only after this public endpoint check
passes.

## Submit pointers

In `PVRLabs/maven-repo`, add a `.url` file for each release asset under
`artifacts/<groupId path>/<artifactId>/<version>/`. For the example above:

```text
artifacts/org/example/widget/widget-api/0.1.0-preview1/widget-api-0.1.0-preview1.jar.url
artifacts/org/example/widget/widget-api/0.1.0-preview1/widget-api-0.1.0-preview1.pom.url
artifacts/org/example/widget/widget-parent/0.1.0-preview1/widget-parent-0.1.0-preview1.pom.url
```

Each pointer contains the public GitHub Release asset URL followed by its SHA-256
digest, one per line:

```text
https://github.com/OWNER/REPO/releases/download/TAG/widget-api-0.1.0-preview1.jar
<64-character SHA-256 digest>
```

Repeat for every required JAR and POM, including `widget-client` and any parent or
BOM POMs. Open a pull request with the pointers, a short project description, the
source release link, and the applicable license information. Follow
[CONTRIBUTING.md](../CONTRIBUTING.md) for the submission policy. Do not include
binary artifacts or edit `published.json` in the pull request.
