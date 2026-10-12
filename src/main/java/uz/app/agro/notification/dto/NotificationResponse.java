package uz.app.agro.notification.dto;

import uz.app.agro.notification.ChannelType;
import uz.app.agro.notification.NotificationCategory;
import uz.app.agro.notification.NotificationStatus;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id, Long farmerId, String farmerName, ChannelType channel, NotificationCategory category, String template,
        String preview, NotificationStatus status, int attempts, String lastError, Long campaignId,
        LocalDateTime createdDate, LocalDateTime nextAttemptAt, LocalDateTime sentAt
) {}
