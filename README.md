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
The [public Maven repository](https://pvrlabs.github.io/maven/) is currently empty
and preparing for its first releases.

## Why this repository?

Maven Central is great for stable releases, but its publishing process adds friction to frequent previews and experimental libraries. Inspired by our experience maintaining [Scriptella ETL](https://github.com/scriptella/scriptella-etl), this repository offers a simpler way to share and discover a small, curated collection of open-source Java libraries.

[Read more about why we built it](docs/why.md).

## Contributing a library

Submitted artifacts originate from public GitHub Releases, are verified with SHA-256,
and are published in standard Maven repository layout. Submit a pull request with
release asset URL pointers and digests; see [CONTRIBUTING.md](CONTRIBUTING.md) for
the format and approval requirements. Published versions are immutable.

## Publishing

Repository maintainers can find publication commands, state management, and testing instructions in the [publishing guide](docs/publishing.md).

The MIT license covers this project's tooling and documentation; distributed libraries retain their own licenses.
