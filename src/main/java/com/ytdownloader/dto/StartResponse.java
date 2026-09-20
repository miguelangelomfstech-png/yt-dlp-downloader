package com.ytdownloader.dto;

public class StartResponse {
    private String jobId;

    public StartResponse() {}

    public StartResponse(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
}
