package com.microshop.recommendation.ai.repository;

import com.microshop.recommendation.ai.entity.AiQueryLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiQueryLogRepository extends JpaRepository<AiQueryLog, Long> {

    List<AiQueryLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
