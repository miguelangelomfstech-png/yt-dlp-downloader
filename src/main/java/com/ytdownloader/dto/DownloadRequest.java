package com.ytdownloader.dto;

/**
 * Inbound request payload containing the YouTube URL to download.
 */
public class DownloadRequest {

    private String url;

    public DownloadRequest() {
    }

    public DownloadRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
