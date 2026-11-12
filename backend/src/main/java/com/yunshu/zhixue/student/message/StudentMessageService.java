package com.yunshu.zhixue.student.message;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.util.TimeFormats;
import com.yunshu.zhixue.student.message.dto.MessageItem;
import com.yunshu.zhixue.student.message.dto.UnreadCountResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * C 端消息中心。
 *
 * <p>全体消息走"懒投递"：发送时后台只记人数快照，学员首次访问消息中心
 * （或查未读数）时把"已发送、且发送时间不早于自己注册时间"的消息补成收件行。
 * 因此注册之前发的消息不会推给新学员，也不必在发送瞬间向全表插入百万行。</p>
 *
 * <p>撤回不是删除收件行，而是稿件改状态（status=3），查询时 join 过滤，
 * 已撤回的消息在 C 端立即消失，后台仍能统计"发出去了多少"。</p>
 *
 * <p>删除是收件侧软删（deleted=1），不影响他人，也不影响稿件。</p>
 */
@Service
public class StudentMessageService {

    private static final int STATUS_SENT = 2;
    private static final int AUDIENCE_ALL = 1;

    /** 懒投递：补齐当前学员缺失的全体消息收件行 */
    private static final String DELIVER_SQL =
            "INSERT INTO message_receive (message_id, student_id, created_at, updated_at) "
                    + "SELECT m.id, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) "
                    + "FROM message m JOIN student s ON s.id = ? "
                    + "WHERE m.status = ? AND m.audience = ? AND m.sent_at >= s.created_at "
                    + "AND NOT EXISTS (SELECT 1 FROM message_receive r "
                    + "                 WHERE r.message_id = m.id AND r.student_id = s.id)";

    private static final String VISIBLE_WHERE =
            "FROM message_receive r JOIN message m ON m.id = r.message_id "
                    + "WHERE r.student_id = ? AND r.deleted = 0 AND m.status = ? ";

    private final JdbcTemplate jdbcTemplate;

    public StudentMessageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 消息列表：未读在前、发送时间倒序 */
    @Transactional
    public PageData<MessageItem> list(long studentId, PageQuery pageQuery) {
        deliver(studentId);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + VISIBLE_WHERE, Long.class, studentId, STATUS_SENT);
        List<MessageItem> items = jdbcTemplate.query(
                "SELECT m.id, m.title, m.content, m.type, m.sent_at, r.read_at " + VISIBLE_WHERE
                        + "ORDER BY r.read_at IS NULL DESC, m.sent_at DESC, m.id DESC LIMIT ? OFFSET ?",
                rowMapper(), studentId, STATUS_SENT, pageQuery.getSize(), pageQuery.offset());
        return PageData.of(items, pageQuery.getPage(), pageQuery.getSize(), total == null ? 0 : total);
    }

    @Transactional
    public UnreadCountResponse unreadCount(long studentId) {
        deliver(studentId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + VISIBLE_WHERE + "AND r.read_at IS NULL",
                Long.class, studentId, STATUS_SENT);
        return new UnreadCountResponse(count == null ? 0 : count.intValue());
    }

    /** 标记单条已读；重复调用幂等。消息不存在（含已撤回/未投递给自己）→ 40039 */
    @Transactional
    public MessageItem read(long studentId, long messageId) {
        deliver(studentId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int updated = jdbcTemplate.update(
                "UPDATE message_receive r JOIN message m ON m.id = r.message_id "
                        + "SET r.read_at = COALESCE(r.read_at, ?), r.updated_at = ? "
                        + "WHERE r.student_id = ? AND r.message_id = ? AND r.deleted = 0 AND m.status = ?",
                now, now, studentId, messageId, STATUS_SENT);
        if (updated == 0) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        List<MessageItem> rows = jdbcTemplate.query(
                "SELECT m.id, m.title, m.content, m.type, m.sent_at, r.read_at " + VISIBLE_WHERE
                        + "AND m.id = ?", rowMapper(), studentId, STATUS_SENT, messageId);
        return rows.get(0);
    }

    /** 全部已读：只更新未读行，返回受影响条数 */
    @Transactional
    public int readAll(long studentId) {
        deliver(studentId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return jdbcTemplate.update(
                "UPDATE message_receive r JOIN message m ON m.id = r.message_id "
                        + "SET r.read_at = ?, r.updated_at = ? "
                        + "WHERE r.student_id = ? AND r.deleted = 0 AND m.status = ? AND r.read_at IS NULL",
                now, now, studentId, STATUS_SENT);
    }

    /** 删除（软删）：只影响自己的收件行 */
    @Transactional
    public void delete(long studentId, long messageId) {
        deliver(studentId);
        int updated = jdbcTemplate.update(
                "UPDATE message_receive r JOIN message m ON m.id = r.message_id "
                        + "SET r.deleted = 1, r.updated_at = ? "
                        + "WHERE r.student_id = ? AND r.message_id = ? AND r.deleted = 0 AND m.status = ?",
                LocalDateTime.now(ZoneOffset.UTC), studentId, messageId, STATUS_SENT);
        if (updated == 0) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------ 内部

    private void deliver(long studentId) {
        jdbcTemplate.update(DELIVER_SQL, studentId, studentId, STATUS_SENT, AUDIENCE_ALL);
    }

    private RowMapper<MessageItem> rowMapper() {
        return (rs, rowNum) -> new MessageItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                toType(rs.getInt("type")),
                TimeFormats.rfc3339(rs.getObject("sent_at")),
                TimeFormats.rfc3339(rs.getObject("read_at")));
    }

    private static String toType(int value) {
        return switch (value) {
            case 2 -> "STUDY";
            case 3 -> "ACTIVITY";
            default -> "SYSTEM";
        };
    }
}
