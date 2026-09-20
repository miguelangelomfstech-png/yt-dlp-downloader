package com.ytdownloader.dto;

/**
 * Event payload sent over Server-Sent Events (SSE) to the frontend.
 */
public class ProgressEvent {
    private String status; // e.g. "STARTING", "DOWNLOADING", "PROCESSING", "DONE", "ERROR"
    private double percent; // 0.0 to 100.0
    private String speed; // e.g. "1.23MiB/s"
    private String eta; // e.g. "00:05"
    private String message; // General message or error details
    private String fileName; // Available when status is DONE

    public ProgressEvent() {}

    public ProgressEvent(String status, double percent, String speed, String eta, String message) {
        this.status = status;
        this.percent = percent;
        this.speed = speed;
        this.eta = eta;
        this.message = message;
    }

    public static ProgressEvent error(String message) {
        return new ProgressEvent("ERROR", 0, "", "", message);
    }
    
    public static ProgressEvent done(String fileName) {
        ProgressEvent evt = new ProgressEvent("DONE", 100.0, "", "", "Download complete");
        evt.setFileName(fileName);
        return evt;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getPercent() { return percent; }
    public void setPercent(double percent) { this.percent = percent; }

    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }

    public String getEta() { return eta; }
    public void setEta(String eta) { this.eta = eta; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
