package com.momentsync.interaction;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** 宿主通过 @Import 接入，必须提供 DataSource、事务管理器以及 CurrentUserProvider。 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@Import({InteractionController.class, InteractionAdvice.class})
public class InteractionConfiguration {
    @Bean public JdbcInteractionRepository interactionRepository(NamedParameterJdbcTemplate jdbc) {
        return new JdbcInteractionRepository(jdbc);
    }
    @Bean @ConditionalOnMissingBean(ContentPolicy.class)
    public ContentPolicy contentPolicy(@Value("${moment.interaction.sensitive-words:classpath:interaction-sensitive-words.txt}") Resource words) throws IOException {
        return new LocalWordContentPolicy(words);
    }
    @Bean public InteractionService interactionService(PostSearchRepository posts, CommentRepository comments, ContentPolicy policy) {
        return new InteractionService(posts, comments, policy);
    }
}
