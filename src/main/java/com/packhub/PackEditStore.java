package com.packhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

public final class PackEditStore {
   public static final String SIDE_CAR = "packhub_edit.json";
   private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

   private PackEditStore() {
   }

   public static String readPackMcmetaDescription(Path packRoot) {
      if (packRoot == null) {
         return "";
      } else {
         Path mcmeta = packRoot.resolve("pack.mcmeta");
         if (!Files.isRegularFile(mcmeta)) {
            return "";
         } else {
            try {
               JsonObject root = JsonParser.parseString(Files.readString(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject();
               if (!root.has("pack")) {
                  return "";
               } else {
                  JsonObject pack = root.getAsJsonObject("pack");
                  if (!pack.has("description")) {
                     return "";
                  } else {
                     JsonElement d = pack.get("description");
                     if (d.isJsonPrimitive()) {
                        return d.getAsString();
                     } else if (d.isJsonArray()) {
                        JsonArray arr = d.getAsJsonArray();
                        return arr.size() > 0 ? arr.get(0).getAsString() : "";
                     } else {
                        return "";
                     }
                  }
               }
            } catch (Exception var6) {
               return "";
            }
         }
      }
   }

   public static void writePackMcmetaDescription(Path packRoot, String description) throws IOException {
      if (packRoot == null) {
         throw new IOException("No pack folder");
      } else {
         Path mcmeta = packRoot.resolve("pack.mcmeta");
         if (!Files.isRegularFile(mcmeta)) {
            throw new IOException("pack.mcmeta not found in pack");
         } else {
            String raw = Files.readString(mcmeta, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonObject packObj = root.getAsJsonObject("pack");
            if (packObj == null) {
               packObj = new JsonObject();
               root.add("pack", packObj);
            }

            packObj.add("description", new JsonPrimitive(description == null ? "" : description));
            Files.writeString(mcmeta, PRETTY.toJson(root), StandardCharsets.UTF_8);
         }
      }
   }

   public static Map<String, String> loadHints(Path packRoot) {
      if (packRoot == null) {
         return new LinkedHashMap<>();
      } else {
         Path f = packRoot.resolve("packhub_edit.json");
         LinkedHashMap<String, String> out = new LinkedHashMap<>();
         if (!Files.isRegularFile(f)) {
            return out;
         } else {
            try {
               JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
               if (root.has("hints") && root.get("hints").isJsonObject()) {
                  JsonObject h = root.getAsJsonObject("hints");

                  for (String k : h.keySet()) {
                     JsonElement v = h.get(k);
                     if (v != null && v.isJsonPrimitive()) {
                        out.put(k, v.getAsString());
                     }
                  }
               }
            } catch (Exception var8) {
               PackHubMod.LOGGER.warn("Could not read {}: {}", "packhub_edit.json", var8.getMessage());
            }

            return out;
         }
      }
   }

   public static String loadSidecarDisplayName(Path packRoot) {
      if (packRoot == null) {
         return "";
      } else {
         Path f = packRoot.resolve("packhub_edit.json");
         if (!Files.isRegularFile(f)) {
            return "";
         } else {
            try {
               JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
               return root.has("displayName") && root.get("displayName").isJsonPrimitive() ? root.get("displayName").getAsString() : "";
            } catch (Exception var31) {
               return "";
            }
         }
      }
   }

   public static void saveSidecar(Path packRoot, String displayName, Map<String, String> hints) throws IOException {
      if (packRoot == null) {
         throw new IOException("No pack folder");
      } else {
         JsonObject root = new JsonObject();
         root.addProperty("version", 1);
         root.addProperty("displayName", displayName == null ? "" : displayName);
         JsonObject h = new JsonObject();

         for (Entry<String, String> e : hints.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank()) {
               h.addProperty(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
         }

         root.add("hints", h);
         Files.writeString(packRoot.resolve("packhub_edit.json"), PRETTY.toJson(root), StandardCharsets.UTF_8);
      }
   }

   public static List<String> scanTexturePaths(Path packRoot, String subdir, int max) throws IOException {
      if (packRoot == null) {
         return List.of();
      } else {
         Path base = packRoot.resolve("assets/minecraft/textures").resolve(subdir);
         if (!Files.isDirectory(base)) {
            return List.of();
         } else {
            List<String> paths = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(base)) {
               for (Path p : walk.toList()) {
                  if (Files.isRegularFile(p) && p.toString().endsWith(".png")) {
                     paths.add(packRoot.relativize(p).toString().replace('\\', '/'));
                     if (paths.size() >= max) {
                        break;
                     }
                  }
               }
            }

            Collections.sort(paths);
            return paths;
         }
      }
   }

   public static String guessVanillaIdFromPath(String relPath) {
      int slash = relPath.lastIndexOf(47);
      String file = slash >= 0 ? relPath.substring(slash + 1) : relPath;
      if (file.endsWith(".png")) {
         file = file.substring(0, file.length() - 4);
      }

      return file.replace('-', '_');
   }
}
