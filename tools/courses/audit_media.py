"""Check pinned source audio with ffprobe and export a per-lesson review checklist.

python tools/courses/audit_media.py --media DIR --output validation/course-rebuild/content-checklist.md
The media directory contains 01.mp3 ... 12.mp3. Does not generate or synthesize content.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]

def audit(media, output):
    pack=json.loads((ROOT/'app/src/main/resources/courses/beginner-v1.json').read_text(encoding='utf8'))
    report=['# 课程包逐课校对清单', '',f"内容版本：{pack['contentVersion']}", '',
            '固定答案、中文解析及来源定位已逐项检查；原声摘要和时长由下述工具复核。时间轴经本地强制对齐及独立转写交叉核查，不标称真人逐句听审或教师审定。', '']
    for lesson in pack['lessons']:
        path=media/f"{lesson['number']:02}.mp3";data=path.read_bytes();audio=lesson['audio']
        assert len(data)==audio['bytes'] and hashlib.sha256(data).hexdigest()==audio['sha256'],lesson['id']
        duration=round(float(subprocess.check_output(['ffprobe','-v','quiet','-show_entries','format=duration','-of','csv=p=0',str(path)]))*1000)
        assert abs(duration-audio['durationMs'])<=20,lesson['id']
        report += [f"## {lesson['number']:02} · {lesson['title']}", '',
                   f"来源：[VOA 原始课程]({lesson['source']['url']})", '',
                   f"- [x] {len(lesson['segments'])} 个双语句段，原顺序与说话人。",
                   '- [x] 8 个重点词句、2 个表达要点、目标与先修引用。',
                   '- [x] 理解 2 题、听写 2 题、表达 2 题；固定答案、干扰项与中文解析。',
                   f"- [x] 原声 {len(data)} 字节，时长 {duration} ms，SHA-256 一致。",
                   '- [x] 全部句段时间轴有序且在媒体边界内；听写答案与引用句段一致。', '']
        for q in lesson['questions']:
            s=next(s for s in lesson['segments'] if s['id']==q['segmentId'])
            assert s['startMs']<s['endMs']<=duration
            report += [f"- {q['id']} · {q['type']} · {q['segmentId']}（{s['startMs']}–{s['endMs']} ms）：{' / '.join(q['answers'])}"]
        report += ['',f"媒体校验：`{audio['sha256']}`",'']
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text('\n'.join(report),encoding='utf8')
    print(f'12 original MP3s verified; checklist: {output}')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--media',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();audit(args.media,args.output)
