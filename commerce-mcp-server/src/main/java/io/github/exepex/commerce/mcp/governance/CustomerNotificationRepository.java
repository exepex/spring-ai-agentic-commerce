package io.github.exepex.commerce.mcp.governance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, UUID> {

    List<CustomerNotification> findByOrderIdOrderByCreatedAt(UUID orderId);

    Optional<CustomerNotification> findByIdempotencyKey(String idempotencyKey);

    List<CustomerNotification> findTop100ByOrderByCreatedAtDesc();

    List<CustomerNotification> findTop100ByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
}
