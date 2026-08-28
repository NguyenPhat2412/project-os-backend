package com.projectos.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiModelFamilyTest {
    @Test
    void classifiesGeminiModelsFromProviderIdentifier() {
        assertThat(AiModelFamily.from(new AiModelView("google/gemini-2.5-flash", "9router", "chat")))
                .isEqualTo(AiModelFamily.GEMINI);
    }

    @Test
    void classifiesGptModelsFromProviderIdentifier() {
        assertThat(AiModelFamily.from(new AiModelView("openai/gpt-4.1", "9router", "chat")))
                .isEqualTo(AiModelFamily.GPT);
    }

    @Test
    void keepsOtherProvidersInAnExplicitOtherGroup() {
        assertThat(AiModelFamily.from(new AiModelView("anthropic/claude-sonnet", "9router", "chat")))
                .isEqualTo(AiModelFamily.OTHER);
    }
}
