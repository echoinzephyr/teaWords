from http.server import ThreadingHTTPServer
from pathlib import Path
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from server import handler

class CourseServerTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.root=Path(self.temp.name)
        audio=self.root/'api/courses/audio';audio.mkdir(parents=True)
        self.content=b'ID3-original-audio-fixture'
        (audio/'test.mp3').write_bytes(self.content)
        (audio/'private.py').write_text('not public')
        self.server=ThreadingHTTPServer(('127.0.0.1',0),handler(self.root))
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
        self.url=f'http://127.0.0.1:{self.server.server_port}'
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
    def test_audio_mime_range_and_head(self):
        request=urllib.request.Request(self.url+'/api/courses/audio/test.mp3',headers={'Range':'bytes=4-'})
        with urllib.request.urlopen(request) as response:
            self.assertEqual(206,response.status)
            self.assertEqual('audio/mpeg',response.headers['Content-Type'])
            self.assertEqual(self.content,self.content[:4]+response.read())
        with urllib.request.urlopen(urllib.request.Request(self.url+'/api/courses/audio/test.mp3',method='HEAD')) as response:
            self.assertEqual(str(len(self.content)),response.headers['Content-Length'])
            self.assertEqual(b'',response.read())
    def test_private_files_and_parent_paths_are_unavailable(self):
        for path in ['/api/courses/audio/private.py','/api/courses/','/../server.py']:
            with self.assertRaises(urllib.error.HTTPError):urllib.request.urlopen(self.url+path)

if __name__=='__main__':unittest.main()
