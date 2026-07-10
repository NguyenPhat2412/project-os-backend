package vn.uytinmang.projectos.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "vn.uytinmang.projectos")
@EntityScan(basePackages = {"vn.uytinmang.projectos.project", "vn.uytinmang.projectos.resource"})
@EnableJpaRepositories(basePackages = {"vn.uytinmang.projectos.project", "vn.uytinmang.projectos.resource"})
public class ProjectServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }
}
