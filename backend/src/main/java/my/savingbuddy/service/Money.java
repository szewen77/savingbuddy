package my.savingbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** BigDecimal helpers with the app's money conventions (2dp, never negative where it would mislead). */
public final class Money {
    private Money() {}

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public static BigDecimal of(long v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal scale(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal floorZero(BigDecimal v) { return scale(v.max(BigDecimal.ZERO)); }

    public static BigDecimal divide(BigDecimal v, int by) {
        if (by <= 0) return scale(v);
        return v.divide(BigDecimal.valueOf(by), 2, RoundingMode.DOWN);
    }

    public static double ratio(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) return 0;
        return part.divide(whole, 4, RoundingMode.HALF_UP).doubleValue();
    }
}
