package uz.app.agro.billing.dto;

import uz.app.agro.billing.PaymentMethod;
import uz.app.agro.billing.SubscriptionEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubscriptionResponse(
        Long id, Long farmerId, Long planId, String planCode, String planName,
        SubscriptionEntity.Status status, boolean activeToday,
        LocalDate validFrom, LocalDate validUntil,
        BigDecimal amount, String currency, LocalDateTime paidAt, PaymentMethod paymentMethod, String note,
        String recordedByEmail, LocalDateTime cancelledAt, String cancelReason, LocalDateTime createdDate,
        Long discountId                 // qaysi chegirma bilan (null — chegirmasiz)
) {}
