package io.k2dv.garden.giftcard.repository;

import io.k2dv.garden.giftcard.model.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface GiftCardRepository extends JpaRepository<GiftCard, UUID>, JpaSpecificationExecutor<GiftCard> {

    Optional<GiftCard> findByCodeIgnoreCase(String code);

    @Modifying
    @Query("""
        UPDATE GiftCard g SET g.currentBalance = g.currentBalance - :amount
        WHERE g.id = :id AND g.currentBalance >= :amount
        """)
    int atomicDebit(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
