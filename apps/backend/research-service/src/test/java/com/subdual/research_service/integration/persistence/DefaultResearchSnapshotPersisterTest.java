package com.subdual.research_service.integration.persistence;

import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultResearchSnapshotPersisterTest {

    @Mock
    private DatasetPersistenceClient persistenceClient;

    @Test
    @DisplayName("Should delegate entity persistence to client successfully")
    void shouldDelegatePersistenceSuccessfully() {
        DefaultResearchSnapshotPersister persister = new DefaultResearchSnapshotPersister(persistenceClient);
        ResearchTarget target = new ResearchTarget("https://example.com", "https://example.com", "t1", EntityType.ORGANIZATION, "Acme");
        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        persister.persistSnapshot(target, List.of(), Map.of(), diagnostics);

        verify(persistenceClient).persistEntity(target, List.of(), Map.of());
        assertThat(diagnostics.warningCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should swallow exceptions from persistence client and record warning in diagnostics")
    void shouldSwallowPersistenceExceptionsAndRecordWarning() {
        DefaultResearchSnapshotPersister persister = new DefaultResearchSnapshotPersister(persistenceClient);
        ResearchTarget target = new ResearchTarget("https://example.com", "https://example.com", "t1", EntityType.ORGANIZATION, "Acme");
        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        doThrow(new RuntimeException("Database offline"))
                .when(persistenceClient).persistEntity(any(), any(), any());

        assertThatCode(() -> persister.persistSnapshot(target, List.of(), Map.of(), diagnostics))
                .doesNotThrowAnyException();

        assertThat(diagnostics.warningCount()).isEqualTo(1);
        assertThat(diagnostics.warnings().get(0)).contains("Database offline");
    }

    @Test
    @DisplayName("Should safely handle null persistenceClient using Null Object pattern")
    void shouldHandleNullClientSafely() {
        DefaultResearchSnapshotPersister persister = new DefaultResearchSnapshotPersister((DatasetPersistenceClient) null);
        ResearchTarget target = new ResearchTarget("https://example.com", "https://example.com", "t1", EntityType.ORGANIZATION, "Acme");
        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        assertThatCode(() -> persister.persistSnapshot(target, List.of(), Map.of(), diagnostics))
                .doesNotThrowAnyException();
        assertThat(diagnostics.warningCount()).isEqualTo(0);
    }
}
