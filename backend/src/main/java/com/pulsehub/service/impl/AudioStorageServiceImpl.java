package com.pulsehub.service.impl;

import com.pulsehub.exception.BusinessException;
import com.pulsehub.service.AudioStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class AudioStorageServiceImpl implements AudioStorageService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "audio/webm", ".webm",
            "audio/ogg", ".ogg",
            "audio/mp4", ".m4a",
            "audio/mpeg", ".mp3",
            "audio/wav", ".wav");
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final Path uploadsDir;

    public AudioStorageServiceImpl(@Value("${pulsehub.uploads.dir}") String uploadsDir) {
        this.uploadsDir = Path.of(uploadsDir, "voice");
        try {
            Files.createDirectories(this.uploadsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create voice message uploads directory", e);
        }
    }

    @Override
    public String store(Long senderId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("Voice message file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("Voice message must be at most 10MB");
        }

        String baseContentType = baseType(file.getContentType());
        String extension = ALLOWED_CONTENT_TYPES.get(baseContentType);
        if (extension == null) {
            throw new BusinessException("Voice message must be a WebM, OGG, MP4, MP3 or WAV audio file");
        }

        String filename = senderId + "-" + UUID.randomUUID() + extension;

        try {
            Files.copy(file.getInputStream(), uploadsDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store voice message file", e);
        }

        return "/uploads/voice/" + filename;
    }

    private String baseType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).trim().toLowerCase();
    }

}
