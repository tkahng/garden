package io.k2dv.garden.giftcard.repository;

import io.k2dv.garden.giftcard.model.GiftCardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GiftCardTransactionRepository extends JpaRepository<GiftCardTransaction, UUID> {
    List<GiftCardTransaction> findByGiftCardIdOrderByCreatedAtAsc(UUID giftCardId);
}
