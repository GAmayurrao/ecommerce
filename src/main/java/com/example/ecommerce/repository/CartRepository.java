package com.example.ecommerce.repository;

import com.example.ecommerce.model.Cart;
import com.example.ecommerce.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUser(User user);

    Optional<Cart> findByUserId(Long userId);

    Optional<Cart> findBySessionId(String sessionId);

    boolean existsByUserId(Long userId);

    boolean existsBySessionId(String sessionId);

    @Query("SELECT c FROM Cart c WHERE c.expiresAt < :now")
    List<Cart> findExpiredCarts(LocalDateTime now);

    @Query("SELECT c FROM Cart c WHERE c.sessionId IS NOT NULL AND c.expiresAt < :now")
    List<Cart> findExpiredGuestCarts(LocalDateTime now);

    void deleteBySessionId(String sessionId);
}

