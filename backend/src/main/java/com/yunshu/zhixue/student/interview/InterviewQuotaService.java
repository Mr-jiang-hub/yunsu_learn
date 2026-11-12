package com.yunshu.zhixue.student.interview;

import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.interview.dto.InterviewQuotaView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 面试每日次数（PRD 5.7 / 03 §9.6）：每学员每上海自然日 3 次（可配置），不按学科/难度拆分，未使用不累积。
 *
 * <p>占用与返还都落 MySQL 原子计数（02-Q17：不引入 Redis）：</p>
 * <ul>
 *   <li>占用 = 先补行再条件自增 {@code used_count < 3}，并发点击只有一个成功；</li>
 *   <li>返还 = 递减且不为负，调用方必须用"会话状态条件更新"保证只返还一次。</li>
 * </ul>
 */
@Service
public class InterviewQuotaService {

    /** 默认每日 3 次；运营可通过 interview.quota.daily-limit 调整，不需要改代码 */
    private static final int DEFAULT_DAILY_LIMIT = 3;

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;

    @Value("${interview.quota.daily-limit:3}")
    private int dailyLimit = DEFAULT_DAILY_LIMIT;

    public InterviewQuotaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 当日的次数上限（配置项，非法值回落默认 3） */
    public int dailyLimit() {
        return dailyLimit > 0 ? dailyLimit : DEFAULT_DAILY_LIMIT;
    }

    /** 业务日按 Asia/Shanghai 自然日，不是 UTC 日期 */
    public LocalDate quotaDate() {
        return LocalDate.now(SHANGHAI);
    }

    public InterviewQuotaView view(long studentId) {
        LocalDate date = quotaDate();
        int used = usedCount(studentId, date);
        return new InterviewQuotaView(
                date.toString(),
                dailyLimit(),
                used,
                Math.max(0, dailyLimit() - used),
                resetsAt(date),
                TimeFormats.rfc3339(Instant.now()));
    }

    public int remaining(long studentId) {
        return Math.max(0, dailyLimit() - usedCount(studentId, quotaDate()));
    }

    /** 原子占用一次；返回 false 表示今日已用完（并发下也只有一个请求能拿到） */
    public boolean tryOccupy(long studentId, LocalDate date) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO interview_quota (student_id, quota_date, used_count) VALUES (?, ?, 0)",
                studentId, date);
        return jdbcTemplate.update(
                "UPDATE interview_quota SET used_count = used_count + 1 "
                        + "WHERE student_id = ? AND quota_date = ? AND used_count < ?",
                studentId, date, dailyLimit()) == 1;
    }

    /** 返还一次（仅用于"开场题未生成即作废"）；必须由调用方保证只调用一次 */
    public void refund(long studentId, LocalDate date) {
        jdbcTemplate.update(
                "UPDATE interview_quota SET used_count = CASE WHEN used_count > 0 THEN used_count - 1 ELSE 0 END "
                        + "WHERE student_id = ? AND quota_date = ?",
                studentId, date);
    }

    private int usedCount(long studentId, LocalDate date) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT used_count FROM interview_quota WHERE student_id = ? AND quota_date = ?",
                studentId, date);
        if (rows.isEmpty()) {
            return 0;
        }
        Object value = rows.get(0).get("used_count");
        return value instanceof Number num ? num.intValue() : 0;
    }

    /** 上海次日 00:00 对应的 UTC 时间 */
    public static String resetsAt(LocalDate date) {
        return TimeFormats.rfc3339(date.plusDays(1).atStartOfDay(SHANGHAI).toInstant());
    }
}
