package uz.app.agro.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Mavsumiy obuna taklifi fermerga: from..until (mavsum oxiri), days — kunlar soni (narx kunlarga proporsional), foiz chegirma bilan */
public record SeasonOffer(Long planId, String planName, LocalDate from, LocalDate until, int days, int discountPct,
                          BigDecimal basePrice, BigDecimal finalPrice, String currency) {}
