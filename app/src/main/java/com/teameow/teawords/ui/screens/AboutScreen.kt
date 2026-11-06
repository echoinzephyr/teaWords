package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.UpdateChecker

@Composable
fun AboutIdentity() {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TeaAppIcon()
        Text("茶词 · teaWords", style = MaterialTheme.typography.headlineMedium)
        Text("版本 ${installedVersionName()}", style = MaterialTheme.typography.bodyMedium)
        TeaCaption("查词、翻译，慢慢记住。")
    }
}

@Composable
fun AboutCredits() {
    val uri = LocalUriHandler.current
    var libraries by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TeaCard {
            Text("用茶词做什么", style = MaterialTheme.typography.titleLarge)
            Text("查词 · 查询已下载词书，联网补充释义、音标和短语。")
            Text("翻译 · 中英句子与长文翻译，支持复制译文。")
            Text("回望 · 巩固查过的生词，暂不包含句子。")
            Text("学习 · 选择词书，快速排雷，再通过回忆练习巩固。")
        }
        TeaCard {
            Text("数据与翻译服务", style = MaterialTheme.typography.titleLarge)
            AboutSource("ECDICT", "按需下载词书 · MIT 许可证", "https://github.com/skywind3000/ECDICT")
            AboutSource("有道词典", "在线释义、音标和搜索建议", "https://dict.youdao.com/")
            AboutSource("MyMemory", "免配置在线翻译，有每日免费额度", "https://mymemory.translated.net/doc/usagelimits.php")
            AboutSource("DeepSeek", "可选长文翻译 · 需本人 API 密钥并按用量计费", "https://api-docs.deepseek.com/quick_start/pricing/")
            TeaCaption("查词使用本地词书并向有道请求在线补充；在线补充只在内存中缓存。在线翻译会把原文发送给所选服务商。")
            TeaCaption("查阅记录与学习进度保存在本机，可在查询历史中关闭记录。外部应用分享的输入不保存查询历史。")
        }
        TeaCard {
            Text("项目与反馈", style = MaterialTheme.typography.titleLarge)
            Text("遇到问题或有新想法，欢迎到项目仓库反馈。")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { uri.openUri(UpdateChecker.PAGE_URL) }, modifier = Modifier.weight(1f)) { Text("项目源码") }
                OutlinedButton(onClick = { uri.openUri("${UpdateChecker.PAGE_URL}/issues") }, modifier = Modifier.weight(1f)) { Text("反馈问题") }
            }
            TextButton(onClick = { uri.openUri("${UpdateChecker.PAGE_URL}/blob/main/LICENSE") }) { Text("应用开源许可 · GPL-3.0") }
        }
        TeaCard {
            TextButton(onClick = { libraries = !libraries }) { Text(if (libraries) "收起开源组件与致谢" else "开源组件与致谢") }
            if (libraries) {
                AboutSource("KernelSU Style UI Kit", "Material 页面与分组列表设计参考 · GPL-3.0", "https://github.com/chenaizhang/KernelSU-Style-UI-Kit")
                Text("Jetpack Compose / Material 3 · 界面与交互")
                Text("OkHttp / Gson · 网络请求与数据解析")
                Text("Coil · 图片加载")
                AboutSource("Bing 每日壁纸", "可选的首页背景", "https://www.bing.com")
                AboutSource("Hitokoto 一言", "可选的首页文字", "https://hitokoto.cn")
            }
        }
        TeaCaption("teaMeow Technology")
    }
}

@Composable
private fun AboutSource(name: String, summary: String, url: String) {
    val uri = LocalUriHandler.current
    TeaSegmentedGroup {
        TeaOptionRow(title = name, summary = summary, onClick = { uri.openUri(url) })
    }
}
