package com.ytdownloader.controller;

import com.ytdownloader.dto.DownloadRequest;
import com.ytdownloader.dto.DownloadResponse;
import com.ytdownloader.dto.StartResponse;
import com.ytdownloader.service.DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DownloadController {

    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);

    private final DownloadService downloadService;

    @Value("${app.welcome-message:Welcome to the YouTube Video Downloader}")
    private String welcomeMessage;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @GetMapping("/welcome")
    public ResponseEntity<Map<String, String>> getWelcomeMessage() {
        return ResponseEntity.ok(Map.of("message", welcomeMessage));
    }

    /**
     * Step 1: Start download asynchronously. Returns a Job ID immediately.
     */
    @PostMapping("/download/start")
    public ResponseEntity<?> startDownload(@RequestBody DownloadRequest request) {
        String url = request.getUrl();
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(DownloadResponse.error("URL must not be empty."));
        }
        
        String jobId = UUID.randomUUID().toString();
        log.info("Starting async download for {}, JobID: {}", url, jobId);
        
        return ResponseEntity.ok(new StartResponse(jobId));
    }

    /**
     * Step 2: Subscribe to SSE progress events for a specific Job ID.
     */
    @GetMapping(value = "/download/{jobId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable String jobId, @RequestParam String url) {
        return downloadService.startDownloadAsync(url.trim(), jobId);
    }

    /**
     * Step 3: Download the final file after SSE says DONE.
     */
    @GetMapping("/download/{jobId}/file")
    public ResponseEntity<Resource> downloadFile(@PathVariable String jobId) {
        DownloadService.JobState state = downloadService.getJobState(jobId);
        
        if (state == null || !state.done || state.error || state.downloadedFile == null) {
            log.warn("Invalid file request for job {}", jobId);
            return ResponseEntity.notFound().build();
        }

        Path filePath = state.downloadedFile;
        String fileName = state.fileName;
        Resource resource = new FileSystemResource(filePath);

        log.info("Streaming file to client: {}", fileName);
        Path tempDir = filePath.getParent();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitizeFileName(fileName) + "\"")
                .body(new CleanupResource(resource, tempDir, downloadService, jobId));
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\"\\\\]", "_");
    }

    private record CleanupResource(
            Resource delegate, Path tempDir, DownloadService service, String jobId
    ) implements Resource {
        @Override public java.io.InputStream getInputStream() throws java.io.IOException {
            return new java.io.FilterInputStream(delegate.getInputStream()) {
                @Override
                public void close() throws java.io.IOException {
                    try { super.close(); }
                    finally { 
                        service.cleanUp(tempDir);
                        service.removeJob(jobId);
                    }
                }
            };
        }
        // delegate all other methods
        @Override public boolean exists() { return delegate.exists(); }
        @Override public java.net.URL getURL() throws java.io.IOException { return delegate.getURL(); }
        @Override public java.net.URI getURI() throws java.io.IOException { return delegate.getURI(); }
        @Override public java.io.File getFile() throws java.io.IOException { return delegate.getFile(); }
        @Override public long contentLength() throws java.io.IOException { return delegate.contentLength(); }
        @Override public long lastModified() throws java.io.IOException { return delegate.lastModified(); }
        @Override public Resource createRelative(String path) throws java.io.IOException { return delegate.createRelative(path); }
        @Override public String getFilename() { return delegate.getFilename(); }
        @Override public String getDescription() { return delegate.getDescription(); }
    }
}
