package uz.app.agro.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditWriter writer;

    public Entry event(AuditEvent eventType) {
        return new Entry(writer, eventType);
    }

    public static final class Entry {

        private final AuditWriter writer;
        private final AuditLog log;

        private Entry(AuditWriter writer, AuditEvent eventType) {
            this.writer = writer;
            this.log = AuditLog.event(eventType, AuditLog.Outcome.SUCCESS);
        }

        public Entry failure() {
            log.setOutcome(AuditLog.Outcome.FAILURE);
            return this;
        }

        public Entry actor(Long id, String email) {
            log.setActorId(id);
            log.setActorEmail(email);
            return this;
        }

        public Entry actorEmail(String email) {
            log.setActorEmail(email);
            return this;
        }

        public Entry target(String type, Object id) {
            log.setTargetType(type);
            log.setTargetId(id == null ? null : id.toString());
            return this;
        }

        public Entry detail(String key, Object value) {
            if (log.getDetails() == null) log.setDetails(new HashMap<>());
            log.getDetails().put(key, value);
            return this;
        }

        public void record() {
            writer.save(log);
        }

        public void recordIndependently() {
            writer.saveIndependently(log);
        }
    }
}