package uz.app.agro.billing.dto;

import jakarta.validation.constraints.*;
import uz.app.agro.billing.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * "To'lov qildi". Bo'sh maydonlar: amount/currency — tarif narxidan, paidAt — hozir,
 * validFrom — bugun yoki joriy obuna tugagan kundan keyin, validUntil — validFrom + tarif muddati.
 */
public record PaymentRequest(
        @NotNull(message = "planId ko'rsatilishi shart") Long planId,
        @DecimalMin(value = "0", message = "amount manfiy bo'lmasin") @Digits(integer = 10, fraction = 2, message = "amount: 10 xona + 2 kasr")
        BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}", message = "currency: ISO 4217 (UZS, USD)") String currency,
        @PastOrPresent(message = "paidAt kelajakda bo'lmasin") LocalDateTime paidAt,
        @NotNull(message = "paymentMethod ko'rsatilishi shart") PaymentMethod paymentMethod,
        LocalDate validFrom,
        LocalDate validUntil,
        @Size(max = 1000, message = "note ko'pi bilan 1000 belgi") String note
) {}
