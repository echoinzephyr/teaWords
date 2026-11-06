<p align="center">
  <img src="docs/brand/teawords-logo.png" width="104" alt="茶词：A 与文组成的双语对话气泡和茶叶" />
</p>

<h1 align="center">teaWords · 茶词</h1>

<p align="center">查词、翻译，也把遇见的单词记下来。</p>

茶词是一款使用 Kotlin、Jetpack Compose 和 Material 3 构建的 Android 英语学习应用，支持 Android 7.0 及以上。

## 下载与更新

**当前正式版：[1.2.3](https://github.com/echoinzephyr/teaWords/releases/tag/v1.2.3)**。在发布页下载 APK 安装，也可打开 [最新正式发布](https://github.com/echoinzephyr/teaWords/releases/latest)。

在应用的「设置 → 检查更新」中检查和下载安装包。希望接收 Beta、RC 等预发布版本时，开启「接收 Beta 更新」。下载完成后由 Android 确认安装；使用相同签名覆盖更新可保留应用数据。

## 用茶词做什么

### 查词与翻译

搜索单词时保留已有本地结果，联网补充有道释义、英美音标、短语、词形与搜索建议。查询历史保存查询文字和本地词条关联，在线释义最多在进程内缓存 64 条，不新增在线结果磁盘缓存。

句子和长文默认使用 MyMemory，无需配置密钥；也可使用自己的 DeepSeek API 密钥。支持从其他应用分享或选择文本，确认后再查询。在线功能受网络和服务额度影响；翻译原文会发送给所选服务商。

### 分轮学习

默认每组 5 个词，也可调整每轮词数。整组分三遍完成：

1. **先认读**：看单词、选「认识／不认识」，再查看释义、听发音和学习搭配。
2. **再辨认**：认读完整组后，穿插进行搭配含义或词义辨认。搭配题的全部选项均来自真实搭配；同类选项不足时使用词义辨认题。
3. **最后拼写**：根据释义回忆英文，答错或看过提示的词回看后再练。

辨认题作答后保留所有选项，展示各项对应的英文或释义、正确项及你的选择，手动点击「下一题」继续。

顶部统一记录整组已完成步骤：5 个词共 15 步，每完成一步加 1，答错、提示和重复提交不加数。三个进度点表示本词的实际完成步骤，结算再显示完成词数。每步进度保存到本机，可暂停续学。认读后的巩固属于辅助学习，不冒充三次独立回忆或永久掌握。

学习页支持下拉查词、左滑拼写，以及发音、收藏和笔记。主动划入的拼写单独更新复习模型，不增加本组步骤；刚看过答案或用过提示的作答按辅助练习记录。「专项练习」另有混合小练习和打字记词；「回望」用于巩固查过的生词。

### 词书与记录

支持词书分类、搜索和多选，以及个人词库导入。已下载词书可离线学习；学习进度、查询历史、收藏与笔记保存在本机。统计与词汇诊断根据实际记录展示，记忆调度使用 FSRS-4.5 默认参数，尚未进行个人参数拟合。

**词书下载服务目前尚未配置公共地址。** 已有词书、个人导入及在线查词可使用；全新安装暂不能直接下载公共词书。自建服务方法见下方部署说明。安装包不包含完整词典，也不需要下载设备端翻译模型。

### 外观与操作

- 浅色、深色、跟随系统和纯黑模式。
- Android 12 起支持 Material Monet 动态取色；开启应用动态取色后，桌面图标同步采用系统配色，刷新速度取决于启动器。
- Bing 每日壁纸、自定义壁纸、首页文字及实时主题预览。
- 跟随系统字号或自定义文字缩放，保留浮动导航。
- Android 14 起可开启向下预览的预测性返回。

发音优先使用词典音频，不可用时尝试所选口音的系统语音；系统语音资源是否可用取决于设备和已安装语音包。

## 构建

使用 Android Studio 打开仓库根目录，准备 **JDK 21** 和 **Android SDK Platform 37**，通过工程自带的 Gradle Wrapper 构建：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。连接真机后可运行 `:app:connectedDebugAndroidTest` 验证设备界面与数据流程。

发布构建运行 `:app:assembleRelease`，启用代码和资源压缩。使用环境变量提供签名：

- `TEAWORDS_KEYSTORE`：密钥库路径。
- `TEAWORDS_STORE_PASSWORD`：密钥库密码。
- `TEAWORDS_KEY_ALIAS`：密钥别名，默认 `teawords`。
- `TEAWORDS_KEY_PASSWORD`：私钥密码。

未提供签名时，发布产物为未签名 APK。GitHub 安装包使用发布签名，无法直接覆盖 Android Studio 的调试签名版本；卸载应用会清除其本机数据。

## 自建词书服务

服务端、数据生成器及部署步骤见 [词书服务说明](server/wordbooks/README.md)。服务仅提供词书目录与下载，不保存用户学习记录。

HTTPS 服务就绪后，构建时指定地址：

```powershell
.\gradlew.bat :app:assembleRelease -PwordbookBaseUrl=https://your-domain.example
```

词书下载支持校验和检查与中断重试。客户端词条身份保持稳定，切换词书不会重新分配已有单词的学习身份。更新备用清单的维护方式见 [更新服务说明](updates/README.md)。

## 反馈与许可

遇到问题可在 [Issues](https://github.com/echoinzephyr/teaWords/issues) 中反馈，附上应用版本、Android 版本及复现步骤；应用设置中提供诊断日志入口。

应用使用 [GPL-3.0](LICENSE) 许可。词书采用 [ECDICT](https://github.com/skywind3000/ECDICT) 数据，其 [MIT 许可](tools/data/ecdict-LICENSE) 和 [数据说明](tools/data/ecdict-README.md) 随仓库保留。考试分类不代表官方最新完整大纲。

teaMeow Technology
