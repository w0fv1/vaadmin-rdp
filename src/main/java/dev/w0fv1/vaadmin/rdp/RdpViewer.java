package dev.w0fv1.vaadmin.rdp;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import java.io.IOException;
import java.util.Base64;
import javax.net.ssl.SSLContext;

@Tag("vaadmin-rdp-viewer")
@JsModule("./vaadmin-rdp-viewer.js")
public final class RdpViewer extends Component {
    private RdpSession session;
    private int generation;


    public void connect(RdpProfile profile, String password, SSLContext trust) {
        disconnect();
        var ui = getUI().orElseThrow();
        int attempt = generation;
        getElement().callJsFunction("setStatus", "正在连接");
        Thread.startVirtualThread(() -> {
            try {
                var connected = RdpSession.connect(profile, password, trust,
                        text -> ui.access(() -> {
                            if (generation == attempt) getElement().callJsFunction("setRemoteText", text);
                        }),
                        error -> ui.access(() -> {
                            if (generation == attempt) {
                                disconnect();
                                getElement().callJsFunction("setStatus", error);
                            }
                        }));
                ui.accessLater(() -> {
                    if (generation == attempt && isAttached()) {
                        session = connected;
                        getElement().callJsFunction("openDesktop", profile.width(), profile.height());
                    } else connected.close();
                }, connected::close).run();
            } catch (Exception e) {
                ui.access(() -> {
                    if (generation == attempt) getElement().callJsFunction("setStatus", "连接失败 · " + e.getMessage());
                });
            }
        });
    }

    public void disconnect() {
        generation++;
        if (session != null) session.close();
        session = null;

        getElement().callJsFunction("closeDesktop");
    }

    @FunctionalInterface
    private interface Input { void send(RdpSession session) throws IOException; }

    private void forward(Input input) {
        try {
            if (session != null) input.send(session);
        } catch (IOException e) {
            disconnect();
            getElement().callJsFunction("setStatus", "远程连接已结束");
        }
    }

    @ClientCallable public String frame(double previous) {
        if (session == null) return "";
        try {
            var frame = session.frameAfter((long) previous);
            return frame == null ? "" : frame.revision() + "," + Base64.getEncoder().encodeToString(frame.png());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @ClientCallable public void key(int code, boolean extended, boolean down) {
        forward(s -> s.key(code, extended, down));
    }

    @ClientCallable public void pointer(int flags, int x, int y) {
        forward(s -> s.pointer(flags, x, y));
    }

    @ClientCallable public void offerText(String text) { forward(s -> s.offerText(text)); }

    @ClientCallable public void pasteShortcut() { forward(RdpSession::pasteShortcut); }

    @ClientCallable public void copyShortcut(boolean rightControl) { forward(s -> s.copyShortcut(rightControl)); }


    @Override protected void onDetach(DetachEvent event) {
        disconnect();
        super.onDetach(event);
    }
}
