package uz.app.agro.file.dto;

/**
 * @param kind  FEEDBACK — murojaat suhbatidagi xabar
 * @param refId bog'langan obyekt (murojaat id)
 * @param label odam o'qiydigan nom: "Murojaat №12 · Aziz Karimov"
 */
public record FileUsage(String kind, Long refId, String label) {}
