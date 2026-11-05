"""Read-only course mirror. Local preparation only; TLS belongs to a future reverse proxy."""
import argparse
import importlib.util
from http.server import ThreadingHTTPServer
from pathlib import Path

common = Path(__file__).with_name('static_server.py')
if not common.exists():
    common = Path(__file__).resolve().parents[1] / 'wordbooks/server.py'
spec = importlib.util.spec_from_file_location('course_static_server', common)
static = importlib.util.module_from_spec(spec)
spec.loader.exec_module(static)

def handler(public):
    return static.handler(public, allowed_suffixes=frozenset({'.json', '.txt', '.mp3', '.sha256'}), service='teawords-courses')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--public', type=Path, default=Path(__file__).with_name('public'))
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8081)
    args = parser.parse_args()
    ThreadingHTTPServer((args.host,args.port), handler(args.public)).serve_forever()
