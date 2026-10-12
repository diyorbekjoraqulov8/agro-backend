package uz.app.agro.analysis.dto;

import java.time.LocalDate;

// Ikkalasi bo'sh — oxirgi 12 oy (bot bilan bir xil oyna)
public record RunAnalysisRequest(
        LocalDate seasonStart,
        LocalDate seasonEnd
) {}
