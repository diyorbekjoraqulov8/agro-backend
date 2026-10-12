package uz.app.agro.notification;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.app.agro.notification.NotificationChannel.Delivery;
import uz.app.agro.settings.Setting;
import uz.app.agro.settings.SettingsService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Outbox'dan kanallarga: har soniyada ≤ N ta (Sozlamalar → notification.rate-per-second). fixedDelay — oldingi
 * paket tugamaguncha keyingisi boshlanmaydi. Bir nechta instans xavfsiz (SKIP LOCKED). Kanali yo'q xabar
 * (masalan bot o'chiq) navbatda kutadi — kanal paydo bo'lganda yuboriladi.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.dispatcher.enabled", havingValue = "true", matchIfMissing = true)
class NotificationDispatcher {

    private final NotificationStore store;
    private final SettingsService settings;
    private final Map<ChannelType, NotificationChannel> channels = new EnumMap<>(ChannelType.class);
    private long ticks;

    NotificationDispatcher(NotificationStore store, SettingsService settings, List<NotificationChannel> channels) {
        this.store = store;
        this.settings = settings;
        channels.forEach(c -> this.channels.put(c.type(), c));
    }

    @Scheduled(fixedDelayString = "${notification.dispatcher.interval:1s}", initialDelayString = "${notification.dispatcher.initial-delay:10s}")
    public void tick() {
        if (channels.isEmpty()) return;
        try {
            if (ticks++ % 60 == 0) {                       // daqiqada bir — SENDING'da qotganlarni qaytarish
                int n = store.requeueStuck();
                if (n > 0) log.warn("SENDING'da qotgan {} ta bildirishnoma navbatga qaytarildi", n);
            }
            for (NotificationStore.Job job : store.claim(channels.keySet(), settings.getInt(Setting.NOTIFICATION_RATE))) {
                deliver(job);
            }
        } catch (RuntimeException e) {
            // DB uzilsa ham scheduler to'xtamasin — keyingi tick qayta urinadi
            log.error("bildirishnoma dispatcher xatosi", e);
        }
    }

    private void deliver(NotificationStore.Job job) {
        try {
            MDC.put("requestId", "ntf-" + job.id());
            if (job.to() == null) { store.skip(job.id(), "fermer topilmadi"); return; }
            if (job.to().blocked()) { store.skip(job.id(), "fermer botni bloklagan"); return; }
            Delivery d;
            try {
                d = channels.get(job.channel()).deliver(job.to(), job.template(), job.params());
            } catch (RuntimeException e) {
                // Renderer xatosi (bug) — cheksiz takrorlanmasin: urinishlar soni bilan cheklangan
                log.warn("bildirishnoma {} ({}): {}", job.id(), job.template(), e.toString());
                d = Delivery.retry(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            store.complete(job.id(), d);
        } finally {
            MDC.remove("requestId");
        }
    }
}
