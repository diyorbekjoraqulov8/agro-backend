package uz.app.agro.billing;

// V12/V21/V36 ck_subscriptions_method bilan bir xil bo'lishi SHART. Tizim beradi (summa 0, xodim yozmaydi):
// REFERRAL — taklif mukofoti, TRIAL — yangi fermerga Pro sinovi
public enum PaymentMethod {
    CASH, CARD, TRANSFER, OTHER, REFERRAL, TRIAL;

    public boolean systemOnly() {
        return this == REFERRAL || this == TRIAL;
    }
}
