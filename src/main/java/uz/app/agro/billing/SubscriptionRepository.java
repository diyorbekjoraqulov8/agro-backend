package uz.app.agro.billing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /** Yangi to'lov muddati eskisining oxiridan davom etishi uchun */
    @Query("""
            SELECT max(s.validUntil) FROM SubscriptionEntity s
             WHERE s.farmer.id = :farmerId AND s.status = uz.app.agro.billing.SubscriptionEntity.Status.ACTIVE
               AND s.validUntil >= :day
            """)
    LocalDate lastActiveUntil(@Param("farmerId") Long farmerId, @Param("day") LocalDate day);
}
