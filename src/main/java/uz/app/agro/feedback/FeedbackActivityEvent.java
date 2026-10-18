package uz.app.agro.feedback;

/**
 * Murojaatda nimadir bo'ldi — tranzaksiya ichida e'lon qilinadi, tinglovchilar AFTER_COMMIT (support guruhi ko'zgusi).
 * messageId — FARMER_MESSAGE / STAFF_MESSAGE da saqlangan xabar; opened — fermer yangi suhbat ochdi (birinchi xabar)
 */
public record FeedbackActivityEvent(long feedbackId, Long messageId, Type type, boolean opened) {

    public enum Type { FARMER_MESSAGE, STAFF_MESSAGE, CLOSED }

    static FeedbackActivityEvent farmer(long feedbackId, long messageId, boolean opened) {
        return new FeedbackActivityEvent(feedbackId, messageId, Type.FARMER_MESSAGE, opened);
    }

    static FeedbackActivityEvent staff(long feedbackId, long messageId) {
        return new FeedbackActivityEvent(feedbackId, messageId, Type.STAFF_MESSAGE, false);
    }

    static FeedbackActivityEvent closed(long feedbackId) {
        return new FeedbackActivityEvent(feedbackId, null, Type.CLOSED, false);
    }
}
