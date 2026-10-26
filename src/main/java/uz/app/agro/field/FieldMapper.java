package uz.app.agro.field;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uz.app.agro.field.dto.FieldResponse;
import uz.app.agro.field.dto.FieldSummary;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FieldMapper {

    // farmer.id — LAZY proxy'ni initsializatsiya qilmaydi (id proxy'da bor)
    @Mapping(target = "farmerId", source = "farmer.id")
    FieldSummary toSummary(FieldEntity entity);

    List<FieldSummary> toSummaryList(List<FieldEntity> entities);

    // Ikki manbada bir xil nomli (id, crop, createdDate) — dala'dan
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "crop", source = "entity.crop")
    @Mapping(target = "createdDate", source = "entity.createdDate")
    @Mapping(target = "farmerId", source = "entity.farmer.id")
    @Mapping(target = "farmerName", source = "entity.farmer.fullName")
    @Mapping(target = "lab", source = "entity.farmer.lab")
    @Mapping(target = "season", source = "season")
    FieldResponse toResponse(FieldEntity entity, FieldSeasonEntity season);

    FieldResponse.Season toSeason(FieldSeasonEntity season);
}
