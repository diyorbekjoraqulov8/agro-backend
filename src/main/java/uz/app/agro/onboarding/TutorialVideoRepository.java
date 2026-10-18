package uz.app.agro.onboarding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TutorialVideoRepository extends JpaRepository<TutorialVideoEntity, String> {

    /** Faqat o'sha fayl hali turgan bo'lsa (shu orada admin almashtirgan bo'lsa — eski id yozilmasin) */
    @Modifying
    @Query("UPDATE TutorialVideoEntity v SET v.telegramFileId = :fileId WHERE v.language = :lang AND v.fileHashId = :hashId")
    int rememberTelegramId(@Param("lang") String lang, @Param("hashId") String hashId, @Param("fileId") String fileId);

    List<TutorialVideoEntity> findByFileHashIdIn(Collection<String> hashIds);
}
