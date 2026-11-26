# 口袋工具箱 (Pocket Toolbox) - Android 原生源码

基于 **Kotlin + Jetpack Compose + Material 3 (2026 Material You 动态取色)** 构建的模块化安卓工具箱工程。

## 已实现功能

1. **模块化工具箱首页**：卡片式工具入口（`ToolboxHomeScreen`），结构清晰，方便后续继续添加新工具。
2. **Base64 编解码工作台**：
   - 支持标准 Base64 与 URL-Safe Base64 编码/解码。
   - 编码支持不强制分段、76 字符/行（MIME）、64 字符/行（PEM），并可选择保留原文换行结构。
   - 解码自动忽略多余空白换行，若解码内容为 JSON 可自动缩进排版。
   - 结果区支持**行号显示**、**自动换行 / 横向滚动切换**、**双指捏合手势缩放文字大小（8sp–32sp）**、以及**全屏阅读模式**。
3. **主题与 Material You 设置**：
   - 支持**跟随系统 / 浅色模式 / 深色模式**手动切换并本地持久化。
   - 默认开启 **Material You 动态取色**（Android 12+ 自动提取系统壁纸色彩，低版本自动回退至 Material 3 配色方案）。

## 构建 APK 步骤

1. 使用 **Android Studio**（建议 2024.3 / 2025.x 或更新版本）选择 **Open** 打开本文件夹。
2. 等待 Gradle 自动同步依赖完成。
3. 点击顶部菜单 **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** 即可生成可安装的 `.apk` 文件。
