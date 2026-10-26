package uz.app.agro.field.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uz.app.agro.farmer.IrrigationType;
import uz.app.agro.field.Crop;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin xaritada chizgan Lab dala.
 * @param coordinates halqa [[lon, lat], ...] — yopilmagan bo'lsa server yopadi; 3–200 uch
 * @param sownOn ixtiyoriy: berilsa — mavsum "ekilgan", aks holda so'ralmagan
 */
public record CreateLabFieldRequest(
        @NotBlank @Size(max = 80) String name,
        @NotNull Crop crop,
        @NotNull @Size(min = 3, max = 201) List<@NotNull @Size(min = 2, max = 2) List<@NotNull Double>> coordinates,
        LocalDate sownOn,
        IrrigationType irrigationMethod
) {}
