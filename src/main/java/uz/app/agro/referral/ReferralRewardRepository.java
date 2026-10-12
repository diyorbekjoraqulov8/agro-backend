package uz.app.agro.referral;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReferralRewardRepository extends JpaRepository<ReferralRewardEntity, Long> {

    @Query("SELECT coalesce(sum(r.referralsUsed), 0) FROM ReferralRewardEntity r WHERE r.farmerId = :farmerId")
    long usedReferrals(@Param("farmerId") Long farmerId);

    long countByFarmerId(Long farmerId);

    List<ReferralRewardEntity> findByFarmerIdOrderByIdDesc(Long farmerId);
}
