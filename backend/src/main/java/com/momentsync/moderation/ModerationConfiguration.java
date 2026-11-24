package com.momentsync.moderation;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
class ModerationConfiguration {
    @Bean
    @ConditionalOnMissingBean(ContentPolicy.class)
    ContentPolicy contentPolicy(
            @Value("${moment.interaction.sensitive-words:classpath:interaction-sensitive-words.txt}") Resource words)
            throws IOException {
        return new LocalWordContentPolicy(words);
    }
}
