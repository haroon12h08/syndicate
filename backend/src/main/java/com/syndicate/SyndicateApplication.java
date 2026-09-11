package com.syndicate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class SyndicateApplication {
    public static void main(String[] args) {
        SpringApplication.run(SyndicateApplication.class, args);
    }
}
