package com.pulsehub.service;

import com.pulsehub.exception.BusinessException;
import com.pulsehub.service.impl.AudioStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioStorageServiceImplTest {

    private AudioStorageServiceImpl audioStorageService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        audioStorageService = new AudioStorageServiceImpl(tempDir.toString());
    }

    @Test
    void store_rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[0]);

        assertThatThrownBy(() -> audioStorageService.store(1L, file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void store_rejectsUnsupportedContentType() {
        var file = new MockMultipartFile("file", "voice.txt", "text/plain", "not audio".getBytes());

        assertThatThrownBy(() -> audioStorageService.store(1L, file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void store_acceptsWebmWithCodecParametersAndReturnsAPublicPath() {
        var file = new MockMultipartFile("file", "voice.webm", "audio/webm;codecs=opus", "fake-audio-bytes".getBytes());

        String url = audioStorageService.store(7L, file);

        assertThat(url).startsWith("/uploads/voice/7-").endsWith(".webm");
    }

}
