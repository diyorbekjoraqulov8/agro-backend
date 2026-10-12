package uz.app.agro.file.dto;

import uz.app.agro.file.FileSource;

import java.time.LocalDateTime;
import java.util.List;

/** Admin ro'yxati: fayl + kim yuklagan + qayerda ishlatilgan. thumbUrl — faqat rasmlarda */
public record FileAdminResponse(
        String hashId, String name, String contentType, long size, Integer width, Integer height, boolean image,
        String url, String thumbUrl, String sha256, FileSource source, String uploadedByEmail,
        LocalDateTime createdDate, List<FileUsage> usages
) {}
