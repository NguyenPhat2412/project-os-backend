package com.projectos.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = AiModuleContextTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:ai_context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class AiModuleContextTest {
    @Test
    void aiModuleConfigurationLoads(ApplicationContext context) {
        assertThat(context.getBean(AiModuleConfiguration.class)).isNotNull();
        assertThat(context.getBean(AiConversationRepository.class)).isNotNull();
        assertThat(context.getBean(AiMessageRepository.class)).isNotNull();
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication(scanBasePackages = "com.projectos.backend")
    @org.springframework.boot.persistence.autoconfigure.EntityScan(basePackages = "com.projectos.backend")
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = "com.projectos.backend")
    static class TestApplication {
    }
}
