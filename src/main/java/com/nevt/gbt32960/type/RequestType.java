package com.nevt.gbt32960.type;

import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
@Getter
public enum RequestType {
    // 车辆登入
    LOGIN((byte) 0x01),
    // 实时信息上报
    REAL_TIME((byte) 0x02),
    // 补发信息上报
    REISSUE((byte) 0x03),
    // 车辆登出
    LOGOUT((byte) 0x04),

    PLATFORM_LOGIN((byte)0x05),

    PLATFORM_LOGOUT((byte)0x06),

    PLATFORM_LOGIN_RESPONSE((byte)0xC0),
    PLATFORM_LOGOUT_RESPONSE((byte)0xC1);

    private final byte value;

    public static RequestType valueOf(byte type) {
        for (RequestType t : values()) {
            if (t.value == type) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown message type : " + type);
    }

}
