package pvrlabs.maven.submission;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path output;
    final JsonObject release = new JsonObject();
    final JsonArray assets = new JsonArray();
    final Map<String, byte[]> downloads = new HashMap<>();
    final List<String> requests = new ArrayList<>();
    MainTest() { release.add("assets", assets); }
    static String pom(String content) {
        return "<project xmlns='http://maven.apache.org/POM/4.0.0'><modelVersion>4.0.0</modelVersion>" + content + "</project>";
    }
    static String coordinates(String artifact) {
        return "<groupId>pvrlabs</groupId><artifactId>" + artifact + "</artifactId><version>0.1.0</version>";
    }
    void asset(String name, String content, String digest) {
        String url = "https://github.com/PVRLabs/example/releases/download/v0.1.0/" + name;
        JsonObject asset = new JsonObject();
        asset.addProperty("name", name); asset.addProperty("browser_download_url", url);
        if (digest != null) asset.addProperty("digest", digest);
        assets.add(asset); downloads.put(url, content.getBytes(StandardCharsets.UTF_8));
    }
    void module(String artifact, String body) {
        asset(artifact + "-0.1.0.pom", pom(body), null);
        asset(artifact + "-0.1.0.jar", "jar fixture", null);
    }
    void generate() throws Exception {
        Main.generate(release, output, uri -> {
            requests.add(uri.toString());
            byte[] data = downloads.get(uri.toString());
            if (data == null) throw new AssertionError("Unexpected request " + uri);
            return data;
        });
    }
    Path pointer(String artifact, String extension) {
        return output.resolve("artifacts/pvrlabs/" + artifact + "/0.1.0/" + artifact + "-0.1.0." + extension + ".url");
    }
    @Test void singleModuleAndPublisherCompatibility() throws Exception {
        module("example", coordinates("example")); generate();
        String url = "https://github.com/PVRLabs/example/releases/download/v0.1.0/example-0.1.0.jar";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(downloads.get(url)));
        assertEquals(url + "\n" + hash + "\n", Files.readString(pointer("example", "jar")));
        // Call the real publisher's pointer and POM validators on every generated fixture.
        Path publisher = Path.of("../../scripts/publish.py").toAbsolutePath().normalize();
        assertTrue(Files.exists(publisher));
        String script = "import importlib.util,pathlib,sys\n" +
            "s=importlib.util.spec_from_file_location('publisher',sys.argv[1]);m=importlib.util.module_from_spec(s);s.loader.exec_module(m)\n" +
            "root=pathlib.Path(sys.argv[2])\n" +
            "for p in root.rglob('*.url'):\n" +
            " r=p.relative_to(root).as_posix()[:-4];m.pointer(r,p.read_text())\n" +
            " if r.endswith('.pom'): m.validate_pom(r,pathlib.Path(sys.argv[3]).read_bytes())\n";
        Path fixture = output.resolve("fixture.pom");
        Files.write(fixture, downloads.get("https://github.com/PVRLabs/example/releases/download/v0.1.0/example-0.1.0.pom"));
        Process process = new ProcessBuilder("python3", "-c", script, publisher.toString(), output.resolve("artifacts").toString(), fixture.toString()).redirectErrorStream(true).start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), log);
    }
    @Test void multiModuleWithInheritedCoordinatesAndBom() throws Exception {
        asset("parent-0.1.0.pom", pom(coordinates("parent") + "<packaging>pom</packaging>"), null);
        asset("bom-0.1.0.pom", pom(coordinates("bom") + "<packaging>pom</packaging>"), null);
        String parent = "<parent>" + coordinates("parent") + "</parent>";
        module("api", parent + "<artifactId>api</artifactId>");
        module("client", parent + "<artifactId>client</artifactId>");
        generate();
        for (String artifact : List.of("parent", "bom", "api", "client")) assertTrue(Files.exists(pointer(artifact, "pom")));
        assertTrue(Files.exists(pointer("client", "jar")));
        assertFalse(Files.exists(pointer("parent", "jar")));
    }
    @Test void includedParentMustMatchReference() {
        asset("parent-0.1.0.pom", pom(coordinates("parent") + "<packaging>pom</packaging>"), null);
        module("example", "<parent>" + coordinates("parent").replace("pvrlabs", "other") + "</parent><artifactId>example</artifactId>");
        assertTrue(assertThrows(IllegalArgumentException.class, this::generate).getMessage().contains("parent reference"));
    }
    @Test void sameArtifactAndVersionAcrossGroupsIsUnsupported() {
        asset("example-0.1.0.pom", pom(coordinates("example")), null);
        asset("example-0.1.0.pom", pom(coordinates("example").replace("pvrlabs", "another.group")), null);
        String message = assertThrows(IllegalArgumentException.class, this::generate).getMessage();
        assertTrue(message.contains("Unsupported asset ambiguity"));
        assertTrue(message.contains("across groupIds"));
        assertTrue(requests.isEmpty());
        assertFalse(Files.exists(output.resolve("artifacts")));
    }
    @Test void helpExplainsDownloadedTool() throws Exception {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Main.class.getName(), "--help")
                .redirectErrorStream(true).start();
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), text);
        assertTrue(text.contains("Usage:"));
        assertTrue(text.contains("--output ./submission"));
        assertTrue(text.contains("Java 17+"));
    }
    @Test void missingPom() {
        asset("example-0.1.0.jar", "jar", null);
        assertTrue(assertThrows(IllegalArgumentException.class, this::generate).getMessage().contains("Missing matching POM"));
    }
    @Test void mismatchedPom() {
        module("example", coordinates("different"));
        assertTrue(assertThrows(IllegalArgumentException.class, this::generate).getMessage().contains("filename disagrees"));
    }
    @Test void githubDigestAvoidsJarDownload() throws Exception {
        String digest = "AB".repeat(32);
        asset("example-0.1.0.pom", pom(coordinates("example")), "sha256:" + digest);
        asset("example-0.1.0.jar", "jar", "sha256:" + digest);
        generate();
        assertEquals(1, requests.size());
        assertTrue(Files.readString(pointer("example", "jar")).endsWith(digest.toLowerCase(Locale.ROOT) + "\n"));
    }
    @Test void malformedDigestFailsBeforeWriting() {
        module("example", coordinates("example"));
        assets.get(1).getAsJsonObject().addProperty("digest", "sha256:bad");
        assertThrows(IllegalArgumentException.class, this::generate);
        assertFalse(Files.exists(output.resolve("artifacts")));
    }
    @Test void outputConflictLeavesExistingContentAndWritesNothingElse() throws Exception {
        module("example", coordinates("example"));
        Path existing = pointer("example", "jar"); Files.createDirectories(existing.getParent()); Files.writeString(existing, "existing");
        assertThrows(java.io.IOException.class, this::generate);
        assertEquals("existing", Files.readString(existing));
        assertFalse(Files.exists(pointer("example", "pom")));
    }
    @Test void ignoresSidecarsAndZip() throws Exception {
        module("example", coordinates("example"));
        for (String name : List.of("source.zip", "example.jar.asc", "example.jar.sha256")) asset(name, "ignored", null);
        generate(); assertEquals(2, requests.size());
    }
    @Test void unsupportedPomStructuresAndExpressions() {
        for (String xml : List.of(
                "<project><artifactId>example</artifactId></project>",
                "<project xmlns='urn:other'><modelVersion>4.0.0</modelVersion></project>",
                pom(coordinates("example").replace("<groupId>", "<groupId xmlns='urn:other'>")),
                pom(coordinates("example").replace("0.1.0", "${revision}")),
                pom(coordinates("example") + "<groupId>pvrlabs</groupId>"),
                pom(coordinates("example") + "<parent><groupId>pvrlabs</groupId></parent>"))) {
            assets.asList().clear(); downloads.clear();
            asset("example-0.1.0.pom", xml, null);
            assertThrows(IllegalArgumentException.class, this::generate, xml);
            assertFalse(Files.exists(output.resolve("artifacts")));
        }
    }
    @Test void acceptsNamespaceFreePom() throws Exception {
        module("example", coordinates("example"));
        downloads.replaceAll((url, data) -> url.endsWith(".pom") ? new String(data, StandardCharsets.UTF_8).replace(" xmlns='http://maven.apache.org/POM/4.0.0'", "").getBytes(StandardCharsets.UTF_8) : data);
        generate(); assertTrue(Files.exists(pointer("example", "pom")));
    }
    @Test void rejectsSnapshotAndInvalidReleaseUrl() {
        module("example", coordinates("example").replace("0.1.0", "0.1.0-SNAPSHOT"));
        assertTrue(assertThrows(IllegalArgumentException.class, this::generate).getMessage().contains("Snapshot"));
        for (String url : List.of("http://github.com/a/b/releases/tag/v1", "https://example.com/a/b/releases/tag/v1", "https://github.com/a/b/releases/latest")) assertThrows(IllegalArgumentException.class, () -> Main.releaseApi(url));
        assertEquals("https://api.github.com/repos/PVRLabs/example/releases/tags/v1", Main.releaseApi("https://github.com/PVRLabs/example/releases/tag/v1").toString());
    }
}
