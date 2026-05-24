package com.packhub;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PackApiClient {
   private static final Gson GSON = new Gson();
   private static final int TIMEOUT_MS = 10000;

   public PackApiClient() {
   }

   public static List<Pack> fetchPacks(String serverUrl) throws IOException {
      String endpoint = serverUrl + "/api/packs";

      try {
         HttpURLConnection conn = (HttpURLConnection)URI.create(endpoint).toURL().openConnection();
         conn.setConnectTimeout(10000);
         conn.setReadTimeout(10000);
         conn.setRequestProperty("Accept", "application/json");
         conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
         String voter = PackHubConfig.getVoterId();
         if (voter != null && !voter.isBlank()) {
            conn.setRequestProperty("X-PackHub-Voter", voter);
         }

         int code = conn.getResponseCode();
         if (code == 503) {
            throw new IOException("PackHub is updating. Try again in a minute.");
         } else if (code != 200) {
            throw new IOException("Server returned HTTP " + code + " for " + endpoint);
         } else {
            List var9;
            try (InputStream in = conn.getInputStream()) {
               String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
               Type listType = (new TypeToken<List<Pack>>() {
               }).getType();
               List<Pack> packs = (List<Pack>)GSON.fromJson(json, listType);
               var9 = packs != null ? packs : List.of();
            }

            return var9;
         }
      } catch (UnknownHostException var12) {
         throw new IOException("The PackHub bot looks offline. Ask the host to restart it, then press Reconnect.", var12);
      } catch (SocketTimeoutException var131) {
         throw new IOException("The PackHub bot didn't respond in time. It may be overloaded — press Reconnect to retry.", var131);
      }
   }

   public static PackApiClient.StarResult starPack(String serverUrl, Pack pack) throws IOException {
      String endpoint = pack.getStarUrl(serverUrl);
      HttpURLConnection conn = (HttpURLConnection)URI.create(endpoint).toURL().openConnection();
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
      conn.setRequestProperty("X-PackHub-Voter", PackHubConfig.getVoterId());
      conn.setDoOutput(false);
      int code = conn.getResponseCode();
      if (code == 200 || code == 201) {
         PackApiClient.StarResult var9;
         try (InputStream in = conn.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject obj = (JsonObject)GSON.fromJson(json, JsonObject.class);
            int newCount = obj != null && obj.has("star_count") ? obj.get("star_count").getAsInt() : pack.getStarCount();
            var9 = new PackApiClient.StarResult(newCount, code == 201);
         }

         return var9;
      } else if (code == 429) {
         throw new IOException("Too many stars too fast — try again in a minute.");
      } else if (code == 404) {
         throw new IOException("That pack is no longer available.");
      } else {
         throw new IOException("Server returned HTTP " + code + " for /star.");
      }
   }

   public static PackApiClient.BuilderResourcesResponse fetchBuilderResources(String serverUrl, String category) throws IOException {
      String endpoint = serverUrl + "/api/builder/resources";
      if (category != null && !category.isEmpty()) {
         endpoint = endpoint + "?category=" + category;
      }

      HttpURLConnection conn = (HttpURLConnection)URI.create(endpoint).toURL().openConnection();
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(30000);
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
      int code = conn.getResponseCode();
      if (code != 200) {
         throw new IOException("Server returned HTTP " + code + " for builder resources.");
      } else {
         PackApiClient.BuilderResourcesResponse var7;
         try (InputStream in = conn.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var7 = (PackApiClient.BuilderResourcesResponse)GSON.fromJson(json, PackApiClient.BuilderResourcesResponse.class);
         }

         return var7;
      }
   }

   public static byte[] buildCustomPack(String serverUrl, String packName, List<PackApiClient.BuilderMapping> mappings) throws IOException {
      String endpoint = serverUrl + "/api/builder/build";
      HttpURLConnection conn = (HttpURLConnection)URI.create(endpoint).toURL().openConnection();
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(120000);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
      conn.setDoOutput(true);
      JsonObject body = new JsonObject();
      body.addProperty("name", packName);
      JsonArray arr = new JsonArray();

      for (PackApiClient.BuilderMapping m : mappings) {
         JsonObject obj = new JsonObject();
         obj.addProperty("resource_id", m.resourceId());
         obj.addProperty("vanilla_name", m.vanillaName());
         arr.add(obj);
      }

      body.add("mappings", arr);
      byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
      conn.setFixedLengthStreamingMode(payload.length);

      try (OutputStream os = conn.getOutputStream()) {
         os.write(payload);
      }

      int code = conn.getResponseCode();
      if (code != 200) {
         String errBody = "";

         try (InputStream es = conn.getErrorStream()) {
            if (es != null) {
               errBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
            }
         } catch (Exception var19) {
         }

         throw new IOException("Build failed (HTTP " + code + "): " + errBody);
      } else {
         byte[] esx;
         try (InputStream in = conn.getInputStream()) {
            esx = in.readAllBytes();
         }

         return esx;
      }
   }

   public static byte[] downloadBytes(String url) throws IOException {
      HttpURLConnection conn = (HttpURLConnection)URI.create(url).toURL().openConnection();
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
      if (conn.getResponseCode() != 200) {
         throw new IOException("HTTP " + conn.getResponseCode() + " fetching " + url);
      } else {
         byte[] var3;
         try (InputStream in = conn.getInputStream()) {
            var3 = in.readAllBytes();
         }

         return var3;
      }
   }

   public static PackApiClient.FeatherProfilesResponse fetchFeatherProfiles(String serverUrl) throws IOException {
      String endpoint = serverUrl + "/api/feather/profiles";
      HttpURLConnection conn = (HttpURLConnection)URI.create(endpoint).toURL().openConnection();
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
      int code = conn.getResponseCode();
      if (code != 200) {
         throw new IOException("Server returned HTTP " + code + " for feather profiles.");
      } else {
         PackApiClient.FeatherProfilesResponse var6;
         try (InputStream in = conn.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var6 = (PackApiClient.FeatherProfilesResponse)GSON.fromJson(json, PackApiClient.FeatherProfilesResponse.class);
         }

         return var6;
      }
   }

   public static String downloadFeatherProfile(String serverUrl, String profileId) throws IOException {
      String endpoint = serverUrl + "/api/feather/profiles/" + profileId;
      HttpURLConnection conn = (HttpURLConnection)URI.create(endpoint).toURL().openConnection();
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      conn.setRequestProperty("Accept", "application/json");
      conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
      int code = conn.getResponseCode();
      if (code != 200) {
         throw new IOException("Server returned HTTP " + code + " for feather profile download.");
      } else {
         String var6;
         try (InputStream in = conn.getInputStream()) {
            var6 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         }

         return var6;
      }
   }

   public static record BuilderMapping(String resourceId, String vanillaName) {
   }

   public static class BuilderResource {
      public String id;
      public String category;
      public String name;
      public String url;
      public String author;
      public int width;
      public int height;

      public BuilderResource() {
      }
   }

   public static class BuilderResourcesResponse {
      public List<String> categories;
      public List<PackApiClient.BuilderResource> resources;
      public int total;

      public BuilderResourcesResponse() {
      }
   }

   public static class FeatherProfile {
      public String id;
      public String name;
      public String description;
      public String author;
      public String download_url;

      public FeatherProfile() {
      }
   }

   public static class FeatherProfilesResponse {
      public List<PackApiClient.FeatherProfile> profiles;
      public int total;

      public FeatherProfilesResponse() {
      }
   }

   public static record StarResult(int starCount, boolean wasNew) {
   }
}
