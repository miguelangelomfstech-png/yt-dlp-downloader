package com.ytdownloader.dto;

import java.nio.file.Path;

/**
 * Internal result object returned by {@link com.ytdownloader.service.DownloadService}.
 * Contains either a successfully downloaded file path or an error message.
 * This is NOT a REST DTO — it is used only between Service and Controller.
 */
public class DownloadResult {

    private final boolean success;
    private final String errorMessage;
    private final Path filePath;
    private final String fileName;

    private DownloadResult(boolean success, String errorMessage, Path filePath, String fileName) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.filePath = filePath;
        this.fileName = fileName;
    }

    public static DownloadResult ok(Path filePath, String fileName) {
        return new DownloadResult(true, null, filePath, fileName);
    }

    public static DownloadResult error(String message) {
        return new DownloadResult(false, message, null, null);
    }

    public boolean isSuccess()       { return success; }
    public String getErrorMessage()  { return errorMessage; }
    public Path getFilePath()        { return filePath; }
    public String getFileName()      { return fileName; }
}
