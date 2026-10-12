package uz.app.agro.irrigation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sug'orish", description = "Sug'orish eslatmasi holati")
@RestController
@RequiredArgsConstructor
public class IrrigationController {

    private final IrrigationQueryService queryService;

    @Operation(summary = "Dala sug'orish holati", description = "Huquq: `field:read`. Tuproq (fermer va SoilGrids), oxirgi/keyingi sug'orish, tarix")
    @GetMapping("/fields/{id}/water")
    public IrrigationQueryService.WaterView get(@PathVariable Long id) {
        return queryService.get(id);
    }
}
