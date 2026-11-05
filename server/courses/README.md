# 入门英语原声镜像

仅在本地准备，本轮不连接或配置阿里云。应用默认直接从 VOA 的 HTTPS 地址下载原声。

在仓库根目录运行 `python server/courses/build_data.py`，逐项下载并核对课程包固定的 SHA-256 和字节数，输出到 `tools/out/courses-server`，同时生成 `tools/out/teawords-courses-server.tar.gz`。已有下载可传入 `--source 目录`（文件命名为 `01.mp3` 至 `12.mp3`）。音频不进入 App。

本地启动：`python server/courses/server.py --public tools/out/courses-server/public --port 8081`。解压交付包后可直接运行 `python server.py`，默认只监听 `127.0.0.1:8081`。只读接口支持 HEAD、单段 Range 续传，不接收用户数据，不列出目录：

- `/health`
- `/api/courses/beginner-v1.json` 和 `.sha256`
- `/api/courses/audio/{sha256}.mp3`

以后准备好域名和 HTTPS，再由现有反向代理转发 `/api/courses/`。构建客户端时可设置 `-PcourseBaseUrl=https://域名`，媒体下载将使用镜像，课程仍保存原始 VOA 来源并校验相同文件。服务器包没有安装脚本，不会改防火墙、SSH、系统服务或现有站点。

来源、内容编排及校对记录见包内 `COURSE_CONTENT.md`。不包含第三方新闻素材或新概念教材、录音。
