package uz.app.agro.file.dto;

import java.time.LocalDateTime;

/** url — shu fayl kontenti (GET, auth bilan). Ichki id va diskdagi yo'l tashqariga chiqmaydi */
public record FileResponse(
        String hashId, String name, String contentType, long size, Integer width, Integer height,
        boolean image, String url, LocalDateTime createdDate
) {}
