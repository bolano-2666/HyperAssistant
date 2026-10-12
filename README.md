# 超灵助手 (HyperAssistant) - HyperOS 3.0 小爱同学改造指南

针对 **Redmi K90 至尊版 (Xiaomi HyperOS 3.0.309.0 whpcnxm.C11)** 定制研发的 AI 语音交互接管系统。

将长按电源键原本的小爱同学无缝替换为类似 **Apple Siri (Apple Intelligence)** 风格的 **Google Gemini 3 / ChatGPT** 全能语音助手，同时支持**迷雾通 (Geph) 专属联动**与**免科学上网反代直连**双通道。

---

## 核心特性

1. **毫秒级接管原生小爱同学 (无需 Root / 解锁 BL)**：
   * 基于专用无障碍监听引擎，在电源键长按唤醒 `com.miui.voiceassist` 的 30~50 毫秒内瞬间静默收起小爱，无缝唤起 Gemini 3 Siri 交互界面。
2. **Apple Intelligence (iOS 18 Siri) 标志性流光视觉**：
   * 屏幕边缘全彩动态流光呼吸动效（Cyan-Purple-Magenta-Amber）。
   * 动态声波律动（实时响应说话分贝），支持实时流式打断（Barge-in）。
   * 底部多模态按钮：支持语音对话、键盘打字输入与屏幕内容识图分析。
3. **搭载最新 Gemini 3 系列与 ChatGPT**：
   * 默认支持 `gemini-3.1-pro`、`gemini-3-flash`、`gemini-2.5-flash` 与 `gpt-4o`、`o3-mini`。
   * 支持流式 SSE 逐字返回与逐句 TTS 朗读。
4. **双通道网络机制（迷雾通专属联动 + 免翻墙反代）**：
   * **迷雾通已开启**：自动直连官方 API，零中转延迟。
   * **迷雾通未开启**：自动秒切至内置 Cloudflare Worker 反向代理，国内三大运营商网络免翻墙直连。
   * **语音与一键联动**：可直接在设置界面或指令中一键拉起迷雾通客户端。

---

## Redmi K90 至尊版 (HyperOS 3.0) 专属配置步骤

### 第一步：设置系统按键动作
1. 打开手机 **「设置」 -> 「更多设置」 -> 「手势及快捷方式」**。
2. 找到 **「长按电源键 0.5 秒」**，确保勾选为 **「小爱同学」**（这是接管触发源）。

---

### 第二步：授予关键权限（非常关键，防止被系统清理）
安装并在手机上打开 **「超灵助手」**：

1. **开启无障碍服务**：
   * 点击应用内的 **「开启「小爱接管」无障碍服务」**。
   * 跳转至系统设置 -> 「已下载的应用」 -> 找到 **「超灵助手」** -> 开启开关。
   * *(注：HyperOS 可能会弹出受限设置提示，在系统应用信息中点击右上角三个点，选择「允许受限设置」即可)*。
2. **授予悬浮窗权限**：
   * 点击 **「开启悬浮窗 / 显示在其他应用上层」** -> 勾选允许。
3. **后台保活与防杀后台设置**：
   * 在手机任务管理界面（多任务后台），找到「超灵助手」卡片，**长按并点击小锁图标（锁定后台）**。
   * 在手机应用设置中，将 **「省电策略 / 电池优化」** 设置为 **「无限制」**。
   * 开启 **「自启动」** 与 **「关联启动」** 权限。

---

### 第三步：配置 AI 模型与 API Key
在主界面中填写：
1. **模型代号**：
   * 若使用 Gemini 3，默认填写 `gemini-3.1-pro` 或 `gemini-3-flash`。
   * 若使用 ChatGPT，选择 OpenAI 并填写 `gpt-4o` 或 `o3-mini`。
2. **API Key**：
   * 填入你的 Google Gemini API Key 或 OpenAI Key。
3. **反代地址 (免翻墙通道)**：
   * 如果手机开启了迷雾通，该项可留空或保留默认。
   * 若平时不想一直开着迷雾通，请按下方教程部署一个 Cloudflare Worker，并将生成的 URL（例如 `https://your-cf-name.workers.dev`）粘贴在此处。
4. 点击底部的 **「保存配置并测试唤起 Siri 助手」** 验证效果。

---

## 免科学上网 Cloudflare Worker 部署教程 (1 分钟极速搞定)

本项目根目录已包含完整脚本 `cloudflare_worker_proxy.js`。

1. 浏览器打开 [Cloudflare 官网](https://dash.cloudflare.com/) 注册并登录（完全免费）。
2. 在左侧菜单点击 **「Workers 和 Pages」** -> 点击 **「创建应用程序 (Create application)」** -> 选择 **「创建 Worker」**。
3. 给 Worker 命名（例如 `my-gemini-proxy`），点击 **「部署 (Deploy)」**。
4. 部署后点击 **「编辑代码 (Edit code)」**：
   * 将编辑器里的默认代码全部清空。
   * 将本项目中的 `cloudflare_worker_proxy.js` 全部代码粘贴进去。
   * 点击右上角 **「保存并部署 (Save and deploy)」**。
5. 复制分配的域名（例如 `https://my-gemini-proxy.youraccount.workers.dev`），填入超灵助手 App 即可在大陆网络畅享 Gemini 3！

---

## 编译与打包 APK

本项目为标准的 Android Studio 工程：
* 推荐使用 **Android Studio (Ladybug / Koala 或最新版)** 打开 `HyperAssistant` 目录。
* 或在命令行中执行：
  ```bash
  gradlew assembleDebug
  ```
  编译完成后的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`。
