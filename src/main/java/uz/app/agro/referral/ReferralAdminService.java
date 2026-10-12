package uz.app.agro.referral;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.app.agro.common.PageResponse;
import uz.app.agro.common.exception.NotFoundException;
import uz.app.agro.farmer.FarmerEntity;
import uz.app.agro.farmer.FarmerRepository;
import uz.app.agro.rbac.Permissions;
import uz.app.agro.referral.dto.FarmerReferral;
import uz.app.agro.referral.dto.ReferralStats;
import uz.app.agro.referral.dto.ReferrerRow;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Admin → Takliflar. Agregatlar — native SQL (bitta so'rovda guruhlash; JPQL'da self-join noqulay) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralAdminService {

    private final EntityManager em;
    private final FarmerRepository farmerRepository;
    private final ReferralRewardRepository rewardRepository;

    @PreAuthorize(Permissions.CAN_READ_REFERRAL)
    public ReferralStats stats() {
        Tuple t = (Tuple) em.createNativeQuery("""
                SELECT count(DISTINCT referred_by_id) AS referrers, count(*) AS invited, count(activated_at) AS activated,
                       (SELECT count(*) FROM referral_rewards) AS rewards
                FROM farmers WHERE referred_by_id IS NOT NULL AND deleted = FALSE
                """, Tuple.class).getSingleResult();
        return new ReferralStats(num(t, "referrers"), num(t, "invited"), num(t, "activated"), num(t, "rewards"));
    }

    /** Taklif qilganlar — eng ko'p taklif qilgani tepada. q — ism, username yoki kod */
    @PreAuthorize(Permissions.CAN_READ_REFERRAL)
    public PageResponse<ReferrerRow> referrers(String q, Pageable pageable) {
        String like = q == null || q.isBlank() ? null
                : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String where = """
                FROM farmers r JOIN farmers f ON f.referred_by_id = r.id AND f.deleted = FALSE
                WHERE r.deleted = FALSE AND (CAST(:q AS text) IS NULL OR lower(coalesce(r.full_name, '')) LIKE CAST(:q AS text)
                   OR lower(coalesce(r.telegram_username, '')) LIKE CAST(:q AS text) OR lower(coalesce(r.referral_code, '')) LIKE CAST(:q AS text))
                """;
        @SuppressWarnings("unchecked")
        List<Tuple> rows = em.createNativeQuery("""
                SELECT r.id AS id, r.full_name AS name, r.telegram_username AS username, r.referral_code AS code,
                       count(f.id) AS invited, count(f.activated_at) AS activated,
                       (SELECT count(*) FROM referral_rewards w WHERE w.farmer_id = r.id) AS rewards,
                       max(f.referred_at) AS last_invited
                """ + where + " GROUP BY r.id ORDER BY count(f.id) DESC, r.id LIMIT :lim OFFSET :off", Tuple.class)
                .setParameter("q", like).setParameter("lim", pageable.getPageSize()).setParameter("off", pageable.getOffset())
                .getResultList();
        long total = ((Number) em.createNativeQuery("SELECT count(DISTINCT r.id) " + where)
                .setParameter("q", like).getSingleResult()).longValue();
        List<ReferrerRow> content = rows.stream().map(t -> new ReferrerRow(num(t, "id"), t.get("name", String.class),
                t.get("username", String.class), t.get("code", String.class), num(t, "invited"), num(t, "activated"),
                num(t, "rewards"), time(t, "last_invited"))).toList();
        return PageResponse.of(new PageImpl<>(content, pageable, total));
    }

    @PreAuthorize(Permissions.CAN_READ_REFERRAL)
    public FarmerReferral forFarmer(Long farmerId) {
        FarmerEntity f = farmerRepository.findById(farmerId).orElseThrow(() -> new NotFoundException("Fermer", farmerId));
        FarmerReferral.Person by = f.getReferredById() == null ? null : farmerRepository.findById(f.getReferredById())
                .map(r -> new FarmerReferral.Person(r.getId(), r.getFullName(), r.getTelegramUsername())).orElse(null);
        @SuppressWarnings("unchecked")
        List<Tuple> invited = em.createNativeQuery("""
                SELECT id, full_name AS name, telegram_username AS username, referred_at, activated_at
                FROM farmers WHERE referred_by_id = :id AND deleted = FALSE ORDER BY referred_at DESC LIMIT 500
                """, Tuple.class).setParameter("id", farmerId).getResultList();
        @SuppressWarnings("unchecked")
        List<Tuple> rewards = em.createNativeQuery("""
                SELECT w.subscription_id, p.name AS plan, s.valid_from, s.valid_until, w.referrals_used, w.created_date
                FROM referral_rewards w JOIN farmer_subscriptions s ON s.id = w.subscription_id JOIN plans p ON p.id = s.plan_id
                WHERE w.farmer_id = :id ORDER BY w.id DESC
                """, Tuple.class).setParameter("id", farmerId).getResultList();
        return new FarmerReferral(f.getReferralCode(), by, f.getReferredAt(), f.getActivatedAt(),
                invited.stream().map(t -> new FarmerReferral.Invited(num(t, "id"), t.get("name", String.class),
                        t.get("username", String.class), time(t, "referred_at"), time(t, "activated_at"))).toList(),
                rewards.stream().map(t -> new FarmerReferral.Reward(num(t, "subscription_id"), t.get("plan", String.class),
                        date(t, "valid_from"), date(t, "valid_until"),
                        (int) num(t, "referrals_used"), time(t, "created_date"))).toList());
    }

    private static long num(Tuple t, String alias) {
        Object v = t.get(alias);
        return v == null ? 0 : ((Number) v).longValue();
    }

    // Hibernate 7 native natijada DATE → LocalDate (eski versiyalarda java.sql.Date) — ikkalasi ham
    private static java.time.LocalDate date(Tuple t, String alias) {
        Object v = t.get(alias);
        if (v instanceof java.time.LocalDate d) return d;
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return null;
    }

    private static LocalDateTime time(Tuple t, String alias) {
        Object v = t.get(alias);
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof LocalDateTime l) return l;
        return null;
    }
}
