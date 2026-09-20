package com.ytdownloader.service;

import com.ytdownloader.dto.DownloadResult;
import com.ytdownloader.dto.ProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    @Value("${app.ytdlp.executable:yt-dlp}")
    private String ytDlpExecutable;

    // Manage jobs and their SSE emitters
    private final ConcurrentHashMap<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // Regex to parse yt-dlp progress output:
    // [download]  15.2% of   20.50MiB at    1.23MiB/s ETA 00:08
    private static final Pattern PROGRESS_PATTERN = Pattern.compile(
            "\\[download\\]\\s+([\\d\\.]+).*?at\\s+([\\w\\.\\/]+).*?ETA\\s+([\\d:]+)"
    );

    public static class JobState {
        public SseEmitter emitter;
        public Path tempDir;
        public Path downloadedFile;
        public String fileName;
        public boolean done;
        public boolean error;

        public JobState(SseEmitter emitter, Path tempDir) {
            this.emitter = emitter;
            this.tempDir = tempDir;
        }
    }

    /**
     * Starts a download job asynchronously and returns an SseEmitter for the client to subscribe to.
     */
    public SseEmitter startDownloadAsync(String url, String jobId) {
        SseEmitter emitter = new SseEmitter(3600000L); // 1 hour timeout
        
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("ytdlp_");
            log.info("Created temp directory for job {}: {}", jobId, tempDir);
        } catch (IOException e) {
            log.error("Failed to create temp dir", e);
            sendEventSafe(emitter, ProgressEvent.error("Could not create temp directory"));
            emitter.complete();
            return emitter;
        }

        JobState state = new JobState(emitter, tempDir);
        jobs.put(jobId, state);

        emitter.onCompletion(() -> log.debug("SSE completed for job {}", jobId));
        emitter.onTimeout(() -> {
            log.warn("SSE timeout for job {}", jobId);
            emitter.complete();
        });

        // Start background process
        executorService.submit(() -> processDownload(url, jobId, state));

        return emitter;
    }
    
    public JobState getJobState(String jobId) {
        return jobs.get(jobId);
    }
    
    public void removeJob(String jobId) {
        jobs.remove(jobId);
    }

    private void processDownload(String url, String jobId, JobState state) {
        sendEventSafe(state.emitter, new ProgressEvent("STARTING", 0, "", "", "Starting download engine..."));
        
        try {
            StringBuilder errorLog = new StringBuilder();
            int exitCode = executeYtDlpWithProgress(url, state.tempDir, state.emitter, errorLog);

            if (exitCode != 0) {
                log.error("yt-dlp exited with code {} for job {}. Log: {}", exitCode, jobId, errorLog);
                state.error = true;
                
                String cleanError = errorLog.toString().replaceAll("\n", " | ").trim();
                if (cleanError.isEmpty()) cleanError = "Unknown error";
                if (cleanError.length() > 100) cleanError = cleanError.substring(cleanError.length() - 100);

                sendEventSafe(state.emitter, ProgressEvent.error("Error: " + cleanError));
                cleanUp(state.tempDir);
                state.emitter.complete();
                return;
            }

            // Find file
            Path downloadedFile = findDownloadedFile(state.tempDir);
            if (downloadedFile == null) {
                log.error("No file found in {} for job {}", state.tempDir, jobId);
                state.error = true;
                sendEventSafe(state.emitter, ProgressEvent.error("Download completed but no file found."));
                cleanUp(state.tempDir);
                state.emitter.complete();
                return;
            }

            // Success
            state.downloadedFile = downloadedFile;
            state.fileName = downloadedFile.getFileName().toString();
            state.done = true;
            log.info("Job {} successful: {}", jobId, state.fileName);
            
            sendEventSafe(state.emitter, ProgressEvent.done(state.fileName));
            state.emitter.complete();

        } catch (Exception e) {
            log.error("Error in job {}", jobId, e);
            state.error = true;
            sendEventSafe(state.emitter, ProgressEvent.error("Server error: " + e.getMessage()));
            cleanUp(state.tempDir);
            state.emitter.complete();
        }
    }

    private int executeYtDlpWithProgress(String url, Path targetDir, SseEmitter emitter, StringBuilder errorLog) throws IOException, InterruptedException {
        String outputTemplate = targetDir.toAbsolutePath() + "/%(title)s.%(ext)s";

        // --newline forces yt-dlp to print progress on new lines instead of carriage returns (\r)
        ProcessBuilder pb = new ProcessBuilder(
                ytDlpExecutable,
                "--newline",
                "--no-playlist",
                "--extractor-args", "youtube:player_client=android,web",
                "-o", outputTemplate,
                url
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // Keep last 5 lines for error reporting
        java.util.LinkedList<String> lastLines = new java.util.LinkedList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastLines.add(line);
                if (lastLines.size() > 5) lastLines.removeFirst();
                
                // Parse yt-dlp output for progress
                if (line.startsWith("[download]")) {
                    Matcher m = PROGRESS_PATTERN.matcher(line);
                    if (m.find()) {
                        try {
                            double percent = Double.parseDouble(m.group(1));
                            String speed = m.group(2);
                            String eta = m.group(3);
                            sendEventSafe(emitter, new ProgressEvent("DOWNLOADING", percent, speed, eta, "Downloading video..."));
                        } catch (NumberFormatException ignored) {}
                    } else if (line.contains("Destination:")) {
                        sendEventSafe(emitter, new ProgressEvent("DOWNLOADING", 0, "", "", "Downloading video..."));
                    } else if (line.contains("100%")) {
                        sendEventSafe(emitter, new ProgressEvent("PROCESSING", 100, "", "", "Merging audio and video..."));
                    }
                } else if (line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]")) {
                    sendEventSafe(emitter, new ProgressEvent("PROCESSING", 100, "", "", "Finalizing file..."));
                } else {
                    log.debug("[yt-dlp] {}", line);
                }
            }
        }
        
        for (String l : lastLines) {
            if (l.contains("ERROR:") || l.contains("WARNING:")) {
                errorLog.append(l).append("\n");
            }
        }
        if (errorLog.length() == 0 && !lastLines.isEmpty()) {
             errorLog.append(lastLines.getLast());
        }

        return process.waitFor();
    }

    private void sendEventSafe(SseEmitter emitter, ProgressEvent event) {
        try {
            emitter.send(event);
        } catch (Exception ignored) {
            // Client probably disconnected early
        }
    }

    public void cleanUp(Path path) {
        try {
            if (path == null || !Files.exists(path)) return;
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); }
                            catch (IOException ignored) {}
                        });
                }
            } else {
                Files.deleteIfExists(path);
            }
            log.debug("Cleaned up: {}", path);
        } catch (IOException e) {
            log.warn("Failed to clean up {}: {}", path, e.getMessage());
        }
    }

    private Path findDownloadedFile(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).findFirst().orElse(null);
        }
    }
}
