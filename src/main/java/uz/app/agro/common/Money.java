package uz.app.agro.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** "1 500 000" — minglik ajratkich bo'sh joy (so'm butun). Bot, support guruhi, chegirma yorlig'i — bitta joy */
public final class Money {

    private Money() {}

    public static String digits(BigDecimal v) {
        String d = v.setScale(0, RoundingMode.HALF_UP).toPlainString();
        boolean negative = d.startsWith("-");
        if (negative) d = d.substring(1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            if (i > 0 && (d.length() - i) % 3 == 0) out.append(' ');
            out.append(d.charAt(i));
        }
        return (negative ? "-" : "") + out;
    }
}
