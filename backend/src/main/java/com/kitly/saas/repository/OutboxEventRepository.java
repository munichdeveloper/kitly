package com.kitly.saas.repository;

import com.kitly.saas.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    
    List<OutboxEvent> findByStatus(OutboxEvent.OutboxStatus status);
    
    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);
    
    List<OutboxEvent> findByAggregateType(String aggregateType);
    
    List<OutboxEvent> findByEventType(String eventType);
}
