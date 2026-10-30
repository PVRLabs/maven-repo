# PVR Labs Maven Repository

A lightweight, curated Maven repository for open-source Java libraries and preview releases. Publish through GitHub Releases without Maven Central / Sonatype onboarding. Use standard Maven or Gradle dependencies—no custom plugins or dependency managers required.

Maven:

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

Add a library’s ordinary group, artifact, and version dependency alongside this configuration.
The [public Maven repository](https://pvrlabs.github.io/maven/) is currently empty
and preparing for its first releases.

## Contributing a library

Submitted artifacts originate from public GitHub Releases, are verified with SHA-256,
and are published in standard Maven repository layout. Submit a pull request with
release asset URL pointers and digests; see [CONTRIBUTING.md](CONTRIBUTING.md) for
the format and approval requirements. Published versions are immutable.

## Publishing

Requires Python 3.9+ and an existing destination directory:

```sh
./scripts/publish.sh /path/to/public-site/maven
# Optionally expose the publication audit:
./scripts/publish.sh /path/to/public-site/maven --copy-state
```

`published.json` is authoritative. Review and merge approved pointers, run the publisher, then review and commit `published.json` here and manually upload or commit the generated hosting files. Do not commit downloaded artifacts here.

The publisher verifies new downloads with SHA-256, preserves POM bytes, and generates checksums and artifact-level version metadata. Snapshots are unsupported. Existing publications are skipped after a local existence check; changed pointers, missing published files, and occupied new paths fail. Removed pointers do not remove publications. Use one publisher at a time; interrupted writes may need manual inspection. Keep destination directories trusted and free of symlink redirections. Automatic recovery, rebuilding, signing, and deployment are outside this MVP.

## Development

Run the test suite with:

```sh
python3 -m unittest discover -s tests -v
```

The suite includes a Maven consumer test, skipped only when Maven or a JDK is unavailable. It uses [`mvn-lite`](https://github.com/ejboy/agent-scripts#mvn-lite) when available (otherwise `mvn`) to compile a minimal consumer against a generated library and parent POM in a temporary file repository. Each run uses unique fixture coordinates to avoid satisfying resolution from a cached fixture. Maven uses its normal `~/.m2` cache for plugins and external dependencies; only missing plugins may require Maven Central access. No live GitHub Releases are used.

The MIT license covers this project’s tooling and documentation; distributed libraries retain their own licenses.
