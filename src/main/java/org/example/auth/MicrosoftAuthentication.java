package org.example.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.util.concurrent.CompletableFuture;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Properties;

public class MicrosoftAuthentication {
    private static final String REDIRECT_URI = "http://localhost:8080";
    private final String CLIENT_ID;
    private final String CLIENT_SECRET;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public MicrosoftAuthentication() {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            props.load(input);
            CLIENT_ID = props.getProperty("client.id");
            CLIENT_SECRET = props.getProperty("client.secret");
        } catch (IOException e) {
            throw new RuntimeException("Impossible de charger la configuration", e);
        }
        
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
    }

    public CompletableFuture<AuthResult> authenticate() {
        CompletableFuture<AuthResult> future = new CompletableFuture<>();
        
        try {
            String scope = URLEncoder.encode("XboxLive.signin offline_access", StandardCharsets.UTF_8);
            String authUrl = String.format("https://login.live.com/oauth20_authorize.srf" +
                "?client_id=%s" +
                "&response_type=code" +
                "&redirect_uri=%s" +
                "&scope=%s",
                CLIENT_ID,
                URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8),
                scope);

            System.out.println("URL d'autorisation générée : " + authUrl);
            
            startLocalServer(future);
            
            Thread.sleep(1000);
            
            java.awt.Desktop.getDesktop().browse(new java.net.URI(authUrl));
            
        } catch (Exception e) {
            System.err.println("Erreur lors de l'authentification : " + e.getMessage());
            e.printStackTrace();
            future.completeExceptionally(e);
        }
        
        return future;
    }

    private void startLocalServer(CompletableFuture<AuthResult> future) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8080)) {
                System.out.println("Serveur local démarré sur le port 8080");
                
                Socket socket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
                );

                StringBuilder requestBuilder = new StringBuilder();
                String line;
                while (!(line = reader.readLine()).isEmpty()) {
                    requestBuilder.append(line).append("\n");
                }

                String request = requestBuilder.toString();
                System.out.println("Requête reçue : " + request);

                if (request.contains("code=")) {
                    String code = extractCode(request);
                    System.out.println("Code reçu : " + code);
                    
                    // Envoyer une réponse HTML
                    String response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html\r\n\r\n" +
                        "<html><body><h1>Authentification réussie !</h1>" +
                        "<p>Vous pouvez fermer cette fenêtre.</p></body></html>";
                    
                    socket.getOutputStream().write(response.getBytes());
                    socket.getOutputStream().flush();
                    
                    try {
                        String accessToken = getMicrosoftToken(code);
                        future.complete(new AuthResult(accessToken, "User"));
                    } catch (Exception e) {
                        System.err.println("Erreur lors de l'obtention du token : " + e.getMessage());
                        future.completeExceptionally(e);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Erreur serveur : " + e.getMessage());
                future.completeExceptionally(e);
            }
        }).start();
    }

    private String extractCode(String request) {
        String[] parts = request.split("\\?")[1].split(" ")[0].split("&");
        for (String part : parts) {
            if (part.startsWith("code=")) {
                return part.substring(5);
            }
        }
        throw new IllegalArgumentException("Code non trouvé dans la requête");
    }

    private String getMicrosoftToken(String authCode) throws IOException {
        RequestBody formBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", authCode)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .build();

        Request request = new Request.Builder()
            .url("https://login.live.com/oauth20_token.srf")
            .post(formBody)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Échec de la requête : " + response.code());
            }
            
            String responseBody = response.body().string();
            System.out.println("Réponse token Microsoft : " + responseBody);
            
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            if (jsonResponse.has("access_token")) {
                return jsonResponse.get("access_token").getAsString();
            } else {
                throw new IOException("Token non trouvé dans la réponse");
            }
        }
    }

    private String getUsernameFromToken(String token) throws IOException {
        Request request = new Request.Builder()
            .url("https://user.auth.xboxlive.com/users/current/profile")
            .header("Authorization", "Bearer " + token)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            JsonObject json = gson.fromJson(response.body().string(), JsonObject.class);
            return json.getAsJsonObject("profileUsers").getAsJsonArray("value")
                      .get(0).getAsJsonObject().get("gamertag").getAsString();
        }
    }

    // Classe interne pour stocker le résultat de l'authentification
    public static class AuthResult {
        private final String accessToken;
        private final String username;

        public AuthResult(String accessToken, String username) {
            this.accessToken = accessToken;
            this.username = username;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getUsername() {
            return username;
        }
    }
}