package uz.app.agro.farmer;

// Telegram'dan kelgan foydalanuvchi ma'lumoti — bot paketi pengrad tiplarini shu yerga o'giradi
public record TelegramProfile(
        long telegramUserId,
        long chatId,
        String username,
        String firstName,
        String lastName,
        String languageCode
) {}
