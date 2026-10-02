package io.github.exepex.commerce.mcp.governance;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderProposalRepository extends JpaRepository<OrderProposal, UUID> {}
