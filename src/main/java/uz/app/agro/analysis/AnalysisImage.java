package uz.app.agro.analysis;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tahlil rasmlari — fayl servisida (files), bu yerda hashId'lar. map_png/chart_png — eski (V20 gacha) yoki
 * zaxira: faylni saqlab bo'lmaganda tahlil yo'qolmasin. Fonda files'ga ko'chiriladi (AnalysisImageBackfill).
 * Bog'lanish yo'q — faqat kerak bo'lganda findById(jobId).
 */
@Entity
@Table(name = "analysis_images")
@Getter
@Setter
public class AnalysisImage {

    @Id
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "map_file_hash_id", length = 32)
    private String mapFileHashId;

    @Column(name = "chart_file_hash_id", length = 32)
    private String chartFileHashId;

    @Column(name = "ndvi_file_hash_id", length = 32)
    private String ndviFileHashId;                  // L1b debug: mutlaq NDVI xaritasi — faqat faylda, zaxirasiz

    @Column(name = "map_png")
    private byte[] mapPng;

    @Column(name = "chart_png")
    private byte[] chartPng;
}
