# 含课程的开发快照

该快照保存从 main 347653c 延续的课程功能，以及移除前尚未提交的教学流程改动。包含十二课文本、原声时间轴、96 道固定练习、课程播放器、录音、独立进度、内容生成工具、测试及课程静态服务。新教学流程已完成源码编译和构建检查，尚未完成这一轮真机验收；既有交付文档中的 14 项课程验收记录对应上一版流程。

原始 VOA 教学 MP3 完整保存在 `course-archive/media/01.mp3` 至 `12.mp3`。摘要与原始来源见 `tools/courses/voa-originals.json`，使用范围见 `COURSE_CONTENT.md`。可以执行 `python server/courses/build_data.py --source course-archive/media` 重新生成本地服务器包，不依赖在线地址重新取得音频。

教学源文件为 `tools/courses/teaching_source.py`，运行 `python tools/courses/build_pack.py` 生成固定 JSON 课程包。本分支用于保留课程代码和素材，不是版本发布，也没有部署阿里云。
