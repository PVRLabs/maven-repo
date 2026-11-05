# PVR Labs Maven Repository

A lightweight, curated Maven repository for open-source Java libraries and preview releases. Publish through GitHub Releases without Maven Central / Sonatype onboarding. Use standard Maven or Gradle dependencies, with no custom plugins or dependency managers required.

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

## Hosted projects

- [CoilBase](https://github.com/PVRLabs/coilbase) is a lightweight Java application
  framework with Spring Boot-inspired simplicity, explicit SQL, and no heavyweight
  runtime. Its published modules include `coilbase-core`, `coilbase-jex`, and the
  `coilbase-parent` POM.

See the [public Maven repository](https://pvrlabs.github.io/maven/) for published
versions and downloads.

## Why this repository?

Maven Central is great for stable releases, but its publishing process adds friction to frequent previews and experimental libraries. Inspired by our experience maintaining [Scriptella ETL](https://github.com/scriptella/scriptella-etl), this repository offers a simpler way to share and discover a small, curated collection of open-source Java libraries.

[Read more about why we built it](docs/why.md).

## Contributing a library

Submitted artifacts originate from public GitHub Releases, are verified with SHA-256,
and are published in standard Maven repository layout. Submit a pull request with
release asset URL pointers and digests; see [CONTRIBUTING.md](CONTRIBUTING.md) for
the format and approval requirements. The [publishing walkthrough](docs/publishing-a-library.md)
explains how to prepare and verify a release. Published versions are immutable.
The [Maven submission tool](tools/maven-submission-tool/README.md) helps prepare
artifact submissions.

## Publishing

Repository maintainers can find publication commands, state management, and testing instructions in the [publishing guide](docs/publishing.md).

The MIT license covers this project's tooling and documentation; distributed libraries retain their own licenses.
