package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Uploads a built pack zip to the SlothyHub worker for moderator review. */
public final class PackSubmitClient {

    private static final Gson GSON = new Gson();
    private static final Type STATUS_LIST = new TypeToken<List<SubmissionStatus>>(){}.getType();
    /** Discord bot attachment limit for most servers. */
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int CONNECT_MS = 15_000;
    private static final int READ_MS = 120_000;

    private PackSubmitClient() {}

    public record SubmitRequest(
        String packName,
        String description,
        String authorName,
        String contact,
        String packId,
        String clientId,
        List<String> tags
    ) {}

    public record SubmitResult(boolean ok, String message, String submissionId) {}

    public record SubmissionStatus(
        String submissionId,
        String packName,
        String status,
        String submittedAt,
        String resolvedAt,
        String catalogId,
        String packUrl,
        String denyReason,
        List<String> tags
    ) {}

    public static SubmitResult submit(byte[] zipBytes, String filename, SubmitRequest req) throws IOException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IOException("Pack file is empty.");
        }
        if (zipBytes.length > MAX_BYTES) {
            throw new IOException("Pack is too large ("
                + (zipBytes.length / (1024 * 1024)) + " MB). Max is 8 MB for upload.");
        }
        if (req.packName() == null || req.packName().isBlank()) {
            throw new IOException("Pack name is required.");
        }

        String base = SlothyConfig.getWorkerBaseUrl();
        if (base.isBlank()) {
            throw new IOException("Upload server is not configured.");
        }

        String boundary = "SlothyHub-" + UUID.randomUUID();
        byte[] body = buildMultipart(boundary, zipBytes, filename, req);

        HttpURLConnection conn = (HttpURLConnection) URI.create(base + "/v1/pack-submit")
            .toURL().openConnection();
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(READ_MS);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0 (pack-submit)");
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(body.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String json = stream != null ? new String(stream.readAllBytes(), StandardCharsets.UTF_8) : "";

        if (code == 429) {
            throw new IOException(parseMessage(json, "Too many uploads — try again in an hour."));
        }
        if (code == 413) {
            throw new IOException("Pack is too large for upload (max 8 MB).");
        }
        if (code == 503) {
            throw new IOException("Upload review is not configured on the server yet.");
        }
        if (code != 200 && code != 201) {
            throw new IOException(parseMessage(json, "Upload failed (HTTP " + code + ")."));
        }

        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        String id = obj != null && obj.has("submissionId") ? obj.get("submissionId").getAsString() : "";
        String msg = obj != null && obj.has("message") ? obj.get("message").getAsString()
            : "Submitted for review!";
        return new SubmitResult(true, msg, id);
    }

    public static List<SubmissionStatus> fetchSubmissions(String clientId) throws IOException {
        if (clientId == null || clientId.isBlank()) return List.of();
        String base = SlothyConfig.getWorkerBaseUrl();
        if (base.isBlank()) return List.of();

        String url = base + "/v1/submit-status?clientId="
            + URLEncode(clientId);
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(CONNECT_MS);
        conn.setReadTimeout(READ_MS);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0 (submit-status)");

        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String json = stream != null ? new String(stream.readAllBytes(), StandardCharsets.UTF_8) : "";
        if (code != 200) {
            throw new IOException(parseMessage(json, "Could not load upload status (HTTP " + code + ")."));
        }

        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        if (obj == null || !obj.has("submissions")) return List.of();
        List<SubmissionStatus> list = GSON.fromJson(obj.get("submissions"), STATUS_LIST);
        return list != null ? list : List.of();
    }

    private static String URLEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String parseMessage(String json, String fallback) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj != null && obj.has("message")) return obj.get("message").getAsString();
            if (obj != null && obj.has("error")) return obj.get("error").getAsString();
        } catch (Exception ignored) {}
        return fallback;
    }

    private static byte[] buildMultipart(String boundary, byte[] zipBytes, String filename,
                                         SubmitRequest req) throws IOException {
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream(zipBytes.length + 2048);

        writeField(out, boundary, "packName", req.packName());
        writeField(out, boundary, "description", req.description() != null ? req.description() : "");
        writeField(out, boundary, "authorName", req.authorName() != null ? req.authorName() : "");
        writeField(out, boundary, "contact", req.contact() != null ? req.contact() : "");
        writeField(out, boundary, "packId", req.packId() != null ? req.packId() : "");
        writeField(out, boundary, "clientId", req.clientId() != null ? req.clientId() : "");
        writeField(out, boundary, "tags", tagsToField(req.tags()));

        String safeFile = filename != null && !filename.isBlank() ? filename : "pack.zip";
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"pack\"; filename=\"" + safeFile + "\"" + crlf)
            .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/zip" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(zipBytes);
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String tagsToField(List<String> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            if (t == null || t.isBlank()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(t.trim().toLowerCase());
        }
        return sb.toString();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary,
                                   String name, String value) throws IOException {
        String crlf = "\r\n";
        out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + crlf + crlf)
            .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write(crlf.getBytes(StandardCharsets.UTF_8));
    }
}
