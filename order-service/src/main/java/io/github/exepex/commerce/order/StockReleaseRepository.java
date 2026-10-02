package io.github.exepex.commerce.order;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockReleaseRepository extends JpaRepository<StockRelease, UUID> {}
