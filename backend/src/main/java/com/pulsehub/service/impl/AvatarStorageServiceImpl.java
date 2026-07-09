package com.pulsehub.service.impl;

import com.pulsehub.exception.BusinessException;
import com.pulsehub.service.AvatarStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageServiceImpl implements AvatarStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final Path uploadsDir;

    public AvatarStorageServiceImpl(@Value("${pulsehub.uploads.dir}") String uploadsDir) {
        this.uploadsDir = Path.of(uploadsDir, "avatars");
        try {
            Files.createDirectories(this.uploadsDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create avatar uploads directory", e);
        }
    }

    @Override
    public String store(Long userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("Avatar file must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("Avatar file must be at most 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("Avatar must be a PNG, JPEG, WEBP or GIF image");
        }

        String extension = extensionFor(contentType);
        String filename = userId + "-" + UUID.randomUUID() + extension;

        try {
            Files.copy(file.getInputStream(), uploadsDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store avatar file", e);
        }

        return "/uploads/avatars/" + filename;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }

}
