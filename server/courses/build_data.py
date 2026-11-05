"""Prepare a pinned VOA course mirror and portable server archive. Never deploys remotely."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tarfile
import urllib.request

ROOT=Path(__file__).resolve().parents[2]

def build(source=None):
    destination=ROOT/'tools/out/courses-server'
    public=destination/'public/api/courses'
    (public/'audio').mkdir(parents=True,exist_ok=True)
    pack_path=ROOT/'app/src/main/resources/courses/beginner-v1.json'
    pack=json.loads(pack_path.read_text(encoding='utf8'))
    for lesson in pack['lessons']:
        audio=lesson['audio']; target=public/'audio'/f"{audio['sha256']}.mp3"
        if source:
            data=(source/f"{lesson['number']:02}.mp3").read_bytes()
        elif target.exists(): data=target.read_bytes()
        else:
            with urllib.request.urlopen(audio['url'],timeout=60) as response: data=response.read(audio['bytes']+1)
        assert len(data)==audio['bytes'] and hashlib.sha256(data).hexdigest()==audio['sha256'], lesson['id']
        target.write_bytes(data)
    shutil.copy2(pack_path,public/pack_path.name)
    shutil.copy2(pack_path.with_suffix('.sha256'),public/pack_path.with_suffix('.sha256').name)
    for name in ['server.py','README.md']:shutil.copy2(Path(__file__).with_name(name),destination/name)
    shutil.copy2(ROOT/'server/wordbooks/server.py',destination/'static_server.py')
    shutil.copy2(ROOT/'COURSE_CONTENT.md',destination/'COURSE_CONTENT.md')
    archive=destination.parent/'teawords-courses-server.tar.gz'
    with tarfile.open(archive,'w:gz') as tar:tar.add(destination,arcname='teawords-courses')
    print(f'12 verified audio files; {archive} ({archive.stat().st_size} bytes)')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,help='Optional folder containing verified 01.mp3 ... 12.mp3')
    build(parser.parse_args().source)
