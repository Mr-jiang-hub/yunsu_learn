package com.yunshu.zhixue.common.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 数据库 DATETIME(3) 存 UTC 朴素时间，对外统一 RFC 3339 UTC 字符串（api-rules §5.4）。
 */
public final class TimeFormats {

    private static final DateTimeFormatter RFC3339_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private TimeFormats() {
    }

    public static String rfc3339(Object value) {
        if (value instanceof Instant instant) {
            return RFC3339_UTC.format(instant);
        }
        if (value instanceof LocalDateTime dateTime) {
            return RFC3339_UTC.format(dateTime.toInstant(ZoneOffset.UTC));
        }
        if (value instanceof Timestamp timestamp) {
            return RFC3339_UTC.format(timestamp.toInstant());
        }
        if (value instanceof java.util.Date date) {
            return RFC3339_UTC.format(date.toInstant());
        }
        return null;
    }
}
