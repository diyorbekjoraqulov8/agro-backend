package uz.app.agro.notification;

/**
 * Shablon nomlari — producer (tahlil, sug'orish, murojaat) va kanal renderer'lari o'rtasidagi kelishuv.
 * Har kanal (Telegram, keyin mobil/web) shu nomlarni o'zicha chiqaradi; params kalitlari izohda.
 */
public final class Templates {

    /** {text, buttons?: [{text, url}]} */
    public static final String TEXT = Notification.TEXT;
    /** {jobId} */
    public static final String ANALYSIS_COMPLETED = "analysis.completed";
    /** {jobId, code} — poligon yaroqsiz / surat yo'q */
    public static final String ANALYSIS_REJECTED = "analysis.rejected";
    /** {jobId} — urinishlar tugadi */
    public static final String ANALYSIS_FAILED = "analysis.failed";
    /** {status: WaterStatus, kind: DUE|SOON|STATUS} */
    public static final String IRRIGATION_REMINDER = "irrigation.reminder";
    /** {feedbackId, text?, fileHashId?} */
    public static final String FEEDBACK_REPLY = "feedback.reply";
    /** {feedbackId, askRating} */
    public static final String FEEDBACK_CLOSED = "feedback.closed";

    private Templates() {}
}
