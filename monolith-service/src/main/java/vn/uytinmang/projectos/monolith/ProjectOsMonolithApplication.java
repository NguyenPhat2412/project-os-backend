package vn.uytinmang.projectos.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "vn.uytinmang.projectos")
@EntityScan(basePackages = "vn.uytinmang.projectos")
@EnableJpaRepositories(basePackages = "vn.uytinmang.projectos")
@EnableScheduling
public class ProjectOsMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectOsMonolithApplication.class, args);
    }
}
