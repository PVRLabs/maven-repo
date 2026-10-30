# Contributions

Submissions require PVR Labs approval. Only redistributable open-source projects
with public GitHub source and identifiable public GitHub Releases are accepted.
No private dependencies or arbitrary binary hosting.

Build the library and upload its original POMs and JARs to a public GitHub Release.
Include parent POMs and all required modules; optional source/Javadoc JARs are welcome.
Preserve dependency and license declarations in POMs.

Submit a PR adding only pointers under `artifacts/`, using Maven group directories,
artifact ID, and immutable release version. For example:

`artifacts/org/example/demo/1.0/demo-1.0.jar.url`

Each file contains exactly two whitespace-separated fields (normally two lines):

```text
https://github.com/OWNER/REPO/releases/download/TAG/demo-1.0.jar
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

Use the actual SHA-256 digest from the release asset metadata or a locally verified
release file (`shasum -a 256 demo-1.0.jar`). Add the corresponding `.pom.url` too.
The example digest is a placeholder. No real releases are included yet.

Explain the source release and redistribution license in the PR. Maintainers review
provenance and licensing before publication. Never change a published version;
publish a new version instead. Contributors should not edit `published.json`.
