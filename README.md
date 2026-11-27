# vaadmin-rdp

Java 21 / Vaadin Flow 的 Windows 远程桌面组件。JVM 直接通过 TLS 和 CredSSP 连接 RDP 服务器，浏览器负责显示画面和收集输入。示例默认监听本机回环地址。

## 运行示例

在 Windows 上运行：

```powershell
.\gradlew.bat :sample:bootRun
```

在 macOS 或 Linux 上运行 `./gradlew :sample:bootRun`。打开 [本地示例](http://localhost:8080)，填写服务器地址、端口、用户名和密码。域账号可单独填写域名。导入 Windows 的 `.rdp` 文件可填充连接地址、用户名和桌面尺寸；密码需要另行填写。导入字段以 [RdpProfile](src/main/java/dev/w0fv1/vaadmin/rdp/RdpProfile.java) 为准。

默认通过 JVM 信任库及主机名验证服务器。使用自签名证书时，可以填写从可信渠道取得的 RDP 服务端证书 SHA-256 指纹。浏览器剪贴板需要 HTTPS 或 localhost，并需要浏览器授予权限。点击桌面后可以使用键鼠，在远端复制选区后点「复制远端文本」，向远端传送文本可点「复制到远端剪贴板」，随后在目标应用中按 Shift+Insert 粘贴。

## 集成

在启用 Vaadin Push 的应用中，将 [RdpWorkbench](src/main/java/dev/w0fv1/vaadmin/rdp/RdpWorkbench.java) 添加到有访问控制的管理页面。自定义连接表单可直接使用 [RdpViewer](src/main/java/dev/w0fv1/vaadmin/rdp/RdpViewer.java)。JVM 需要能访问目标服务器的 RDP 端口。

[示例入口](sample/src/main/java/dev/w0fv1/vaadmin/rdp/sample/RdpSampleView.java) · [RDP 协议规范](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-rdpbcgr/) · [Apache License 2.0](LICENSE)
