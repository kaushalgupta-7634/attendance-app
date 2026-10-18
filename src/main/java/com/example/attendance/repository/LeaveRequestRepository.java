package com.example.attendance.repository;

import com.example.attendance.model.LeaveRequest;
import com.example.attendance.model.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByStudentIdOrderByAppliedAtDesc(Long studentId);

    List<LeaveRequest> findByStatusOrderByAppliedAtDesc(LeaveStatus status);

    List<LeaveRequest> findByClassNameInIgnoreCaseAndStatusOrderByAppliedAtDesc(Collection<String> classNames, LeaveStatus status);

    List<LeaveRequest> findByClassNameIgnoreCaseAndStatusOrderByAppliedAtDesc(String className, LeaveStatus status);
}
