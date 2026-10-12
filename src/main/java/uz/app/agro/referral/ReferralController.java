package uz.app.agro.referral;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.Pages;
import uz.app.agro.referral.dto.FarmerReferral;
import uz.app.agro.referral.dto.ReferralStats;
import uz.app.agro.referral.dto.ReferrerRow;

import java.util.Set;

@Tag(name = "Takliflar", description = "Do'stlarni taklif qilish: kim kimni taklif qilgan, mukofotlar")
@RestController
@RequestMapping("/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralAdminService service;

    @Operation(summary = "Statistika", description = "Huquq: `referral:read`")
    @GetMapping("/stats")
    public ReferralStats stats() {
        return service.stats();
    }

    @Operation(summary = "Taklif qilganlar", description = "Huquq: `referral:read`. Eng ko'p taklif qilgani tepada; `q` — ism, username, kod")
    @GetMapping
    public PageResponse<ReferrerRow> referrers(@RequestParam(required = false) String q,
                                               @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        // Tartib qat'iy (taklif soni bo'yicha) — mijoz sort'i qabul qilinmaydi
        return service.referrers(q, Pages.guard(pageable.getSort().isSorted() ? Pageable.ofSize(pageable.getPageSize())
                .withPage(pageable.getPageNumber()) : pageable, Set.of()));
    }

    @Operation(summary = "Fermerning taklif ma'lumoti", description = "Huquq: `referral:read`")
    @GetMapping("/farmers/{farmerId}")
    public FarmerReferral forFarmer(@PathVariable Long farmerId) {
        return service.forFarmer(farmerId);
    }
}
