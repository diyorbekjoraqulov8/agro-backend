package uz.app.agro.billing;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uz.app.agro.billing.dto.PlanResponse;
import uz.app.agro.billing.dto.SubscriptionResponse;

import java.time.LocalDate;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BillingMapper {

    PlanResponse toResponse(PlanEntity plan);

    @Mapping(target = "farmerId", source = "s.farmer.id")
    @Mapping(target = "planId", source = "s.plan.id")
    @Mapping(target = "planCode", source = "s.plan.code")
    @Mapping(target = "planName", source = "s.plan.name")
    @Mapping(target = "activeToday", expression = "java(s.isActiveOn(today))")
    SubscriptionResponse toResponse(SubscriptionEntity s, LocalDate today);
}
