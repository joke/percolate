package examples.builders;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

@Mapper
public interface PreferenceMapper {

    // Coupon offers BOTH an all-args constructor and a fluent builder, so both assembly gates match
    // and percolate.construction.preference decides which one is emitted.
    @Map(target = "code", source = "dto.code")
    Coupon toCoupon(CouponDto dto);

    // Voucher offers only a constructor, so it assembles that way whatever the preference says --
    // the preference is a preference, never an exclusion.
    @Map(target = "code", source = "dto.code")
    Voucher toVoucher(CouponDto dto);
}

final class Coupon {

    private final String code;

    Coupon(String code) {
        this.code = code;
    }

    static CouponBuilder builder() {
        return new CouponBuilder();
    }

    public String getCode() {
        return code;
    }
}

final class CouponBuilder {

    private String code = "";

    CouponBuilder code(String value) {
        this.code = value;
        return this;
    }

    Coupon build() {
        return new Coupon(code);
    }
}

final class Voucher {

    private final String code;

    Voucher(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

final class CouponDto {

    private final String code;

    CouponDto(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
