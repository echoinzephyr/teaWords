# teaWords 词书下载服务

服务只提供公开的 ECDICT 词书，不接收查词原文、用户账号或学习记录。接口兼容 WordMoment 的目录和 JSON 词书格式；所有程序在本地准备，本轮未部署或修改阿里云。

## 构建数据

在仓库根目录运行：

```powershell
python server/wordbooks/build_data.py
```

输出为 `tools/out/wordbooks/public`。它由 `tools/dictionary/reference/dictionary.db` 生成，包含八本原有词书及 SHA-256 校验值。完整数据库仅用于数据生成和测试，不进入应用安装包。更新数据时加 `--write-assets`，同步应用内的固定编号索引和目录，然后重新构建应用。

## 本地验证

```powershell
python server/wordbooks/server.py --public tools/out/wordbooks/public --port 8765
python -m unittest discover -s server/wordbooks -p test_server.py -v
```

接口：`GET /health`、`GET /api/wordbooks/manifest.json`、`GET /api/wordbooks/{id}.json`；词书文件支持 HEAD 和单段 Range 续传。不存在上传接口，不列出目录，不开放生成器或数据库文件。

## 日后部署到 Linux

交付包包含 `server.py`、`install.sh`、`public/` 和本说明。准备域名与 HTTPS 后，将包解压到新的临时目录，审阅 `install.sh`，再执行 `sudo sh install.sh`。它仅新建 `/srv/teawords-wordbooks/v1` 和 `teawords-wordbooks.service`，遇到已有目录或同名服务会退出，不覆盖、不删除。服务默认只监听 `127.0.0.1:8080`，由现有 HTTPS 站点转发；脚本不会安装软件、修改防火墙或 SSH，也不会重启服务器。

反向代理示例（在域名、证书和现有站点安排确定后再手动合并）：

```nginx
location /api/wordbooks/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
}
location = /health {
    proxy_pass http://127.0.0.1:8080;
}
```

日常检查：`systemctl status teawords-wordbooks`、`journalctl -u teawords-wordbooks --since today`。如需停用，可运行 `systemctl disable --now teawords-wordbooks`；此命令不删除文件。升级数据前保留旧版本并同步发布应用中的目录校验值，避免客户端与服务端版本不一致。

## 客户端地址

构建时传入 `-PwordbookBaseUrl=https://你的域名`。本轮交付包留空，界面会说明词书服务尚未上线；已有词书和个人导入仍可离线使用。正式包只接受 HTTPS。调试包仅为真机本地验证允许 localhost/127.0.0.1 的 HTTP，不允许任意明文地址。

应用内目录固定了各词书的 SHA-256；远程目录也必须与应用内目录一致。下载校验和词数验证完成后，在事务中导入，失败保留旧词书。下载中断保留片段，下次继续；目录更新需要同步更新应用。

## 来源

- ECDICT，MIT；发布文件中附 `ECDICT-LICENSE.txt`。
- WordMoment 的在线词书接口结构与查词接入方式作为参考，[WordMoment](https://github.com/Cydiacoft/wordmoment)，GPLv3。本服务代码独立实现，不包含教材提取数据。
