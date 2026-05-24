package com.packhub;

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

   public Pack() {
   }

   public String getId() {
      return this.id != null ? this.id : "";
   }

   public String getName() {
      return this.name != null ? this.name : "Unknown Pack";
   }

   public String getPackFilename() {
      return this.pack_filename != null ? this.pack_filename : "pack.zip";
   }

   public String getAuthorName() {
      return this.author_name != null ? this.author_name : "Unknown";
   }

   public String getAuthorId() {
      return this.author_id != null ? this.author_id : "";
   }

   public String getShowcasePath() {
      return this.showcase_path != null ? this.showcase_path : "";
   }

   public String getPackUrl() {
      return this.pack_url != null ? this.pack_url : "";
   }

   public String getTag() {
      if (this.tag != null && !this.tag.isBlank()) {
         return this.tag;
      } else {
         return this.tags != null && !this.tags.isEmpty() ? this.tags.get(0) : "";
      }
   }

   public List<String> getTags() {
      if (this.tags != null && !this.tags.isEmpty()) {
         List<String> copy = new ArrayList<>(this.tags.size());

         for (String t : this.tags) {
            if (t != null && !t.isBlank()) {
               copy.add(t);
            }
         }

         return Collections.unmodifiableList(copy);
      } else {
         return this.tag != null && !this.tag.isBlank() ? Collections.singletonList(this.tag) : Collections.emptyList();
      }
   }

   public boolean isZip() {
      return this.is_zip;
   }

   public boolean hasLocalFile() {
      return this.has_local_file;
   }

   public double getApprovedAt() {
      return this.approved_at;
   }

   public int getStarCount() {
      return Math.max(0, this.star_count);
   }

   public int getDownloads() {
      return Math.max(0, this.downloads);
   }

   public String getSha256() {
      return this.sha256 != null ? this.sha256.trim() : "";
   }

   public boolean isViewerStarred() {
      return this.viewer_starred;
   }

   public void setStarCount(int v) {
      this.star_count = Math.max(0, v);
   }

   public void setViewerStarred(boolean v) {
      this.viewer_starred = v;
   }

   public String getShowcaseUrl(String serverBase) {
      return this.showcase_path != null && !this.showcase_path.isBlank() ? serverBase + this.showcase_path : "";
   }

   public String getDirectDownloadUrl(String serverBase) {
      return serverBase + "/api/packs/" + this.getId() + "/pack";
   }

   public String getStarUrl(String serverBase) {
      return serverBase + "/api/packs/" + this.getId() + "/star";
   }
}
