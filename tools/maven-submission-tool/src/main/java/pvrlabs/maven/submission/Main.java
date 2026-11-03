package pvrlabs.maven.submission;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.*;

/** Generates curated repository pointers without changing repository state. */
public final class Main {
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
    static final String HELP = """
            Usage: java -jar maven-submission-tool.jar RELEASE_URL --output DIRECTORY

            Generate PVR Labs Maven submission pointers from a public GitHub Release.
            Requires Java 17+. Writes pointers under DIRECTORY/artifacts/.

            Example:
              java -jar maven-submission-tool.jar https://github.com/PVRLabs/coilbase/releases/tag/v0.1.0-preview1 --output ./submission
            """;

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.print(HELP);
            return;
        }
        try {
            if (args.length != 3 || !args[1].equals("--output")) throw new IllegalArgumentException("Usage: java -jar maven-submission-tool.jar RELEASE_URL --output DIRECTORY");
            URI api = releaseApi(args[0]);
            generate(JsonParser.parseString(new String(fetch(api), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(), Path.of(args[2]));
        } catch (Exception e) { System.err.println("Error: " + e.getMessage()); System.exit(1); }
    }
    static URI releaseApi(String url) {
        if (!url.matches("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/tag/[^/?#\\s]+")) throw new IllegalArgumentException("Invalid public GitHub Release URL");
        return URI.create(url.replace("https://github.com/", "https://api.github.com/repos/").replace("/releases/tag/", "/releases/tags/"));
    }
    static byte[] fetch(URI uri) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).header("User-Agent", "pvrlabs-maven-submission-tool/0.1.0").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        return response.body();
    }
    @FunctionalInterface
    interface Downloader { byte[] get(URI uri) throws Exception; }

    static Element child(Element root, String name) {
        Element found = null;
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && name.equals(e.getLocalName())) {
                if (!Objects.equals(root.getNamespaceURI(), e.getNamespaceURI()))
                    throw new IllegalArgumentException("Unsupported POM namespace for " + name);
                if (found != null) throw new IllegalArgumentException("Duplicate POM element: " + name);
                found = e;
            }
        }
        return found;
    }

    static String value(Element root, String name) {
        Element element = child(root, name);
        if (element == null) return null;
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element) throw new IllegalArgumentException("Unsupported nested POM value: " + name);
        return element.getTextContent().trim();
    }
    static String coordinate(String value) {
        if (value != null && value.contains("${")) throw new IllegalArgumentException("Unsupported coordinate expression: " + value);
        if (value == null || !value.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*") || value.equals(".") || value.equals("..")) throw new IllegalArgumentException("Missing, unsafe, or unresolved Maven coordinate: " + value);
        return value;
    }
    static void generate(JsonObject release, Path output) throws Exception {
        generate(release, output, Main::fetch);
    }
    static void generate(JsonObject release, Path output, Downloader downloader) throws Exception {
        Path absolute = output.toAbsolutePath().normalize();
        Path ancestor = absolute;
        while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
        output = ancestor.toRealPath().resolve(ancestor.relativize(absolute));
        Map<String, JsonObject> assets = new LinkedHashMap<>();
        for (JsonElement entry : release.getAsJsonArray("assets")) {
            JsonObject asset = entry.getAsJsonObject(); String name = asset.get("name").getAsString();
            if (!(name.endsWith(".jar") || name.endsWith(".pom"))) continue;
            if (assets.putIfAbsent(name, asset) != null) throw new IllegalArgumentException("Unsupported asset ambiguity: duplicate filename " + name + "; matching requires unique artifactId-version filenames across groupIds");
        }
        Map<String, String> bases = new LinkedHashMap<>(); Map<String, byte[]> downloaded = new HashMap<>();
        Map<String, String> parentReferences = new LinkedHashMap<>();
        Set<String> pomOnly = new HashSet<>();
        for (var entry : assets.entrySet()) {
            if (!entry.getKey().endsWith(".pom")) continue;
            byte[] data = downloader.get(URI.create(entry.getValue().get("browser_download_url").getAsString())); downloaded.put(entry.getKey(), data);
            var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element pom = factory.newDocumentBuilder().parse(new ByteArrayInputStream(data)).getDocumentElement();
            if ((!Objects.equals(pom.getNamespaceURI(), null) && !"http://maven.apache.org/POM/4.0.0".equals(pom.getNamespaceURI())) || !"project".equals(pom.getLocalName())) throw new IllegalArgumentException("Invalid POM: " + entry.getKey());
            if (!"4.0.0".equals(value(pom, "modelVersion"))) throw new IllegalArgumentException("Unsupported POM modelVersion: " + entry.getKey());
            Element parent = child(pom, "parent");
            if (parent != null) {
                coordinate(value(parent, "groupId"));
                coordinate(value(parent, "artifactId"));
                String parentVersion = coordinate(value(parent, "version"));
                if (parentVersion.toUpperCase(Locale.ROOT).contains("SNAPSHOT")) throw new IllegalArgumentException("Snapshot parent version: " + parentVersion);
                String parentArtifact = value(parent, "artifactId");
                parentReferences.put(parentArtifact + "-" + parentVersion,
                        value(parent, "groupId").replace('.', '/') + "/" + parentArtifact + "/" + parentVersion + "/");
            }
            String group = value(pom,"groupId"), version = value(pom,"version"), artifact = coordinate(value(pom,"artifactId"));
            if (parent != null) { if (group == null) group = value(parent,"groupId"); if (version == null) version = value(parent,"version"); }
            group = coordinate(group); version = coordinate(version);
            for (String segment : group.split("\\.", -1)) coordinate(segment);
            if (version.toUpperCase(Locale.ROOT).contains("SNAPSHOT")) throw new IllegalArgumentException("Snapshot version: " + version);
            String base = artifact + "-" + version;
            if (!entry.getKey().equals(base + ".pom")) throw new IllegalArgumentException("POM filename disagrees with coordinates: " + entry.getKey());
            if (bases.putIfAbsent(base, group.replace('.', '/') + "/" + artifact + "/" + version + "/") != null) throw new IllegalArgumentException("Unsupported coordinate ambiguity: " + base + "; matching requires unique artifactId-version filenames across groupIds");
            if ("pom".equals(value(pom,"packaging"))) pomOnly.add(base);
            if (!"pom".equals(value(pom,"packaging")) && !assets.containsKey(base + ".jar")) throw new IllegalArgumentException("Missing JAR for " + base);
        }
        for (var parent : parentReferences.entrySet()) {
            if (bases.containsKey(parent.getKey()) && (!parent.getValue().equals(bases.get(parent.getKey())) || !pomOnly.contains(parent.getKey())))
                throw new IllegalArgumentException("Included parent POM disagrees with parent reference: " + parent.getKey());
        }
        Map<Path,String> pointers = new LinkedHashMap<>();
        for (var entry : assets.entrySet()) {
            String name = entry.getKey(); String base = bases.keySet().stream().filter(b -> name.equals(b+".pom") || name.equals(b+".jar") || name.matches(java.util.regex.Pattern.quote(b)+"-[A-Za-z0-9_.-]+\\.jar")).max(Comparator.comparingInt(String::length)).orElseThrow(() -> new IllegalArgumentException("Missing matching POM: " + name));
            JsonObject asset = entry.getValue(); String url = asset.get("browser_download_url").getAsString();
            if (!url.matches("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[^/?#\\s]+/[^/?#\\s]+")) throw new IllegalArgumentException("Invalid asset URL: " + url);
            String digest = asset.has("digest") && !asset.get("digest").isJsonNull() ? asset.get("digest").getAsString() : "";
            if (digest.startsWith("sha256:")) { digest = digest.substring(7); if (!digest.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException("Invalid SHA-256: " + name); }
            else { byte[] data = downloaded.get(name); if (data == null) data = downloader.get(URI.create(url)); digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
            Path path = output.toAbsolutePath().resolve("artifacts/" + bases.get(base) + name + ".url");
            for (Path p = path; p != null && p.startsWith(output); p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("Symlink output conflict: " + p);
            if (Files.exists(path)) throw new IOException("Output conflict: " + path);
            pointers.put(path, url + "\n" + digest.toLowerCase(Locale.ROOT) + "\n");
        }
        if (pointers.isEmpty()) throw new IllegalArgumentException("No Maven artifacts found");
        for (var entry : pointers.entrySet()) { Files.createDirectories(entry.getKey().getParent()); Files.writeString(entry.getKey(), entry.getValue(), StandardOpenOption.CREATE_NEW); System.out.println("Generated " + entry.getKey()); }
        System.out.println("Generated " + pointers.size() + " pointers for " + bases.size() + " Maven artifacts.");
    }
}
