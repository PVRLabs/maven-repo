"""Independent Maven resolution through a file repository (no live GitHub)."""
import hashlib
import uuid
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from test_publish import p


MAVEN = shutil.which('mvn-lite') or shutil.which('mvn')


@unittest.skipUnless(MAVEN and shutil.which('javac') and shutil.which('java'), 'requires Maven and a JDK')
class MavenConsumerTest(unittest.TestCase):
    def test_parent_and_jar_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source, repo, consumer = root / 'source', root / 'repository', root / 'consumer'
            for path in (source, repo, consumer):
                path.mkdir()
            (source / 'published.json').write_text('{"schemaVersion":1,"artifacts":{}}')
            java = root / 'Greeting.java'
            java.write_text('package fixture; public class Greeting { public static String text() { return "resolved"; } }')
            subprocess.run(['javac', '-d', str(root / 'classes'), str(java)], check=True, capture_output=True)
            jar = root / 'fixture.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.write(root / 'classes/fixture/Greeting.class', 'fixture/Greeting.class')
            group = 'org.pvrlabs.test.run' + uuid.uuid4().hex
            header = '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
            parent = (header + f'<groupId>{group}</groupId><artifactId>parent</artifactId><version>1</version><packaging>pom</packaging></project>').encode()
            child = (header + f'<parent><groupId>{group}</groupId><artifactId>parent</artifactId><version>1</version><relativePath/></parent><artifactId>library</artifactId></project>').encode()
            assets = {'parent/1/parent-1.pom': parent, 'library/1/library-1.pom': child, 'library/1/library-1.jar': jar.read_bytes()}
            downloads = {}
            for relative, data in assets.items():
                url = 'https://github.com/example/fixture/releases/download/v1/' + Path(relative).name
                downloads[url] = data
                pointer = source / 'artifacts' / group.replace('.', '/') / (relative + '.url')
                pointer.parent.mkdir(parents=True, exist_ok=True)
                pointer.write_text(url + '\n' + hashlib.sha256(data).hexdigest())
            p.publish(repo, source, downloads.__getitem__)
            (consumer / 'pom.xml').write_text(header + f'''<groupId>consumer</groupId><artifactId>consumer</artifactId><version>1</version>
<properties><maven.compiler.source>8</maven.compiler.source><maven.compiler.target>8</maven.compiler.target></properties>
<repositories><repository><id>fixture</id><url>{repo.as_uri()}</url></repository></repositories>
<dependencies><dependency><groupId>{group}</groupId><artifactId>library</artifactId><version>1</version></dependency></dependencies></project>''')
            app = consumer / 'src/main/java/Main.java'
            app.parent.mkdir(parents=True)
            app.write_text('import fixture.Greeting; public class Main { public String text() { return Greeting.text(); } }')
            result = subprocess.run([MAVEN, '-B', '-ntp', 'compile'],
                                    cwd=consumer, capture_output=True, text=True, timeout=180)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertTrue((consumer / 'target/classes/Main.class').is_file())
            self.assertEqual(child, (repo / group.replace('.', '/') / 'library/1/library-1.pom').read_bytes())
