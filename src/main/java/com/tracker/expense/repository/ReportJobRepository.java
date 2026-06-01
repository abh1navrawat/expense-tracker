package com.tracker.expense.repository;

import com.tracker.expense.model.ReportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportJobRepository extends JpaRepository<ReportJob, Long> {
    Optional<ReportJob> findByTrackingId(UUID trackingId);
    List<ReportJob> findByUserIdOrderByDateCreatedDesc(Long userId);
}
