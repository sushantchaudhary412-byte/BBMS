package com.bbms.repo;
import com.bbms.domain.BloodIssue; import com.bbms.domain.BloodRequest; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface BloodIssueRepository extends JpaRepository<BloodIssue,Long>{ long count(); long countByDemoDataTrue(); Optional<BloodIssue> findByRequest(BloodRequest request); }
