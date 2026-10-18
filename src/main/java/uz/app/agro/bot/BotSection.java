package uz.app.agro.bot;

import uz.app.agro.settings.Setting;

/**
 * Asosiy menyu bo'limlari — tartib klaviaturadagi tartib: NEW_FIELD yolg'iz, eng tepada (asosiy harakat), qolgani
 * ikkitadan qator. flag — admin yoqib-o'chiradigan sozlama; null — doim bor (⚙️ Sozlamalar: tarif, til, yordam,
 * qo'llanma, do'stlarni taklif — hammasi shu ichida).
 */
enum BotSection {
    NEW_FIELD("menu.newField", Setting.MENU_NEW_FIELD),
    MY_FIELDS("menu.myFields", Setting.MENU_MY_FIELDS),
    IRRIGATION("menu.irrigation", Setting.MENU_IRRIGATION),
    WEATHER("menu.weather", Setting.MENU_WEATHER),
    ADVISOR("menu.advisor", Setting.MENU_ADVISOR),
    SETTINGS("menu.settings", null);

    final String label;
    final Setting flag;

    BotSection(String label, Setting flag) {
        this.label = label;
        this.flag = flag;
    }
}
