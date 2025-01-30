package org.example;

import org.example.auth.MicrosoftAuthentication;
import org.example.auth.MicrosoftAuthentication.AuthResult;
import org.example.launcher.MinecraftLauncher;

public class Main {
    public static void main(String[] args) {
        System.out.println("Démarrage du launcher...");
        
        MicrosoftAuthentication auth = new MicrosoftAuthentication();
        
        auth.authenticate()
            .thenAccept(result -> {
                System.out.println("Authentification réussie pour : " + result.getUsername());
                MinecraftLauncher launcher = new MinecraftLauncher(result.getAccessToken());
                launcher.downloadGame("1.20.1");
                launcher.launch("1.20.1");
            })
            .exceptionally(error -> {
                System.err.println("Erreur : " + error.getMessage());
                error.printStackTrace();
                return null;
            });
            
        try {
            Thread.sleep(120000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}