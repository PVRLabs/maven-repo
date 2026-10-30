# Why PVR Labs Maven Repository?

The idea grew out of maintaining [Scriptella ETL](https://github.com/scriptella/scriptella-etl).
We want to publish previews more frequently so developers can try changes and
share feedback before a stable release.

Maven Central is excellent for stable releases. Sonatype onboarding, signing,
metadata, and publication requirements take effort, though, and that friction is
more noticeable with frequent previews. Scriptella stable releases will continue
using Maven Central; previews can use PVR Labs Maven.

GitHub Releases provides the binaries. PVR Labs Maven makes them available through
standard Maven and Gradle coordinates, so trying a preview fits the dependency
workflow developers already use.

PVR Labs will also distribute its own experimental Java libraries here. Over time,
we hope to grow a small, curated collection of useful open-source Java libraries:
a place to discover projects and easily add them as dependencies.

The goal is to make sharing and trying these libraries practical. We do not aim
to replace Maven Central or build a general-purpose artifact registry.
