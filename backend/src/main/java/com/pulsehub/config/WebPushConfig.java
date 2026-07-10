package com.pulsehub.config;

import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.GeneralSecurityException;
import java.security.Security;

@Configuration
public class WebPushConfig {

    // Must run before the pushService bean is constructed below — BC parses
    // the VAPID EC keys by provider name ("BC"), and Spring's bean creation
    // order isn't guaranteed to instantiate PushSubscriptionServiceImpl (whose
    // own static initializer also registers this) first.
    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Bean
    public PushService pushService(
            @Value("${pulsehub.push.vapid.public-key}") String publicKey,
            @Value("${pulsehub.push.vapid.private-key}") String privateKey,
            @Value("${pulsehub.push.vapid.subject}") String subject) throws GeneralSecurityException {
        return new PushService(publicKey, privateKey, subject);
    }

}
