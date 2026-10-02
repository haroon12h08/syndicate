package com.syndicate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@EnableJpaAuditing
public class SyndicateApplication {
    public static void main(String[] args) {
        SpringApplication.run(SyndicateApplication.class, args);
    }
}
