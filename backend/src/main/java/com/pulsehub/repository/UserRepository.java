package com.pulsehub.repository;

import com.pulsehub.entity.User;
import com.pulsehub.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByIdNotOrderByNameAsc(Long id);

    List<User> findByStatusInAndLastActivityAtBefore(List<UserStatus> statuses, LocalDateTime threshold);
}
