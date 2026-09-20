package com.ytdownloader.dto;

/**
 * Outbound response payload returned after a download attempt.
 */
public class DownloadResponse {

    private boolean success;
    private String message;
    private String downloadPath;

    public DownloadResponse() {
    }

    public DownloadResponse(boolean success, String message, String downloadPath) {
        this.success = success;
        this.message = message;
        this.downloadPath = downloadPath;
    }

    /* ---------- static factory helpers ---------- */

    public static DownloadResponse ok(String message, String downloadPath) {
        return new DownloadResponse(true, message, downloadPath);
    }

    public static DownloadResponse error(String message) {
        return new DownloadResponse(false, message, null);
    }

    /* ---------- getters / setters ---------- */

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDownloadPath() {
        return downloadPath;
    }

    public void setDownloadPath(String downloadPath) {
        this.downloadPath = downloadPath;
    }
}
