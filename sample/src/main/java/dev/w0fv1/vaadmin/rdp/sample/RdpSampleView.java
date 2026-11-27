package dev.w0fv1.vaadmin.rdp.sample;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.w0fv1.vaadmin.rdp.RdpWorkbench;

@Route("")
@PageTitle("Windows 远程桌面")
public final class RdpSampleView extends VerticalLayout {
    public RdpSampleView() {
        add(new H1("Windows 远程桌面"), new RdpWorkbench());
    }
}
