package uz.app.agro.bot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import uz.app.agro.field.Crop;
import uz.app.agro.field.Rings;

import java.util.ArrayList;
import java.util.List;

/**
 * Mini App'dan kelgan web_app_data — ISHONCHSIZ kirish: fermer (yoki boshqa odam) Mini App
 * kodini o'zgartirib istalgan JSON yubora oladi. Bu yerda arzon tekshiruvlar; maydon va
 * o'z-o'zini kesishni Python tekshiradi (422 → fermerga tushunarli xabar, S7).
 */
final class BotPolygonParser {

    static final int MAX_VERTICES = Rings.MAX_VERTICES;
    static final int MAX_BYTES = 4096;               // Telegram sendData chegarasi

    /** fieldId — tahrirlash rejimi (Mini App ?fieldId=); EGALIK FieldIntakeService'da tekshiriladi */
    record Parsed(String name, List<double[]> ring, Long fieldId, Crop crop) {}

    private BotPolygonParser() {}

    /** @return null — yaroqsiz (sababi log'ga) */
    static Parsed parse(String data, String defaultName) {
        if (data == null || data.length() > MAX_BYTES) return null;
        JsonNode root;
        try {
            root = JsonMapper.shared().readTree(data);
        } catch (RuntimeException e) {
            return null;
        }
        if (root == null || !root.isObject() || root.path("v").asInt(0) != 1) return null;
        JsonNode coords = root.path("coords");
        // Yopilmagan uchburchak (3 nuqta) ham yaroqli — "≥ 4" tekshiruvi halqa yopilgandan KEYIN
        if (!coords.isArray() || coords.size() < 3 || coords.size() > MAX_VERTICES + 1) return null;

        List<double[]> ring = new ArrayList<>(coords.size() + 1);
        for (JsonNode p : coords) {
            if (!p.isArray() || p.size() != 2 || !p.get(0).isNumber() || !p.get(1).isNumber()) return null;
            double lon = p.get(0).asDouble(), lat = p.get(1).asDouble();
            if (!Rings.validPoint(lon, lat)) return null;
            ring.add(new double[]{lon, lat});
        }
        if (!Rings.close(ring)) return null;            // halqa yopilmagan bo'lsa — yopiladi

        String name = Rings.name(root.path("name").isString() ? root.path("name").asString() : "", defaultName);
        Long fieldId = null;
        JsonNode fid = root.path("fieldId");
        if (!fid.isMissingNode() && !fid.isNull()) {
            if (!fid.canConvertToLong() || fid.asLong() <= 0) return null;
            fieldId = fid.asLong();
        }
        // Ekin — ixtiyoriy; noma'lum qiymat rad etilmaydi, e'tiborsiz (dala/fermer ekini qoladi)
        Crop crop = null;
        if (root.path("crop").isString()) {
            try {
                crop = Crop.valueOf(root.path("crop").asString());
            } catch (IllegalArgumentException ignored) {
                // soxtalashtirilgan qiymat
            }
        }
        return new Parsed(name, ring, fieldId, crop);
    }
}
