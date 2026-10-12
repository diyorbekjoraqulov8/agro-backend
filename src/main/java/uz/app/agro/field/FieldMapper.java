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

    @Mapping(target = "farmerId", source = "farmer.id")
    @Mapping(target = "farmerName", source = "farmer.fullName")
    FieldResponse toResponse(FieldEntity entity);
}
