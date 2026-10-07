package com.pulsehub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "pulsehub.call")
public record CallProperties(List<IceServer> iceServers) {

    public CallProperties {
        iceServers = iceServers == null ? List.of() : List.copyOf(iceServers);
    }

    public record IceServer(List<String> urls, String username, String credential) {
    }
}
