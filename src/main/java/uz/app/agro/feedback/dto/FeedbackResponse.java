package uz.app.agro.feedback.dto;

import uz.app.agro.feedback.FeedbackKind;
import uz.app.agro.feedback.FeedbackSender;
import uz.app.agro.feedback.FeedbackStatus;

import java.time.LocalDateTime;

public record FeedbackResponse(
        Long id, Long farmerId, String farmerName, String farmerUsername, String farmerLanguage,
        FeedbackKind kind, String subject, FeedbackStatus status,
        Long assigneeId, String assigneeEmail, LocalDateTime assignedAt,
        LocalDateTime lastMessageAt, FeedbackSender lastSender, int unreadStaff,
        LocalDateTime closedAt, FeedbackSender closedBy, String closedByEmail,
        Short rating, LocalDateTime ratedAt, LocalDateTime createdDate
) {}
