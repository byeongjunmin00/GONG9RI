package com.gong9ri.gong9ri;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class Gong9riApplication {

    public static void main(String[] args) {
        SpringApplication.run(Gong9riApplication.class, args);
    }
}
