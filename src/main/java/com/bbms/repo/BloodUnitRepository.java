package com.bbms.repo;
import com.bbms.domain.BloodUnit; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType; import java.time.LocalDate; import java.util.*;
public interface BloodUnitRepository extends JpaRepository<BloodUnit,Long>{
 @Query("select coalesce(sum(u.quantity),0) from BloodUnit u where u.bloodGroup=:g and u.component=:c and u.status='RELEASED' and u.expiryDate>=:today and u.quantity>0 and u.demoData=false") long stock(@Param("g") String g,@Param("c") String c,@Param("today") LocalDate today);
 @Query("select coalesce(sum(u.quantity),0) from BloodUnit u where u.bloodGroup=:g and u.component=:c and u.status='RELEASED' and u.expiryDate>=:today and u.quantity>0 and u.demoData=:demoData") long stockBySource(@Param("g") String g,@Param("c") String c,@Param("today") LocalDate today,@Param("demoData") boolean demoData);
 List<BloodUnit> findAllByOrderByExpiryDateAsc();
 Optional<BloodUnit> findByBatchId(String batchId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from BloodUnit u where u.id=:id") Optional<BloodUnit> findByIdForUpdate(@Param("id") Long id);
 Optional<BloodUnit> findByDemoKey(String demoKey);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from BloodUnit u where u.bloodGroup=:g and u.component=:c and u.status='RELEASED' and u.expiryDate>=:today and u.quantity>0 and u.demoData=:demoData order by u.expiryDate asc") List<BloodUnit> availableForIssue(@Param("g") String g,@Param("c") String c,@Param("today") LocalDate today,@Param("demoData") boolean demoData);
 long countByDemoDataTrue();
}
