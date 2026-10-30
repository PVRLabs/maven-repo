# PVR Labs Maven Repository

Experimental, curated publishing for early-stage open-source Java libraries without
Maven Central / Sonatype onboarding. Standard Maven layout means consumers need no
custom resolver. This is a small static repository, without Central's availability
or review guarantees.

The planned endpoint is `https://pvrlabs.github.io/maven/`; hosting and initial
releases are not set up by this project.

```xml
<repositories>
  <repository>
    <id>pvrlabs</id>
    <url>https://pvrlabs.github.io/maven/</url>
  </repository>
</repositories>
```

Gradle (Kotlin):

```kotlin
repositories {
    maven { url = uri("https://pvrlabs.github.io/maven/") }
    mavenCentral()
}
```

Add the library's ordinary group, artifact and version dependency separately.

## Publishing

Requires Python 3.9+ and an existing destination directory:

```sh
./scripts/publish.sh /path/to/public-site/maven
# Optionally expose the publication audit:
./scripts/publish.sh /path/to/public-site/maven --copy-state
python3 -m unittest discover -s tests -v
```

Review and merge approved pointers, run the publisher, then review and commit
`published.json` here and manually upload or commit the generated hosting files.
Do not commit downloaded artifacts here. No deployment credentials are needed.

`published.json` is authoritative. Existing publications are checked for existence locally and
skipped without downloading or rewriting them. Changed pointers and missing
published files, and occupied new destination paths fail. Removed pointers never
remove publications. New downloads are SHA-256 verified before the batch is written.
POM bytes (including parent and dependency declarations) are preserved. Artifact
checksums and artifact-level version metadata are generated; snapshots are unsupported.
Versions are listed without guessing Maven's latest/release ordering.

Use one publisher at a time. An interrupted filesystem write may require manual
inspection; automatic recovery, rebuilding, signing, and deployment are outside
this MVP. Keep destination directories trusted and free of symlink redirections.

See [CONTRIBUTING.md](CONTRIBUTING.md) for submissions. The MIT license covers this
project's tooling and documentation; distributed libraries retain their own licenses.

POM literal coordinates must match their destination; group and version may be
inherited from a parent. Property expressions are left to Maven. Historical
artifacts are never read or rehashed during normal publication.

The regular test suite includes a Maven consumer test, skipped only when Maven
or a JDK is unavailable. It uses [`mvn-lite`](https://github.com/ejboy/agent-scripts#mvn-lite) when available (otherwise `mvn`) to compile a minimal consumer against a generated library
and parent POM in a temporary file repository. Each run uses unique fixture
coordinates so a previously cached fixture cannot satisfy resolution. Maven uses
its normal `~/.m2` cache for plugins and external dependencies; only missing plugins
may require Maven Central access. No live GitHub Releases are used.
