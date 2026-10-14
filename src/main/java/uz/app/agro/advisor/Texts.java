package uz.app.agro.advisor;

/** Matnni ustun / Telegram chegarasigacha kesish — emoji (surrogate juftlik) o'rtasidan bo'linmaydi */
public final class Texts {

    private Texts() {}

    public static String cut(String s, int max) {
        if (s == null || s.length() <= max) return s;
        int end = max - 1;
        if (Character.isHighSurrogate(s.charAt(end - 1))) end--;
        return s.substring(0, end) + "…";
    }
}
