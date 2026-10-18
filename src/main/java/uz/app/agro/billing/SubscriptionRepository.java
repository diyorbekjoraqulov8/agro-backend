package uz.app.agro.billing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long>,
                                                JpaSpecificationExecutor<SubscriptionEntity> {

    @EntityGraph(attributePaths = "plan")
    List<SubscriptionEntity> findByFarmerIdOrderByValidFromDescIdDesc(Long farmerId);

    /** Shu kuni amal qilayotganlar; bir nechta bo'lsa — eng kech tugaydigani birinchi */
    @Query("""
            SELECT s FROM SubscriptionEntity s JOIN FETCH s.plan
             WHERE s.farmer.id = :farmerId AND s.status = uz.app.agro.billing.SubscriptionEntity.Status.ACTIVE
               AND s.validFrom <= :day AND s.validUntil >= :day
             ORDER BY s.validUntil DESC, s.id DESC
            """)
    List<SubscriptionEntity> findActiveOn(@Param("farmerId") Long farmerId, @Param("day") LocalDate day);

    boolean existsByFarmerIdAndPaymentMethod(Long farmerId, PaymentMethod paymentMethod);

    /** Chegirma auditoriyasi: haqiqiy to'lovi bo'lganmi (bekor qilingan — hisoblanmaydi) */
    boolean existsByFarmerIdAndStatusAndPaymentMethodIn(Long farmerId, SubscriptionEntity.Status status,
                                                       Collection<PaymentMethod> methods);

    interface DiscountUse {
        Long getDiscountId();
        long getUsed();
    }

    /** Chegirmalar ro'yxati — bitta so'rov (har qatorga alohida count emas) */
    @Query("""
            SELECT s.discountId AS discountId, count(s) AS used FROM SubscriptionEntity s
             WHERE s.discountId IS NOT NULL AND s.status = uz.app.agro.billing.SubscriptionEntity.Status.ACTIVE
             GROUP BY s.discountId
            """)
    List<DiscountUse> discountUses();

    /** Pro sinovi eslatmalari: shu oraliqda tugaydigan (yoki tugagan) faol sinovlar (tarif nomi xabarda — fetch) */
    @Query("""
            SELECT s FROM SubscriptionEntity s JOIN FETCH s.plan
             WHERE s.paymentMethod = uz.app.agro.billing.PaymentMethod.TRIAL
               AND s.status = uz.app.agro.billing.SubscriptionEntity.Status.ACTIVE
               AND s.validUntil BETWEEN :from AND :to
            """)
    List<SubscriptionEntity> findTrialsEnding(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Yangi to'lov muddati eskisining oxiridan davom etishi uchun */
    @Query("""
            SELECT max(s.validUntil) FROM SubscriptionEntity s
             WHERE s.farmer.id = :farmerId AND s.status = uz.app.agro.billing.SubscriptionEntity.Status.ACTIVE
               AND s.validUntil >= :day
            """)
    LocalDate lastActiveUntil(@Param("farmerId") Long farmerId, @Param("day") LocalDate day);
}
