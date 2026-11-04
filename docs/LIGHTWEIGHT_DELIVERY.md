# 轻量安装包与在线查词

本轮把完整词典移出安装包，保留稳定编号索引；八本 ECDICT 词书采用 WordMoment 兼容的目录及 JSON 格式按需下载。下载带 SHA-256 和词数校验，支持 Range 续传，校验后通过事务导入，已有学习记录与词书不会因下载失败而删除。完整参考数据库保存在 tools/dictionary/reference，仅用于生成数据和测试。

有道 jsonapi/suggest 为在线补充，先显示本地结果；在线释义就绪后只显示一张词条详情卡。在线结果最多在进程内缓存 64 条，不新增磁盘缓存。输入变化或离开页面会取消请求；查看词条不写入学习证据，远端结果不冒用本地词条编号。旧磁盘缓存仍可读取。

移除 ML Kit 设备端翻译依赖、模型管理入口及重复四六级 TXT；旧 DEVICE 设置自动回退至 MyMemory，提示一次。保留 MyMemory 与 DeepSeek。发布构建启用 R8 与资源压缩，Gson 使用的反射模型保留必要字段。

学习首页移除重复的顶部个人入口，底部浮动导航保持原样。词书封面使用轻量绘制和完整编号，避免缩写被折行拆开；关于页不展示 WordMoment 独立卡片。接口结构参考 WordMoment（https://github.com/Cydiacoft/wordmoment，GPLv3）；ECDICT 保留 MIT 许可。网站接口的可访问性不等于数据授权，在线数据不作为批量离线词库发布。

## 服务端交付与边界

依用户最新要求，未部署、上传或配置阿里云。server/wordbooks 包含只读服务、确定性数据生成器、部署脚本及说明。本地交付包位于 validation/size-reduction/teawords-wordbooks-server.tar.gz。默认客户端词书地址为空，等待域名与 HTTPS 就绪后使用 -PwordbookBaseUrl 配置；已有词书和个人导入仍可使用。部署脚本不删除、覆盖既有目录，不修改防火墙或 SSH，不重启服务器。

## 验证与备份

163 项单元测试通过；Lint、调试构建及启用压缩的构建通过。OnePlus LE2101（Android 14）执行页面、设置、导航、学习与短课回归；另执行真实下载、续传、离线词形、固定编号迁移及会话持久化测试。无虚拟机，未发布正式版本。调试包使用 .materialreview，压缩验证包使用 .slimreview，均与正式应用分开。

基线工作区（包含原有新增文件）保存在 validation/size-reduction/workspace-before.zip，文件校验见 baseline-sha256.json。构建、测试日志和体积对比均在同目录。安装包与服务端交付包不提交到 Git。正式源码推送目标为 https://github.com/echoinzephyr/teaWords 的 main。
