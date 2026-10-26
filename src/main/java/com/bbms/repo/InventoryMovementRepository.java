package com.bbms.repo;

import com.bbms.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement,Long> {
    List<InventoryMovement> findByUnitIdOrderByOccurredAtDesc(Long unitId);
    long countByDemoDataTrue();
}
