package com.pelea.ui;

import com.pelea.auth.AuthService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

@Route("login")
public class LoginView extends VerticalLayout {

    private AuthService authService = new AuthService();

    public LoginView() {
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setHeightFull();

        H1 title = new H1("Pelea Desk Booking Tool");
        
        // Image logo = new Image("icons/logo.png", "Logo");
        
        Button googleLoginBtn = new Button("Login with Google");
        googleLoginBtn.getStyle().set("cursor", "pointer");
        
        googleLoginBtn.addClickListener(event -> {
            String url = authService.getLoginUrl();
            
            UI.getCurrent().getPage().setLocation(url);
        });

        add(title, googleLoginBtn);
    }
}