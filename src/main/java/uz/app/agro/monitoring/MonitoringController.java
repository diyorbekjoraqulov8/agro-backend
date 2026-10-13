package uz.app.agro.monitoring;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uz.app.agro.monitoring.dto.FieldMonitoringResponse;

@Tag(name = "Monitoring")
@RestController
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService service;

    @Operation(summary = "Dala monitoringi", description = "Huquq: `field:read`. Navbat holati va so'nggi 20 surat natijasi")
    @GetMapping("/fields/{id}/monitoring")
    public FieldMonitoringResponse forField(@PathVariable Long id) {
        return service.forField(id);
    }
}
