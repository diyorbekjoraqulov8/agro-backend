package uz.app.agro.accuracy.dto;

import uz.app.agro.accuracy.FindingReviewEntity;

import java.time.LocalDateTime;

public record ReviewResponse(FindingReviewEntity.TargetType targetType, Long targetId, FindingReviewEntity.Verdict verdict,
                             String note, String reviewerEmail, LocalDateTime updatedDate) {

    public static ReviewResponse of(FindingReviewEntity r) {
        return new ReviewResponse(r.getTargetType(), r.getTargetId(), r.getVerdict(), r.getNote(), r.getReviewerEmail(),
                r.getUpdatedDate() != null ? r.getUpdatedDate() : r.getCreatedDate());
    }
}
