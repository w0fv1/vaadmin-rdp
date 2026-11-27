package dev.w0fv1.vaadmin.rdp;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

public final class RdpWorkbench extends VerticalLayout {
    private final TextField host = new TextField("IP 或主机名");
    private final IntegerField port = new IntegerField("端口");
    private final TextField user = new TextField("用户名");
    private final PasswordField password = new PasswordField("密码");
    private final TextField domain = new TextField("域名（可选）");
    private final TextField fingerprint = new TextField("服务器证书 SHA-256（可选）");
    private final RdpViewer viewer = new RdpViewer();
    private final Span notice = new Span();
    private int width = 1280;
    private int height = 800;

    public RdpWorkbench() {
        port.setValue(3389);
        port.setMin(1);
        port.setMax(65535);
        var fields = new HorizontalLayout(host, port, user, password, domain, fingerprint);
        fields.setWrap(true);
        var upload = new Upload(UploadHandler.inMemory((metadata, bytes) -> {
            try {
                loadProfile(RdpProfile.parse(bytes), null);
                notice.setText("已导入连接地址与桌面尺寸，证书按连接时的信任设置验证。");
            } catch (IllegalArgumentException e) { notice.setText(e.getMessage()); }
        }));
        upload.setMaxFileSize(1024 * 1024);
        upload.setMaxFiles(1);
        upload.setAcceptedFileExtensions(".rdp");
        upload.setUploadButton(new Button("导入 .rdp"));
        upload.setDropAllowed(false);
        var actions = new HorizontalLayout(upload, new Button("连接", e -> connect()),
                new Button("断开", e -> viewer.disconnect()));
        add(fields, actions, notice, viewer);
        setWidthFull();
    }

    private void connect() {
        try {
            if (port.isEmpty() || user.isEmpty() || password.isEmpty()) {
                notice.setText("请填写端口、用户名和密码。");
                return;
            }
            var profile = new RdpProfile(host.getValue().strip(), port.getValue(), user.getValue(),
                    domain.getValue(), width, height);
            connect(profile, password.getValue(), fingerprint.getValue());
        } catch (Exception e) { notice.setText(e.getMessage()); }
    }

    public void connect(RdpProfile profile, String secret, String sha256) {
        loadProfile(profile, sha256);
        try {
            viewer.connect(profile, secret, RdpTrust.context(sha256));
            notice.setText("");
        } catch (java.security.GeneralSecurityException | IllegalArgumentException e) {
            notice.setText(e.getMessage());
        }
    }

    private void loadProfile(RdpProfile profile, String sha256) {
        host.setValue(profile.host());
        port.setValue(profile.port());
        user.setValue(profile.username());
        domain.setValue(profile.domain() == null ? "" : profile.domain());
        fingerprint.setValue(sha256 == null ? "" : sha256);
        password.clear();
        width = profile.width();
        height = profile.height();
    }
}
