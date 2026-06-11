package com.zoufx.ai.agent.base.support;

/**
 * JSON 字符串值转义——手写 payload（SSE 事件）拼接时保证单行安全、不出现裸控制字符。
 */
public final class JsonStrings {

    private JsonStrings() {
    }

    /** 转义为 JSON 字符串字面值的内容部分（不含外层引号）。null 视为空串。 */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
