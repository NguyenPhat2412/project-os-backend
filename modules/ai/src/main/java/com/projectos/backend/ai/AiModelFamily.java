package com.projectos.backend.ai;

import java.util.Locale;

/** Provider family used by the UI to group the live model catalog. */
enum AiModelFamily {
    GEMINI,
    GPT,
    OTHER;

    static AiModelFamily from(AiModelView model) {
        String identifier = ((model.id() == null ? "" : model.id()) + " "
                + (model.ownedBy() == null ? "" : model.ownedBy())).toLowerCase(Locale.ROOT);
        if (identifier.contains("gemini") || identifier.contains("google")) return GEMINI;
        if (identifier.contains("gpt") || identifier.contains("openai")) return GPT;
        return OTHER;
    }
}
