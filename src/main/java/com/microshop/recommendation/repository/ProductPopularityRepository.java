package com.microshop.recommendation.repository;

import com.microshop.recommendation.entity.ProductPopularity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ProductPopularityRepository extends JpaRepository<ProductPopularity, Long> {

    /** Top productos por score global, excluyendo los ya comprados */
    @Query("SELECT p FROM ProductPopularity p WHERE p.productId NOT IN :excludedIds ORDER BY p.score DESC")
    List<ProductPopularity> findTopExcluding(@Param("excludedIds") Set<Long> excludedIds, Pageable pageable);

    /** Top productos (sin exclusiones) para el endpoint de populares general */
    @Query("SELECT p FROM ProductPopularity p ORDER BY p.score DESC")
    List<ProductPopularity> findTopByScore(Pageable pageable);

    /** Productos de una categoría específica ordenados por score */
    @Query("SELECT p FROM ProductPopularity p WHERE p.categoryId = :categoryId " +
           "AND p.productId NOT IN :excludedIds ORDER BY p.score DESC")
    List<ProductPopularity> findByCategoryIdExcluding(
            @Param("categoryId") Long categoryId,
            @Param("excludedIds") Set<Long> excludedIds,
            Pageable pageable);

    /** Productos similares: misma categoría, distinto producto */
    @Query("SELECT p FROM ProductPopularity p WHERE p.categoryId = :categoryId " +
           "AND p.productId <> :productId ORDER BY p.score DESC")
    List<ProductPopularity> findSimilarByCategory(
            @Param("categoryId") Long categoryId,
            @Param("productId") Long productId,
            Pageable pageable);
}
