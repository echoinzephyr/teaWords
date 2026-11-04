import hashlib
from http.server import ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

from server import handler


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'api/wordbooks').mkdir(parents=True)
        self.content = b'{"schemaVersion":1,"wordbooks":[]}'
        (self.root / 'api/wordbooks/manifest.json').write_bytes(self.content)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), handler(self.root))
        self.worker = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.worker.start()
        self.url = f'http://127.0.0.1:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.worker.join()
        self.temp.cleanup()

    def test_health_and_manifest_alias(self):
        with urllib.request.urlopen(self.url + '/health') as response:
            self.assertTrue(json.load(response)['ok'])
        with urllib.request.urlopen(self.url + '/api/wordbooks') as response:
            self.assertEqual(self.content, response.read())

    def test_resume_reconstructs_identical_content(self):
        request = urllib.request.Request(self.url + '/api/wordbooks/manifest.json', headers={'Range': 'bytes=12-'})
        with urllib.request.urlopen(request) as response:
            self.assertEqual(206, response.status)
            self.assertEqual(self.content, self.content[:12] + response.read())

    def test_invalid_range_and_directory_traversal(self):
        for path, headers, expected in [('/api/wordbooks/manifest.json', {'Range': 'bytes=9999-'}, 416), ('/../server.py', {}, 403), ('/api/', {}, 404)]:
            with self.assertRaises(urllib.error.HTTPError) as result:
                urllib.request.urlopen(urllib.request.Request(self.url + path, headers=headers))
            self.assertEqual(expected, result.exception.code)

if __name__ == '__main__':
    unittest.main()
