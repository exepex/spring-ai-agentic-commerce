package io.github.exepex.commerce.shipping;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByOrderId(UUID orderId);

    /** Locks the shipment, so two reports about the same parcel are taken one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shipment from Shipment shipment where shipment.orderId = :orderId")
    Optional<Shipment> findForUpdate(@Param("orderId") UUID orderId);

    List<Shipment> findTop100ByStatusInOrderByCreatedAtDesc(Collection<Shipment.Status> statuses);

    List<Shipment> findTop100ByOrderByCreatedAtDesc();
}
