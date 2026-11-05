# 更新检查备用清单

应用先读取 GitHub Releases API；遇到 403、429、常见服务器错误或网络故障时，读取同一仓库 `main/updates/releases.json` 的 raw.githubusercontent.com 地址。它不占用 GitHub REST 匿名接口额度，不使用第三方代理或客户端令牌。两条路径均按版本号选取版本，默认排除预发布，开启 Beta 后包含 RC。

清单是公开发布记录的快照，不代表当前 main 的开发代码已经发布。安装包仍下载自 GitHub Releases，并保留大小、SHA-256、应用身份和签名验证。API 和备用源都失败时显示失败，不显示“已是最新版”。

`.github/workflows/update-manifest.yml` 在发布、编辑、删除或改变预发布状态时自动更新清单，也可从 Actions 手动运行。添加或替换 APK 后应确认该工作流成功；如通过 API/GITHUB_TOKEN 发布而未触发新工作流，需手动运行。受分支保护限制时，维护者需通过正常审核合入清单。

本地刷新（无需修改服务器）：

```powershell
python tools/updates/build_manifest.py
git diff -- updates/releases.json
```

应用将各渠道成功结果缓存在内存中五分钟；403/429 后遵守 Retry-After 或 X-RateLimit-Reset，至少一分钟内不重试 REST API，期间仍可读取备用清单。缓存不写磁盘，失败不会当作无更新缓存。
