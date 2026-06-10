package com.microshop.recommendation.repository;

import com.microshop.recommendation.entity.ProductView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface ProductViewRepository extends JpaRepository<ProductView, Long> {

    /** Todos los productIds vistos por un usuario (con duplicados para frecuencia) */
    @Query("SELECT v.productId FROM ProductView v WHERE v.userId = :userId")
    List<Long> findProductIdsByUserId(@Param("userId") Long userId);

    /** Categorías que el usuario ha visto con su frecuencia */
    @Query("SELECT v.categoryId AS categoryId, COUNT(v) AS freq " +
           "FROM ProductView v WHERE v.userId = :userId " +
           "GROUP BY v.categoryId ORDER BY freq DESC")
    List<Map<String, Object>> findCategoryFrequencyByUserId(@Param("userId") Long userId);

    /** Frecuencia de vistas de un producto específico por usuario */
    @Query("SELECT COUNT(v) FROM ProductView v WHERE v.userId = :userId AND v.productId = :productId")
    Long countByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    /** Categorías únicas vistas por el usuario */
    @Query("SELECT DISTINCT v.categoryId FROM ProductView v WHERE v.userId = :userId")
    List<Long> findDistinctCategoryIdsByUserId(@Param("userId") Long userId);
}
