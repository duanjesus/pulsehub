package com.pulsehub.config;

import com.pulsehub.service.impl.RedisRealtimeMessenger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RealtimeRelayConfig {

    @Bean
    public RedisMessageListenerContainer realtimeRelayListenerContainer(RedisConnectionFactory connectionFactory,
                                                                        RedisRealtimeMessenger relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new ChannelTopic(RedisRealtimeMessenger.CHANNEL));
        return container;
    }

}
