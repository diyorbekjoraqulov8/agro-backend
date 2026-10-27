package uz.app.agro.accuracy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.app.agro.accuracy.FindingReviewEntity;

public record ReviewRequest(@NotNull FindingReviewEntity.Verdict verdict, @Size(max = 1000) String note) {}
