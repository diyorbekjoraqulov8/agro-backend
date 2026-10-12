package uz.app.agro.settings;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import uz.app.agro.settings.dto.SettingResponse;
import uz.app.agro.settings.dto.SettingUpdateRequest;

import java.util.List;

@Tag(name = "Sozlamalar", description = "Xizmatlar sozlamalari: yoqish/o'chirish, limitlar, chegaralar — deploysiz")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @Operation(summary = "Barcha sozlamalar", description = "Huquq: `settings:read`. Bo'limlar bo'yicha (group)")
    @GetMapping
    public List<SettingResponse> list() {
        return settingsService.list();
    }

    @Operation(summary = "O'zgartirish", description = "Huquq: `settings:manage`. Tur va chegara tekshiriladi. Audit: `SETTING_CHANGED`")
    @PutMapping("/{key}")
    public SettingResponse update(@PathVariable String key, @Valid @RequestBody SettingUpdateRequest body) {
        return settingsService.update(key, body.value());
    }

    @Operation(summary = "Default'ga qaytarish", description = "Huquq: `settings:manage`")
    @DeleteMapping("/{key}")
    public SettingResponse reset(@PathVariable String key) {
        return settingsService.reset(key);
    }
}
