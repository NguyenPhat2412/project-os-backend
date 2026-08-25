package com.projectos.backend.project;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.projectos.backend")
@EnableJpaRepositories(basePackages = "com.projectos.backend")
@EntityScan(basePackages = "com.projectos.backend")
class ProjectTestApplication {
}
