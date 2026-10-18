package uz.app.agro.feedback;

/** SUBSCRIPTION — tarif so'rovi / Agro Biznes arizasi (bot o'zi ochadi; "Fikr va yordam" turlari ro'yxatida yo'q) */
public enum FeedbackKind {
    PROBLEM, SUGGESTION, QUESTION, SUBSCRIPTION;

    /** Fermer "💬 Fikr va yordam" da o'zi tanlay oladigan turlar */
    public boolean farmerSelectable() {
        return this != SUBSCRIPTION;
    }
}
