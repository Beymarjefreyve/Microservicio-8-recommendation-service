package com.microshop.recommendation.repository;

import com.microshop.recommendation.entity.PurchaseHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface PurchaseHistoryRepository extends JpaRepository<PurchaseHistory, Long> {

    /** Todos los productIds comprados por el usuario (para exclusión) */
    @Query("SELECT DISTINCT p.productId FROM PurchaseHistory p WHERE p.userId = :userId")
    Set<Long> findPurchasedProductIdsByUserId(@Param("userId") Long userId);

    /** Categorías únicas compradas por el usuario */
    @Query("SELECT DISTINCT p.categoryId FROM PurchaseHistory p WHERE p.userId = :userId")
    List<Long> findPurchasedCategoryIdsByUserId(@Param("userId") Long userId);

    /** Evita registrar la misma combinación orden+producto dos veces */
    boolean existsByOrderIdAndProductId(Long orderId, Long productId);
}
