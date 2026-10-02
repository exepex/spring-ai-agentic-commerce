package io.github.exepex.commerce.catalog;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByOrderBySku();

    /** Loads a product with a row lock, so concurrent stock changes on it run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.id = :productId")
    Optional<Product> findForUpdate(UUID productId);
}
