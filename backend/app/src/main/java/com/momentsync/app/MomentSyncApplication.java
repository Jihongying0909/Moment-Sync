package com.momentsync.app;

import com.momentsync.interaction.InteractionConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(InteractionConfiguration.class)
public class MomentSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(MomentSyncApplication.class, args);
    }
}
