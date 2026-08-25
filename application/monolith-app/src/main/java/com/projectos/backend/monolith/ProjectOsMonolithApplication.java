package com.projectos.backend.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = "com.projectos.backend")
@EntityScan(basePackages = "com.projectos.backend")
@EnableJpaRepositories(basePackages = "com.projectos.backend")
@EnableScheduling
public class ProjectOsMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectOsMonolithApplication.class, args);
    }
}
