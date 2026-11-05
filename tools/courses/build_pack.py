"""Build deterministic, bundled text only. Audio stays outside the APK.

python tools/courses/build_pack.py [--check]
Original VOA material is public domain; see sources and COURSE_CONTENT.md.
"""
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
OUTPUT = ROOT / 'app/src/main/resources/courses'

def normalize(text):
    return ' '.join(re.sub(r"[^\w\s]", ' ', text.lower().replace('’', "'").replace("'", '')).split())

def build():
    originals = json.loads((HERE / 'voa-originals.json').read_text(encoding='utf8'))
    edit = json.loads((HERE / 'editorial.json').read_text(encoding='utf8'))
    lessons = []
    for i, original in enumerate(originals):
        translations = edit['translations'][i].splitlines()
        assert len(translations) == len(original['segments']), (i + 1, 'translation count')
        segments = [dict(id=f's{n:02}', **segment, chinese=chinese)
                    for n, (segment, chinese) in enumerate(zip(original['segments'], translations), 1)]
        def locate(text, answer=None):
            candidates = [s for s in segments if normalize(text) in normalize(s['english'])]
            assert candidates, (i + 1, 'missing locator', text)
            return next((s['id'] for s in candidates if answer and normalize(s['english']) == normalize(answer)), candidates[0]['id'])
        questions = []
        for number, line in enumerate(edit['questions'][i].splitlines(), 1):
            kind, prompt, answers, options, explanation, locator = line.split('|')
            accepted = answers.split('~')
            segment_id = locate(locator, accepted[0] if kind == 'dictation' else None)
            if kind == 'dictation':
                assert normalize(accepted[0]) == normalize(next(s['english'] for s in segments if s['id'] == segment_id)), (i+1, number)
                # Explicitly list contraction equivalents, never accept arbitrary semantic similarity.
                for answer in list(accepted):
                    for short, full in [("I'm", 'I am'), ("It's", 'It is'), ("That's", 'That is')]:
                        if short in answer: accepted.append(answer.replace(short, full))
            questions.append(dict(id=f'q{number:02}', type=kind, prompt=prompt, answers=accepted,
                                  options=options.split('~') if options else [], explanation=explanation, segmentId=segment_id))
        phrases = []
        for entry in edit['phrases'][i].split(';'):
            english, chinese = entry.split('|')
            phrases.append(dict(english=english, chinese=chinese, segmentId=locate(english)))
        explanations = []
        for line in edit['explanations'][i].splitlines():
            title, text, example, locator = line.split('|')
            explanations.append(dict(title=title, text=text, example=example, segmentId=locate(locator)))
        lesson = dict(id=f'voa1-{i+1:02}', number=i+1, unit=i//4+1, title=edit['titles'][i], subtitle=edit['subtitles'][i],
                      minutes=8 if i<2 else 10 if i<11 else 12, objectives=edit['objectives'][i],
                      prerequisites=[f'voa1-{i:02}'] if i else [], skills=['listening','reading','speaking','writing'],
                      source=dict(publisher='VOA Learning English', url=original['url'],
                                  rightsUrl='https://learningenglish.voanews.com/p/6861.html',
                                  credit='课文及原声：VOA Learning English — learningenglish.voanews.com'),
                      audio=original['audio'], segments=segments, keyPhrases=phrases, explanations=explanations, questions=questions)
        assert len(questions) >= 6 and all(sum(q['type']==kind for q in questions)>=2 for kind in ['comprehension','dictation','expression'])
        assert 6 <= len(phrases) <= 10 and 1 <= len(explanations) <= 2
        previous = 0
        for s in segments:
            assert previous <= s['startMs'] < s['endMs'] <= lesson['audio']['durationMs'], (i+1,s)
            previous = s['endMs']
        lessons.append(lesson)
    assert len(lessons) == 12
    return dict(schemaVersion=1,contentVersion='2026.11.05.1', title='入门英语', lessons=lessons)

if __name__ == '__main__':
    data = (json.dumps(build(), ensure_ascii=False, indent=2) + '\n').encode('utf8')
    digest = (hashlib.sha256(data).hexdigest() + '\n').encode()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, content in [('beginner-v1.json', data), ('beginner-v1.sha256', digest)]:
        path = OUTPUT/name
        if '--check' in sys.argv: assert path.read_bytes() == content, f'Outdated course pack: {path}'
        else: path.write_bytes(content)
    print(f'12 lessons / 72 exercises / text {len(data)} bytes; SHA256 {digest.decode().strip()}')
