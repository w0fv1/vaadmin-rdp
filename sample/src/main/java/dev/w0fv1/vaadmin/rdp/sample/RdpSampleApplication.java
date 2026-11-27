package dev.w0fv1.vaadmin.rdp.sample;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Push
@SpringBootApplication
public class RdpSampleApplication implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(RdpSampleApplication.class, args);
    }
}
