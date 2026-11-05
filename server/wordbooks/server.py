#!/usr/bin/env python3
"""Read-only static wordbook API, including resumable single-range downloads."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit


def handler(public: Path, allowed_suffixes=frozenset({'.json', '.txt'}), service='teawords-wordbooks'):
    public = public.resolve()

    class Handler(BaseHTTPRequestHandler):
        def do_HEAD(self):
            self.serve(head=True)

        def do_GET(self):
            self.serve(head=False)

        def serve(self, head):
            path = unquote(urlsplit(self.path).path)
            if path == '/health':
                payload = json.dumps(dict(ok=True, service=service, schemaVersion=1)).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json; charset=utf-8')
                self.send_header('Content-Length', str(len(payload)))
                self.end_headers()
                if not head:
                    self.wfile.write(payload)
                return
            if path.rstrip('/') == '/api/wordbooks':
                path = '/api/wordbooks/manifest.json'
            target = (public / path.lstrip('/')).resolve()
            try:
                target.relative_to(public)
            except ValueError:
                self.send_error(403)
                return
            if not target.is_file() or target.suffix not in allowed_suffixes:
                self.send_error(404)
                return
            size = target.stat().st_size
            start, end, status = 0, size - 1, 200
            value = self.headers.get('Range')
            if value:
                match = re.fullmatch(r'bytes=(\d+)-(\d*)', value)
                if not match or int(match[1]) >= size or (match[2] and int(match[2]) < int(match[1])):
                    self.send_response(416)
                    self.send_header('Content-Range', f'bytes */{size}')
                    self.send_header('Content-Length', '0')
                    self.end_headers()
                    return
                start, end, status = int(match[1]), min(int(match[2]) if match[2] else end, end), 206
            self.send_response(status)
            mime = {'.json': 'application/json; charset=utf-8', '.mp3': 'audio/mpeg'}
            self.send_header('Content-Type', mime.get(target.suffix, 'text/plain; charset=utf-8'))
            self.send_header('Content-Length', str(end - start + 1))
            self.send_header('Accept-Ranges', 'bytes')
            self.send_header('Cache-Control', 'public, max-age=3600')
            self.send_header('X-Content-Type-Options', 'nosniff')
            if status == 206:
                self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
            self.end_headers()
            if not head:
                try:
                    with target.open('rb') as stream:
                        stream.seek(start)
                        left = end - start + 1
                        while left:
                            data = stream.read(min(left, 64 * 1024))
                            if not data:
                                break
                            self.wfile.write(data)
                            left -= len(data)
                except (BrokenPipeError, ConnectionResetError):
                    pass
    return Handler


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--public', type=Path, default=Path(__file__).resolve().parent / 'public')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8080)
    args = parser.parse_args()
    ThreadingHTTPServer((args.host, args.port), handler(args.public)).serve_forever()
