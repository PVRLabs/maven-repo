#!/usr/bin/env python3
"""Incremental local Maven publisher. Python standard library only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import urllib.request
from datetime import datetime, timezone
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as f:
        name = f.name
        f.write(data)
    os.replace(name, path)

def checked_path(root, relative):
    path = root / relative
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError(f'unsafe destination: {relative}')
    return path

def pointer(relative, text):
    parts = relative.split('/')
    if len(parts) < 4 or any(not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.-]*', p) or p in ('.', '..') for p in parts):
        raise ValueError(f'invalid Maven path: {relative}')
    artifact, version, filename = parts[-3:]
    if version.endswith('-SNAPSHOT') or not re.fullmatch(re.escape(artifact + '-' + version) + r'(?:-[A-Za-z0-9_.-]+)?\.(?:jar|pom)', filename):
        raise ValueError(f'invalid release filename: {relative}')
    fields = text.split()
    if len(fields) != 2 or not re.fullmatch(r'https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[^/?#\s]+/[^/?#\s]+', fields[0]) or not re.fullmatch(r'[a-fA-F0-9]{64}', fields[1]):
        raise ValueError(f'invalid URL pointer: {relative}')
    return fields[0], fields[1].lower()

def validate_pom(relative, data):
    project = ET.fromstring(data)
    namespace = '{http://maven.apache.org/POM/4.0.0}' if project.tag.startswith('{') else ''
    if project.tag != namespace + 'project':
        raise ValueError(f'invalid POM root: {relative}')
    def value(path):
        node = project.find('/'.join(namespace + part for part in path.split('/')))
        return node.text.strip() if node is not None and node.text else None
    parts = relative.split('/')
    expected = dict(groupId='.'.join(parts[:-3]), artifactId=parts[-3], version=parts[-2])
    for key, wanted in expected.items():
        actual = value(key)
        if actual is None and key != 'artifactId':
            actual = value('parent/' + key)
        # Expressions require Maven's model builder; validate only literal values.
        if actual is None or ('${' not in actual and actual != wanted):
            raise ValueError(f'POM {key} inconsistent with destination: {relative}')

def download(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()

def sidecars(path, data):
    for algorithm in ('sha1', 'sha256', 'sha512', 'md5'):
        atomic(Path(str(path) + '.' + algorithm), (hashlib.new(algorithm, data).hexdigest() + '\n').encode())

def publish(destination, source=ROOT, fetch=download, copy_state=False):
    destination = Path(destination)
    if not destination.is_dir():
        raise ValueError('destination must be an existing directory')
    state_path = source / 'published.json'
    state = json.loads(state_path.read_text())
    if state.get('schemaVersion') != 1 or not isinstance(state.get('artifacts'), dict):
        raise ValueError('invalid publication state')
    records = state['artifacts']
    for relative, record in records.items():
        pointer(relative, record['source'] + '\n' + record['sha256'])
        path = checked_path(destination, relative)
        if not path.is_file():
            raise ValueError(f'published artifact missing: {relative}')
    pending = []
    skipped = 0
    for file in sorted((source / 'artifacts').rglob('*.url')):
        relative = file.relative_to(source / 'artifacts').as_posix()[:-4]
        url, digest = pointer(relative, file.read_text())
        if relative in records:
            if records[relative]['sha256'] != digest or records[relative]['source'] != url:
                raise ValueError(f'conflicting published pointer: {relative}')
            skipped += 1
            continue
        path = checked_path(destination, relative)
        if any(Path(str(path) + suffix).exists() for suffix in ('', '.sha1', '.sha256', '.sha512', '.md5')):
            raise ValueError(f'destination already exists: {relative}')
        pending.append((relative, url, digest, path))
    # Verify all downloads before materializing this batch.
    verified = []
    for relative, url, digest, path in pending:
        data = fetch(url)
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError(f'SHA-256 mismatch: {relative}')
        if relative.endswith('.pom'):
            validate_pom(relative, data)  # Preserve the original POM bytes.
        verified.append((relative, url, digest, path, data))
    for relative, url, digest, path, data in verified:
        atomic(path, data)
        sidecars(path, data)
        records[relative] = dict(source=url, sha256=digest, publishedAt=datetime.now(timezone.utc).isoformat())
    for parent in sorted({str(Path(r).parent.parent) for r, *_ in verified}):
        parts = parent.split('/')
        versions = sorted({Path(r).parts[-2] for r in records if str(Path(r).parent.parent) == parent})
        metadata = ET.Element('metadata')
        for key, value in (('groupId', '.'.join(parts[:-1])), ('artifactId', parts[-1])):
            ET.SubElement(metadata, key).text = value
        versioning = ET.SubElement(metadata, 'versioning')
        listing = ET.SubElement(versioning, 'versions')
        for version in versions:
            ET.SubElement(listing, 'version').text = version
        ET.SubElement(versioning, 'lastUpdated').text = datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')
        data = ET.tostring(metadata, encoding='utf-8', xml_declaration=True)
        path = checked_path(destination, parent + '/maven-metadata.xml')
        atomic(path, data)
        sidecars(path, data)
    if verified:
        atomic(state_path, (json.dumps(state, indent=2, sort_keys=True) + '\n').encode())
    if copy_state:
        atomic(destination / 'published.json', state_path.read_bytes())
    print(f'added={len(verified)} skipped={skipped} failed=0')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--copy-state', action='store_true')
    args = parser.parse_args()
    try:
        publish(args.destination, copy_state=args.copy_state)
    except (ValueError, OSError, KeyError, ET.ParseError) as error:
        parser.exit(1, f'failed: {error}\n')
