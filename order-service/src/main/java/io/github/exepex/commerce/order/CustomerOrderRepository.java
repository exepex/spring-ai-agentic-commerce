package io.github.exepex.commerce.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    List<CustomerOrder> findTop100ByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    List<CustomerOrder> findTop100ByOrderByCreatedAtDesc();

    List<CustomerOrder> findTop50ByStatusInAndCreatedAtBeforeOrderByCreatedAt(List<OrderStatus> statuses,
            Instant createdBefore);
}
