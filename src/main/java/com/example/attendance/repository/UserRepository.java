package com.example.attendance.repository;

import com.example.attendance.model.Role;
import com.example.attendance.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByUsernameIgnoreCase(String username);
    Boolean existsByUsername(String username);
    Boolean existsByUsernameIgnoreCase(String username);
    Boolean existsByEmail(String email);
    Boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByResetToken(String resetToken);
    List<User> findByRole(Role role);
    List<User> findByRoleAndClassNameIgnoreCase(Role role, String className);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query(value = "ALTER TABLE users MODIFY COLUMN role VARCHAR(20)", nativeQuery = true)
    void alterRoleColumnToVarchar();
}
