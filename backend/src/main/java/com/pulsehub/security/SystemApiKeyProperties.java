package com.pulsehub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulsehub.security")
public record SystemApiKeyProperties(String systemApiKey) {
}
