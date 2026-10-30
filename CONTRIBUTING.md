# Contributions

## Versioning and release policy

PVR Labs Maven accepts immutable preview releases and independently distributed
open-source Java libraries.

- Recommended for previews: `1.8.0-preview1`, `1.8.0-preview2`, and so on.
- Other conventional prerelease versions, such as `1.8.0-alpha1`, `1.8.0-beta1`,
  and `1.8.0-rc1`, are also welcome.
- Independent libraries may use ordinary versions such as `0.1.0` or `1.0.0`.
- `-SNAPSHOT` versions are not supported. All published artifacts must be immutable.

Preview is a versioning recommendation, not a maturity certification. This
repository supplements Maven Central rather than mirroring it. Projects already
publishing stable releases to Maven Central should normally submit only their
prereleases.

Submissions are curated and reviewed individually. Only redistributable open-source
projects with public GitHub source and identifiable public GitHub Releases are
accepted. No private dependencies or arbitrary binary hosting.

## Submitting artifacts

See the [publishing a library walkthrough](docs/publishing-a-library.md) for build,
release, pointer, and consumer verification steps.

Build the library and upload its original POMs and JARs to a public GitHub Release.
Include parent POMs and all required modules; optional source/Javadoc JARs are welcome.
Preserve dependency and license declarations in POMs.

Submit a PR adding pointers under `artifacts/`, using Maven group directories,
artifact ID, and immutable release version. For example:

`artifacts/org/example/demo/1.0/demo-1.0.jar.url`

Each file contains exactly two whitespace-separated fields (normally two lines):

```text
https://github.com/OWNER/REPO/releases/download/TAG/demo-1.0.jar
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

Use the actual SHA-256 digest from the release asset metadata or a locally verified
release file (`shasum -a 256 demo-1.0.jar`). Add the corresponding `.pom.url` too.
The example digest is a placeholder.

Explain the source release and redistribution license in the PR. Maintainers review
provenance and licensing before publication. Never change a published version;
publish a new version instead. Contributors should not edit `published.json`.
