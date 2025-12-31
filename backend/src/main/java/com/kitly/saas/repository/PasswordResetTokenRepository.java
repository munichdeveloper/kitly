package com.kitly.saas.repository;

import com.kitly.saas.entity.PasswordResetToken;
import com.kitly.saas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUser(User user);

    void deleteByExpiryDateBefore(LocalDateTime date);
}

