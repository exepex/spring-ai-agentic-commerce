package io.github.exepex.commerce.mcp.cases;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CaseNoteRepository extends JpaRepository<CaseNote, UUID> {

    List<CaseNote> findBySentAtIsNullOrderByCreatedAt();

    List<CaseNote> findByCaseIdOrderByCreatedAt(UUID caseId);
}
