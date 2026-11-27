# vaadmin-rdp

Java 21 / Vaadin Flow 的 Windows 远程桌面组件。JVM 直接通过 TLS 和 CredSSP 连接 RDP 服务器，浏览器负责显示画面和收集输入。示例默认监听本机回环地址。

## 运行示例

在 Windows 上运行：

```powershell
.\gradlew.bat :sample:bootRun
```

在 macOS 或 Linux 上运行 `./gradlew :sample:bootRun`。打开 [本地示例](http://localhost:8080)，填写服务器地址、端口、用户名和密码。域账号可单独填写域名。导入 Windows 的 `.rdp` 文件可填充连接地址、用户名和桌面尺寸；密码需要另行填写。导入字段以 [RdpProfile](src/main/java/dev/w0fv1/vaadmin/rdp/RdpProfile.java) 为准。

默认通过 JVM 信任库及主机名验证服务器。使用自签名证书时，可以填写从可信渠道取得的 RDP 服务端证书 SHA-256 指纹。浏览器剪贴板需要 HTTPS 或 localhost。

在远程桌面选中文字并按 Ctrl+C，看到「已复制到本机剪贴板」后，就能在本机应用中按 Ctrl+V。浏览器要求手动写入时，按页面提示点「复制远端文本」。从本机向远端粘贴时，在桌面按 Ctrl+V，也可以点「复制到远端剪贴板」，随后在远端应用按 Shift+Insert。

## 集成

在启用 Vaadin Push 的应用中，将 [RdpWorkbench](src/main/java/dev/w0fv1/vaadmin/rdp/RdpWorkbench.java) 添加到有访问控制的管理页面。自定义连接表单可直接使用 [RdpViewer](src/main/java/dev/w0fv1/vaadmin/rdp/RdpViewer.java)。JVM 需要能访问目标服务器的 RDP 端口。

[示例入口](sample/src/main/java/dev/w0fv1/vaadmin/rdp/sample/RdpSampleView.java) · [RDP 协议规范](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-rdpbcgr/) · [Apache License 2.0](LICENSE)

## 真机复制验收

在装有 Edge 和 Node.js 的 Windows 测试机上启动示例，配置 `VAADMIN_TEST_PROFILE`、`VAADMIN_TEST_USER` 和 `VAADMIN_TEST_PASS`。自签名服务器还需配置 `RDP_TEST_SERVER_PIN`。示例地址可通过 `VAADMIN_SAMPLE_URL` 指定。在专用 Windows 测试会话中打开并最大化一个空白记事本。验收会在这个文档中输入测试文本，并使用本机剪贴板。

```powershell
npm install --no-save --package-lock=false playwright
node src/test/browser/remote-copy.cjs
```
