package com.shortlink.common.util;

/**
 * Base62 编解码器。
 *
 * <p>62 进制字符表为 {@code 0-9a-zA-Z}，7 位编码空间约 3.5 万亿，
 * 配合发号器起始值 10 亿使用；encode 固定输出 7 位（不足补 '0'）。</p>
 */
public final class Base62 {

    private static final char[] CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final int RADIX = 62;

    /**
     * 编码后固定长度（62^7 ≈ 3.5 万亿）。
     */
    public static final int CODE_LENGTH = 7;

    private static final int[] CHAR_TO_INDEX = new int[128];

    static {
        for (int i = 0; i < CHAR_TO_INDEX.length; i++) {
            CHAR_TO_INDEX[i] = -1;
        }
        for (int i = 0; i < CHARS.length; i++) {
            CHAR_TO_INDEX[CHARS[i]] = i;
        }
    }

    private Base62() {
    }

    /**
     * 将非负 long 编码为 7 位 Base62 字符串。
     *
     * @param id 发号器产生的全局唯一 ID，必须小于 62^7
     */
    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id 不能为负数: " + id);
        }
        if (id >= 3_521_614_606_208L) {
            throw new IllegalArgumentException("id 超出 7 位 Base62 编码空间: " + id);
        }
        char[] buf = new char[CODE_LENGTH];
        long rest = id;
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            buf[i] = CHARS[(int) (rest % RADIX)];
            rest /= RADIX;
        }
        return new String(buf);
    }

    /**
     * 将 Base62 字符串解码为 long，非法字符直接抛出 {@link IllegalArgumentException}。
     */
    public static long decode(String code) {
        if (code == null || code.length() != CODE_LENGTH) {
            throw new IllegalArgumentException("短码长度必须为 " + CODE_LENGTH + ": " + code);
        }
        long result = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            int idx = c < 128 ? CHAR_TO_INDEX[c] : -1;
            if (idx < 0) {
                throw new IllegalArgumentException("非法 Base62 字符: " + c);
            }
            result = result * RADIX + idx;
        }
        return result;
    }
}
