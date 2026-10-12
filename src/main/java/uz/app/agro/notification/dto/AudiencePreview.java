package uz.app.agro.notification.dto;

import java.util.List;
import java.util.Map;

/** Yuborishdan oldin: nechta fermerga boradi, tillar bo'yicha va bir nechta misol */
public record AudiencePreview(long count, Map<String, Long> byLanguage, List<String> sample) {}
