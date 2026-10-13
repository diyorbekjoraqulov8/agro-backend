package uz.app.agro.analysis;

import uz.app.agro.field.Crop;

import java.time.LocalDate;
import java.util.Map;

// Tahlil uchun kerakli hamma narsa — tranzaksiya ichida bir marta o'qiladi (LAZY tashqarida yo'q)
public record JobInput(
        long jobId,
        AnalysisJob.Kind kind,
        long fieldId,
        String fieldName,
        Map<String, Object> geometry,
        Crop crop,
        LocalDate seasonStart,
        LocalDate seasonEnd,
        long farmerId
) {}
