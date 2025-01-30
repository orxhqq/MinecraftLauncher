package org.example.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.*;
import java.nio.file.*;

public class MinecraftLauncher {
    private final String minecraftToken;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Path gameDir;

    public MinecraftLauncher(String minecraftToken) {
        this.minecraftToken = minecraftToken;
        this.httpClient = new OkHttpClient();
        this.gson = new Gson();
        // Définir le dossier .minecraft dans le dossier utilisateur
        this.gameDir = Paths.get(System.getProperty("user.home"), ".minecraft");
    }

    public void downloadGame(String version) {
        try {
            System.out.println("Début du téléchargement de Minecraft " + version);
            
            // Créer le dossier .minecraft s'il n'existe pas
            Files.createDirectories(gameDir);
            
            // Étape 1 : Télécharger le manifest des versions
            downloadVersionManifest();
            
            // Étape 2 : Télécharger les fichiers de la version spécifique
            downloadVersion(version);
            
            // Étape 3 : Télécharger les assets (textures, sons, etc.)
            downloadAssets(version);
            
            // Étape 4 : Télécharger les libraries nécessaires
            downloadLibraries(version);
            
            System.out.println("Téléchargement terminé avec succès !");
            
        } catch (IOException e) {
            System.err.println("Erreur lors du téléchargement : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void downloadVersionManifest() throws IOException {
        System.out.println("Téléchargement du manifest des versions...");
        
        Request request = new Request.Builder()
            .url("https://launchermeta.mojang.com/mc/game/version_manifest.json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Échec du téléchargement du manifest : " + response.code());
            }
            
            String json = response.body().string();
            Path manifestPath = gameDir.resolve("version_manifest.json");
            Files.writeString(manifestPath, json);
            
            System.out.println("Manifest des versions téléchargé avec succès");
        }
    }

    private void downloadVersion(String version) throws IOException {
        System.out.println("Téléchargement de la version " + version + "...");
        
        // Lire le manifest pour trouver l'URL de la version
        String manifestContent = Files.readString(gameDir.resolve("version_manifest.json"));
        JsonObject manifest = gson.fromJson(manifestContent, JsonObject.class);
        
        // Trouver l'URL de la version spécifique
        String versionUrl = null;
        for (var element : manifest.getAsJsonArray("versions")) {
            JsonObject versionObj = element.getAsJsonObject();
            if (versionObj.get("id").getAsString().equals(version)) {
                versionUrl = versionObj.get("url").getAsString();
                break;
            }
        }
        
        if (versionUrl == null) {
            throw new IOException("Version " + version + " non trouvée");
        }

        // Télécharger le json de la version
        Request request = new Request.Builder()
            .url(versionUrl)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String json = response.body().string();
            Path versionPath = gameDir.resolve("versions").resolve(version);
            Files.createDirectories(versionPath);
            Files.writeString(versionPath.resolve(version + ".json"), json);
        }
    }

    private void downloadAssets(String version) throws IOException {
        System.out.println("Téléchargement des assets...");
        // Cette méthode sera implémentée dans la prochaine étape
    }

    private void downloadLibraries(String version) throws IOException {
        System.out.println("Téléchargement des libraries...");
        // Cette méthode sera implémentée dans la prochaine étape
    }

    public void launch(String version) {
        try {
            System.out.println("Préparation au lancement de Minecraft " + version);
            
            // Lire le fichier json de la version
            Path versionJsonPath = gameDir.resolve("versions").resolve(version).resolve(version + ".json");
            String versionJson = Files.readString(versionJsonPath);
            JsonObject versionData = gson.fromJson(versionJson, JsonObject.class);
            
            // Construire la commande de lancement
            String mainClass = versionData.get("mainClass").getAsString();
            
            // Construire les arguments
            ProcessBuilder processBuilder = new ProcessBuilder(
                "java",
                "-Xmx2G",  // Allouer 2GB de RAM max
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:G1NewSizePercent=20",
                "-XX:G1ReservePercent=20",
                "-XX:MaxGCPauseMillis=50",
                "-XX:G1HeapRegionSize=32M",
                "-cp", getClasspath(version),
                mainClass,
                "--version", version,
                "--gameDir", gameDir.toString(),
                "--assetsDir", gameDir.resolve("assets").toString(),
                "--accessToken", minecraftToken,
                "--username", "Player"  // À remplacer par le vrai nom d'utilisateur
            );
            
            // Définir le répertoire de travail
            processBuilder.directory(gameDir.toFile());
            
            // Rediriger la sortie
            processBuilder.inheritIO();
            
            // Lancer le processus
            System.out.println("Lancement du jeu...");
            Process process = processBuilder.start();
            
            // Attendre la fin du processus
            int exitCode = process.waitFor();
            System.out.println("Jeu terminé avec le code : " + exitCode);
            
        } catch (Exception e) {
            System.err.println("Erreur lors du lancement : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getClasspath(String version) throws IOException {
        // Pour l'instant, une version simplifiée du classpath
        return gameDir.resolve("versions").resolve(version).resolve(version + ".jar").toString();
    }
}
