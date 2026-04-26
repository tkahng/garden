package io.k2dv.garden.automation;

import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTagService {

    private static final Set<OrderStatus> CONFIRMED = Set.of(
        OrderStatus.PAID, OrderStatus.PARTIALLY_FULFILLED, OrderStatus.FULFILLED);

    private final UserRepository userRepo;
    private final OrderRepository orderRepo;
    private final AppProperties appProperties;

    @Transactional
    public void applyOrderTags(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        long orderCount = orderRepo.countByUserIdAndStatusIn(userId, CONFIRMED);
        BigDecimal totalSpend = orderRepo.sumSpendByUserId(userId, CONFIRMED);

        List<String> tags = new ArrayList<>(user.getTags() != null ? user.getTags() : List.of());

        if (orderCount == 1) {
            addTag(tags, "first-time-buyer");
        } else if (orderCount >= 2) {
            tags.remove("first-time-buyer");
            addTag(tags, "repeat-customer");
        }

        if (orderCount >= 5) {
            addTag(tags, "loyal-customer");
        }

        BigDecimal vipThreshold = appProperties.getAutomation().getVipSpendThreshold();
        if (totalSpend != null && totalSpend.compareTo(vipThreshold) >= 0) {
            addTag(tags, "vip");
        }

        user.setTags(tags);
        userRepo.save(user);
        log.debug("Applied auto-tags to user {}: {}", userId, tags);
    }

    private static void addTag(List<String> tags, String tag) {
        if (!tags.contains(tag)) tags.add(tag);
    }
}
