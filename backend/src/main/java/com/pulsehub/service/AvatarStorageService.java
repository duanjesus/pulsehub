package com.pulsehub.service;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarStorageService {

    /** Persists the file to disk and returns the public URL path clients should use to fetch it. */
    String store(Long userId, MultipartFile file);
}
