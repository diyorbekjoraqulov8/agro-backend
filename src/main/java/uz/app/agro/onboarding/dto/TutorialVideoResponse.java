package uz.app.agro.onboarding.dto;

import java.time.LocalDateTime;

/** fileHashId null — bu til uchun video yuklanmagan */
public record TutorialVideoResponse(String language, String fileHashId, Long size, String name,
                                    LocalDateTime updatedDate, String updatedByEmail) {}
