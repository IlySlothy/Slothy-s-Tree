package com.slothyhub;

import com.slothyhub.ui.Ui;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local record of pack upload submissions (merged with worker status on refresh). */
public final class UploadTracker {

    private UploadTracker() {}

    public record Entry(
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

    public static void remember(String submissionId, String packName, List<String> tags) {
        if (submissionId == null || submissionId.isBlank()) return;
        List<Entry> list = loadLocal();
        list.removeIf(e -> submissionId.equalsIgnoreCase(e.submissionId()));
        list.add(0, new Entry(
            submissionId,
            packName != null ? packName : "Pack",
            "pending",
            java.time.Instant.now().toString(),
            "",
            "",
            "",
            "",
            tags != null ? new ArrayList<>(tags) : List.of()
        ));
        while (list.size() > 40) list.remove(list.size() - 1);
        SlothyConfig.saveUploadEntries(list);
    }

    public static List<Entry> loadLocal() {
        return new ArrayList<>(SlothyConfig.loadUploadEntries());
    }

    public static List<Entry> mergeRemote(List<PackSubmitClient.SubmissionStatus> remote) {
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (Entry e : loadLocal()) {
            byId.put(e.submissionId().toLowerCase(Locale.ROOT), e);
        }
        for (PackSubmitClient.SubmissionStatus r : remote) {
            if (r.submissionId() == null || r.submissionId().isBlank()) continue;
            String key = r.submissionId().toLowerCase(Locale.ROOT);
            Entry prev = byId.get(key);
            byId.put(key, new Entry(
                r.submissionId(),
                r.packName() != null && !r.packName().isBlank()
                    ? r.packName() : (prev != null ? prev.packName() : "Pack"),
                r.status() != null ? r.status() : "pending",
                r.submittedAt() != null ? r.submittedAt() : (prev != null ? prev.submittedAt() : ""),
                r.resolvedAt() != null ? r.resolvedAt() : "",
                r.catalogId() != null ? r.catalogId() : "",
                r.packUrl() != null ? r.packUrl() : "",
                r.denyReason() != null ? r.denyReason() : "",
                r.tags() != null && !r.tags().isEmpty()
                    ? r.tags() : (prev != null ? prev.tags() : List.of())
            ));
        }
        return new ArrayList<>(byId.values());
    }

    public static String statusLabel(String status) {
        if (status == null) return "Pending";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> "Approved";
            case "denied" -> "Denied";
            default -> "Pending";
        };
    }

    public static int statusColor(String status) {
        if (status == null) return Ui.COL_MUTED;
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approved" -> Ui.COL_ACCENT;
            case "denied" -> Ui.COL_DANGER;
            default -> 0xFF6EC4FF;
        };
    }
}
