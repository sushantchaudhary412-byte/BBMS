package com.bbms.repo;

import com.bbms.domain.BloodRequestEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface BloodRequestEventRepository extends JpaRepository<BloodRequestEvent,Long> {
    boolean existsByRequest(com.bbms.domain.BloodRequest request);
    @Query("select e from BloodRequestEvent e where e.request.id in :ids order by e.occurredAt asc")
    List<BloodRequestEvent> findForRequests(@Param("ids") Collection<Long> requestIds);
    long countByDemoDataTrue();
}
