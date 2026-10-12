package uz.app.agro.file;

import uz.app.agro.file.dto.FileUsage;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Fayl qayerda ishlatilgan — fayldan foydalanuvchi har bir feature (murojaatlar, keyin hisobotlar...) o'zi aytadi.
 * file paketi ularni bilmaydi (bog'liqlik bir tomonlama: feature → file).
 */
public interface FileUsageProvider {

    /** hashId → foydalanish joylari (faqat topilganlari) */
    Map<String, List<FileUsage>> usages(Collection<String> hashIds);
}
