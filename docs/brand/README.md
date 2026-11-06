# teaWords 图标

采用选定的蓝色「A／文」双对话气泡与茶叶方案，表达英汉查词和语言学习。矢量稿按选定概念图重建轮廓；字形也保存为路径，导出不依赖字体。选定原图保存在 `concepts/11-language-lookup.png`。

- `teawords-logo.svg`：可编辑矢量稿；`teawords-logo.png` 为 512px 导出。
- `teawords.ico`：Windows 多尺寸图标。
- Android 7 使用独立的普通／圆形矢量图标；Android 8 起使用自适应前景与背景；Android 13 起提供单色主题图标。
- 应用内使用 `TeaAppIcon`，背景、双气泡、茶叶和文字采用当前 Material 配色角色，主题切换或 Monet 取色变化会即时更新，文字使用对应的对比色。
- 桌面单色图标由系统桌面的“主题图标”选项着色，和应用内主题设置分别控制。

矢量资源通过 `tools/branding/generate_icons.py` 同步生成。PNG 可由 SVG 导出，ICO 包含 16、24、32、48、64、128、256px。
