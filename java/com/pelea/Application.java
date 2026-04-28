package com.pelea;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.pelea.database.DatabaseManager;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;

@SpringBootApplication
@Push
@Theme(value = "pelea-theme")
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        DatabaseManager.getInstance();

        SpringApplication.run(Application.class, args);
    }
}