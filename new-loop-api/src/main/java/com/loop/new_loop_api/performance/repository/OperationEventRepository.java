package com.loop.new_loop_api.performance.repository;

import com.loop.new_loop_api.performance.entity.OperationEvent;
import com.loop.new_loop_api.performance.entity.OperationEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OperationEventRepository extends JpaRepository<OperationEvent, UUID> {

    /** How many events of a type a subject already has, e.g. previous driver rejections of a control. */
    long countByEventTypeAndSubjectId(OperationEventType eventType, UUID subjectId);
}
