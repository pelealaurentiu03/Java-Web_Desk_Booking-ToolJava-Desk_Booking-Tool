package com.pelea.ui;

import com.pelea.auth.AuthService;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import java.util.List;
import java.util.Map;

@Route("oauth2callback")
public class OAuthCallbackView extends Div implements BeforeEnterObserver {

    public OAuthCallbackView() {
        add(new Span("Login..."));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Map<String, List<String>> params = event.getLocation().getQueryParameters().getParameters();
        
        if (params.containsKey("code")) {
            String code = params.get("code").get(0);
            try {
                Map<String, String> userInfo = AuthService.getUserInfo(code);

                if (userInfo != null) {
                    VaadinSession.getCurrent().setAttribute("email", userInfo.get("email"));
                    VaadinSession.getCurrent().setAttribute("name", userInfo.get("name"));
                    VaadinSession.getCurrent().setAttribute("photo", userInfo.get("picture"));
                    
                    event.forwardTo(MainView.class);
                } else {
                    event.forwardTo(LoginView.class);
                }
            } catch (Exception e) {
                e.printStackTrace();
                event.forwardTo(LoginView.class);
            }
        } else {
            event.forwardTo(LoginView.class);
        }
    }
}