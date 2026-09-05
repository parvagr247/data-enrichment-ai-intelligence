package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.ResearchTarget;

public interface SourceClassifier {
    String classify(String url, String title, String candidateType, ResearchTarget target);

    default String classify(String url, String candidateType, ResearchTarget target) {
        return classify(url, null, candidateType, target);
    }
}
