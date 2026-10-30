# Publishing guide

This guide is for repository maintainers publishing approved submissions to an
existing local Maven repository directory, such as a checkout of the GitHub Pages
hosting repository.

## Requirements and command

The publisher requires Python 3.9+ and an existing destination directory:

```sh
./scripts/publish.sh /path/to/public-site/maven
```

To also copy the publication audit file into the generated repository, add
`--copy-state`:

```sh
./scripts/publish.sh /path/to/public-site/maven --copy-state
```

## Publication state and generated files

`published.json` in this repository is the authoritative source of truth. After
running the publisher, review and commit its changes here. The generated artifacts
and metadata are written into the destination; commit or upload those files to the
hosting destination manually. Do not commit downloaded artifacts into this source
repository.

Publishing is incremental. The publisher scans approved `.url` pointers, downloads
new assets from public GitHub Releases, and verifies SHA-256 before writing. Existing
published artifacts are skipped after a local existence check. Changed pointers,
missing published files, and occupied new paths fail. Removed pointers do not remove
published artifacts. Published versions are immutable; submit a new version for
changes.

POM bytes are preserved. The publisher generates artifact checksums and artifact
version metadata. Snapshots are unsupported. Keep the destination directory trusted
and free of symlink redirections, and run only one publisher at a time. Interrupted
writes may need manual inspection. Automatic recovery, rebuilding, signing, and
deployment automation are outside the current scope.

## Tests

Run the full suite with:

```sh
python3 -m unittest discover -s tests -v
```

It includes a Maven consumer test and skips that test only when Maven or a JDK is
unavailable. The test uses [`mvn-lite`](https://github.com/ejboy/agent-scripts#mvn-lite)
when available, falling back to `mvn`. It compiles a minimal consumer against a
generated library and parent POM in a temporary file repository. Each run uses unique
fixture coordinates so a cached fixture cannot satisfy resolution. Maven uses its
normal `~/.m2` cache for plugins and external dependencies; missing plugins may need
to be downloaded from Maven Central. The test does not use live GitHub Releases.
