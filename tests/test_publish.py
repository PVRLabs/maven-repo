import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('publisher', Path(__file__).parents[1] / 'scripts/publish.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)

class PublisherTests(unittest.TestCase):
    def test_incremental(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, dest = root / 'source', root / 'dest'
            source.mkdir(); dest.mkdir()
            (source / 'published.json').write_text('{"schemaVersion":1,"artifacts":{}}')
            data_template = b'<project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId><artifactId>demo</artifactId><version>1</version><packaging>pom</packaging></project>'
            data = data_template
            def add(version):
                nonlocal data
                data = data_template.replace(b'<version>1</version>', ('<version>' + version + '</version>').encode())
                rel = f'org/example/demo/{version}/demo-{version}.pom'
                file = source / 'artifacts' / (rel + '.url')
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text('https://github.com/example/demo/releases/download/v1/demo.pom\n' + hashlib.sha256(data).hexdigest())
                return rel, file
            rel, file = add('1')
            p.publish(dest, source, lambda _: data)
            before = (dest / rel).stat().st_mtime_ns
            p.publish(dest, source, lambda _: self.fail('redownload'))
            add('2')
            (dest / 'unrelated').write_text('keep')
            p.publish(dest, source, lambda _: data)
            self.assertEqual(before, (dest / rel).stat().st_mtime_ns)
            self.assertEqual(data_template, (dest / rel).read_bytes())
            self.assertEqual('keep', (dest / 'unrelated').read_text())
            self.assertTrue((dest / (rel + '.sha256')).exists())
            self.assertEqual(2, len(json.loads((source / 'published.json').read_text())['artifacts']))
            file.write_text(file.read_text().replace(hashlib.sha256(data_template).hexdigest(), '0'*64))
            with self.assertRaisesRegex(ValueError, 'conflicting'):
                p.publish(dest, source)
            file.unlink()
            (dest / rel).unlink()
            with self.assertRaisesRegex(ValueError, 'missing'):
                p.publish(dest, source)

    def test_invalid_pointers(self):
        for path, text in [('org/x/a/1/a-1.jar', 'http://example.com/x ' + '0'*64), ('../a/1/a-1.jar', ''), ('org/x/a/1/wrong.jar', '')]:
            with self.assertRaises(ValueError):
                p.pointer(path, text)

    def test_pom_coordinates(self):
        path = 'org/example/demo/1/demo-1.pom'
        inherited = b'<project><parent><groupId>org.example</groupId><version>1</version></parent><artifactId>demo</artifactId></project>'
        p.validate_pom(path, inherited)
        for old, new in [(b'demo', b'other'), (b'org.example', b'wrong'), (b'<version>1', b'<version>2')]:
            with self.assertRaisesRegex(ValueError, 'inconsistent'):
                p.validate_pom(path, inherited.replace(old, new))

    def test_history_is_not_read(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'artifacts').mkdir()
            relative = 'org/example/demo/1/demo-1.jar'
            artifact = root / relative
            artifact.parent.mkdir(parents=True)
            artifact.write_bytes(b'historical')
            (root / 'published.json').write_text(json.dumps({'schemaVersion': 1, 'artifacts': {relative: {
                'source': 'https://github.com/example/demo/releases/download/v1/demo.jar', 'sha256': '0'*64}}}))
            with patch.object(Path, 'read_bytes', side_effect=AssertionError('historical read')):
                p.publish(root, root)
