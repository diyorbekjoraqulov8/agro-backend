package uz.app.agro.farmer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FarmerRepository extends JpaRepository<FarmerEntity, Long>,
                                          JpaSpecificationExecutor<FarmerEntity> {

    Optional<FarmerEntity> findByTelegramUserId(Long telegramUserId);

    Optional<FarmerEntity> findByReferralCode(String referralCode);

    /** Support guruhidagi mavzu → fermer (guruh ham — boshqa guruhning shu raqamli mavzusi boshqa fermer) */
    Optional<FarmerEntity> findBySupportChatIdAndSupportTopicId(Long supportChatId, Long supportTopicId);

    /** Mukofot hisobi — bir fermer uchun ketma-ket (ikki do'st bir vaqtda faollashsa ham bitta mukofot) */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT f FROM FarmerEntity f WHERE f.id = :id")
    Optional<FarmerEntity> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    long countByReferredById(Long referredById);

    long countByReferredByIdAndActivatedAtIsNotNull(Long referredById);

    Optional<FarmerEntity> findByChatId(Long chatId);

    Optional<FarmerEntity> findByLabTrue();
}
