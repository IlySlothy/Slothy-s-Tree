package com.slothyhub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Pack {

    private String id;
    private String name;
    private String pack_filename;
    private String author_name;
    private String author_id;
    private String showcase_path;
    private String pack_url;
    private String tag;
    private List<String> tags;
    private boolean is_zip;
    private boolean has_local_file;
    private double approved_at;
    private int star_count;
    private int downloads;
    private String sha256;
    private boolean viewer_starred;

    // extra field for local packs
    private boolean local = false;

    public Pack() {}

    public String getId() { return id != null ? id : ""; }
    public String getName() { return name != null ? name : "Unknown Pack"; }
    public String getPackFilename() { return pack_filename != null ? pack_filename : "pack.zip"; }
    public String getAuthorName() { return author_name != null ? author_name : "Unknown"; }
    public String getAuthorId() { return author_id != null ? author_id : ""; }
    public String getShowcasePath() { return showcase_path != null ? showcase_path : ""; }
    public String getPackUrl() { return pack_url != null ? pack_url : ""; }
    public boolean isZip() { return is_zip; }
    public boolean hasLocalFile() { return has_local_file; }
    public double getApprovedAt() { return approved_at; }
    public int getStarCount() { return Math.max(0, star_count); }
    public int getDownloads() { return Math.max(0, downloads); }
    public String getSha256() { return sha256 != null ? sha256.trim() : ""; }
    public boolean isViewerStarred() { return viewer_starred; }
    public boolean isLocal() { return local; }

    public void setStarCount(int v) { star_count = Math.max(0, v); }
    public void setViewerStarred(boolean v) { viewer_starred = v; }
    public void setLocal(boolean v) { local = v; }

    public String getTag() {
        if (tag != null && !tag.isBlank()) return tag;
        return tags != null && !tags.isEmpty() ? tags.get(0) : "";
    }

    public List<String> getTags() {
        if (tags != null && !tags.isEmpty()) {
            List<String> copy = new ArrayList<>(tags.size());
            for (String t : tags) {
                if (t != null && !t.isBlank()) copy.add(t);
            }
            return Collections.unmodifiableList(copy);
        }
        return tag != null && !tag.isBlank() ? Collections.singletonList(tag) : Collections.emptyList();
    }

    public String getShowcaseUrl(String serverBase) {
        return showcase_path != null && !showcase_path.isBlank() ? serverBase + showcase_path : "";
    }

    public String getDirectDownloadUrl(String serverBase) {
        return serverBase + "/api/packs/" + getId() + "/pack";
    }

    public String getStarUrl(String serverBase) {
        return serverBase + "/api/packs/" + getId() + "/star";
    }
}
