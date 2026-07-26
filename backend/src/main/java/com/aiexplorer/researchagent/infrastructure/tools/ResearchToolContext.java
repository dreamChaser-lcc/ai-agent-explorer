package com.aiexplorer.researchagent.infrastructure.tools;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchStepEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.ResearchTaskEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.entity.SourceDocumentEntity;
import java.util.List;

/**
 * 封装研究工具执行时所需的上下文信息。
 */
public record ResearchToolContext(
        ResearchTaskEntity task,
        ResearchStepEntity step,
        List<SourceDocumentEntity> existingSources) {
}
