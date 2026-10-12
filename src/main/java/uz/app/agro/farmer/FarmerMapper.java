package uz.app.agro.farmer;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import uz.app.agro.billing.dto.EffectivePlan;
import uz.app.agro.farmer.dto.FarmerDetail;
import uz.app.agro.farmer.dto.FarmerListItem;
import uz.app.agro.field.dto.FieldSummary;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FarmerMapper {

    FarmerListItem toListItem(FarmerEntity entity);

    FarmerDetail toDetail(FarmerEntity entity, List<FieldSummary> fields, EffectivePlan plan);
}
