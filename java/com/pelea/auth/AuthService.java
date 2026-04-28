package com.pelea.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class AuthService {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String CLIENT_ID = dotenv.get("GOOGLE_CLIENT_ID");
    private static final String CLIENT_SECRET = dotenv.get("GOOGLE_CLIENT_SECRET");
    private static final String REDIRECT_URI = dotenv.get("REDIRECT_URI");

    public String getLoginUrl() {
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + CLIENT_ID +
                "&redirect_uri=" + REDIRECT_URI +
                "&response_type=code" +
                "&scope=openid%20email%20profile" +
                "&access_type=offline";
    }

    public static Map<String, String> getUserInfo(String code) throws Exception {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        String params = "code=" + code +
                "&client_id=" + CLIENT_ID +
                "&client_secret=" + CLIENT_SECRET +
                "&redirect_uri=" + REDIRECT_URI +
                "&grant_type=authorization_code";

        URL url = new URL(tokenUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(params.getBytes());
        }

        JsonObject jsonToken = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
        String accessToken = jsonToken.get("access_token").getAsString();

        URL infoUrl = new URL("https://www.googleapis.com/oauth2/v3/userinfo?access_token=" + accessToken);
        HttpURLConnection infoConn = (HttpURLConnection) infoUrl.openConnection();
        infoConn.setRequestMethod("GET");

        JsonObject userInfo = JsonParser.parseReader(new InputStreamReader(infoConn.getInputStream())).getAsJsonObject();

        Map<String, String> result = new HashMap<>();
        result.put("email", userInfo.get("email").getAsString());
        result.put("name", userInfo.get("name").getAsString());
        result.put("picture", userInfo.get("picture").getAsString());

        return result;
    }
}