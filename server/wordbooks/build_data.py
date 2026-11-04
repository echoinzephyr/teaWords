#!/usr/bin/env python3
"""Build immutable WordMoment-compatible JSON books and the compact Android identity index."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sqlite3


def build(reference: Path, output: Path, assets: Path | None = None):
    source = sqlite3.connect(f"file:{reference.resolve().as_posix()}?mode=ro", uri=True)
    source.row_factory = sqlite3.Row
    output.mkdir(parents=True, exist_ok=True)
    api = output / "api" / "wordbooks"
    api.mkdir(parents=True, exist_ok=True)
    generated = source.execute("SELECT value FROM dictionary_meta WHERE key='built_at'").fetchone()[0]
    attribution = {"name": "ECDICT", "license": "MIT", "projectUrl": "https://github.com/skywind3000/ECDICT",
                   "licenseUrl": "https://github.com/skywind3000/ECDICT/blob/master/LICENSE"}
    summaries = []
    for book in source.execute("SELECT * FROM word_books ORDER BY id"):
        entries = []
        for row in source.execute("SELECT w.* FROM lex_words w JOIN word_book_entries b ON w.id=b.word_id WHERE b.book_id=? ORDER BY w.normalized", (book['id'],)):
            translations = [x.strip() for x in row['zh'].replace('\\n', '\n').splitlines() if x.strip()]
            entries.append(dict(word=row['word'], phonetic=row['phonetic'], translations=translations,
                                english=row['en'], forms=row['forms'], frequency=row['frequency'], tags=row['tags'].split()))
        detail = dict(schemaVersion=1, id=book['id'], title=book['name'], subtitle=book['exam_type'],
                      stage=book['category'], description=book['description'], wordCount=len(entries),
                      generatedAt=generated, source=attribution, words=entries)
        payload = json.dumps(detail, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        (api / f"{book['id']}.json").write_bytes(payload)
        summaries.append(dict(id=book['id'], title=book['name'], subtitle=book['exam_type'], stage=book['category'],
                              description=book['description'], wordCount=len(entries), jsonUrl=f"/api/wordbooks/{book['id']}.json",
                              jsonSha256=hashlib.sha256(payload).hexdigest(), sourceName='ECDICT', sourceLicense='MIT'))
    manifest = json.dumps(dict(schemaVersion=1, generatedAt=generated, source=attribution, wordbooks=summaries),
                          ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    (api / 'manifest.json').write_bytes(manifest)
    license_file = Path(__file__).resolve().parents[2] / 'tools/data/ecdict-LICENSE'
    shutil.copyfile(license_file, output / 'ECDICT-LICENSE.txt')
    if assets:
        assets.mkdir(parents=True, exist_ok=True)
        (assets / 'wordbooks-manifest.json').write_bytes(manifest)
        # Atomic replacement keeps an interrupted generator from damaging the previous seed.
        temp = assets / 'dictionary-index.db.building'
        if temp.exists():
            raise FileExistsError(f'Previous build exists; inspect it before retrying: {temp}')
        db = sqlite3.connect(temp)
        for row in source.execute("SELECT name,sql FROM sqlite_master WHERE type='table' AND sql IS NOT NULL"):
            if not row['name'].startswith('lex_fts_'):
                db.execute(row['sql'])
        for row in source.execute("SELECT sql FROM sqlite_master WHERE type='index' AND sql IS NOT NULL"):
            db.execute(row['sql'])
        db.execute('CREATE TABLE word_identity(id INTEGER PRIMARY KEY, normalized TEXT NOT NULL UNIQUE)')
        db.executemany('INSERT INTO word_identity VALUES(?,?)', source.execute('SELECT id,normalized FROM lex_words'))
        for table in ['dictionary_meta', 'word_books', 'lex_sources']:
            rows = source.execute(f'SELECT * FROM {table}').fetchall()
            if rows:
                db.executemany(f'INSERT INTO {table} VALUES({",".join("?" for _ in rows[0])})', rows)
        db.execute("UPDATE dictionary_meta SET value='0' WHERE key='word_count'")
        db.execute("INSERT INTO dictionary_meta VALUES('data_mode','downloaded')")
        db.execute('CREATE TABLE downloaded_books(book_id TEXT PRIMARY KEY, sha256 TEXT NOT NULL, downloaded_at INTEGER NOT NULL)')
        db.commit()
        db.execute('VACUUM')
        db.close()
        temp.replace(assets / 'dictionary-index.db')
    source.close()
    return summaries


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, default=Path('tools/dictionary/reference/dictionary.db'))
    parser.add_argument('--output', type=Path, default=Path('tools/out/wordbooks/public'))
    parser.add_argument('--write-assets', action='store_true')
    args = parser.parse_args()
    books = build(args.reference, args.output, Path('app/src/main/assets') if args.write_assets else None)
    print(f'Built {len(books)} books / {sum(b["wordCount"] for b in books)} entries (overlap included)')
