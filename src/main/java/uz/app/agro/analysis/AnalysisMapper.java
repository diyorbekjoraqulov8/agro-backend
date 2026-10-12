package uz.app.agro.analysis;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uz.app.agro.analysis.dto.AnalysisListItem;
import uz.app.agro.analysis.dto.AnalysisResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnalysisMapper {

    @Mapping(target = "fieldId", source = "field.id")
    AnalysisListItem toListItem(AnalysisJob job);

    @Mapping(target = "fieldId", source = "field.id")
    AnalysisResponse toResponse(AnalysisJob job);
}
