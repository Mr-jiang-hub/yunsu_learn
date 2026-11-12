package com.yunshu.zhixue.admin.message;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.message.dto.CreateMessageRequest;
import com.yunshu.zhixue.admin.message.dto.MessageItem;
import com.yunshu.zhixue.admin.message.dto.UpdateMessageRequest;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.util.TimeFormats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台消息推送（模块 message）。状态三态：1 草稿 / 2 已发送 / 3 已撤回。
 *
 * <p>与公告（notice）的区别：消息要触达到人、要统计已读、要能撤回，
 * 因此稿件（message）与收件（message_receive）分开存，已读/删除都是收件侧状态。</p>
 *
 * <p>状态机：</p>
 * <ul>
 *   <li>草稿：可编辑、可删除、可发送；</li>
 *   <li>已发送：只能撤回，不能编辑也不能删除（避免改掉学员已收到的内容）；</li>
 *   <li>已撤回：终态，不可重发、不可删除，保留历史。</li>
 * </ul>
 *
 * <p>投递策略：全体消息走"懒投递"——发送只记人数快照，学员首次访问消息中心时
 * 补齐收件行；指定学员消息在发送时直接落收件行。</p>
 */
@Service
public class AdminMessageService {

    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_SENT = 2;
    private static final int STATUS_REVOKED = 3;

    private static final int AUDIENCE_ALL = 1;
    private static final int AUDIENCE_SPECIFIED = 2;

    private static final int TYPE_SYSTEM = 1;
    private static final int TYPE_STUDY = 2;
    private static final int TYPE_ACTIVITY = 3;

    /** 单次定向推送的人数上限：防止误操作一次性写入过多收件行 */
    private static final int MAX_TARGETS = 200;

    private static final String SELECT_BASE =
            "SELECT m.id, m.title, m.content, m.type, m.audience, m.status, m.target_count, m.sent_at, "
                    + "m.created_at, m.updated_at, "
                    + "(SELECT COUNT(*) FROM message_receive r "
                    + "  WHERE r.message_id = m.id AND r.read_at IS NOT NULL AND r.deleted = 0) AS read_count "
                    + "FROM message m ";

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminMessageService(JdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageData<MessageItem> list(int page, int size, String keyword, String status) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND m.title LIKE ?");
            args.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND m.status = ?");
            args.add(parseStatus(status));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message m" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);

        List<MessageItem> items = jdbcTemplate.query(
                SELECT_BASE + where + " ORDER BY m.id DESC LIMIT ? OFFSET ?",
                rowMapper(), pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public MessageItem detail(long id) {
        List<MessageItem> rows = jdbcTemplate.query(SELECT_BASE + "WHERE m.id = ?", rowMapper(), id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public MessageItem create(CreateMessageRequest request) {
        String title = request.title().trim();
        if (title.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "消息标题不能为空");
        }
        int type = parseType(request.type() == null ? "SYSTEM" : request.type());
        int audience = parseAudience(request.audience() == null ? "ALL" : request.audience());
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO message (title, content, type, audience, status, sent_at, target_count, "
                            + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, NULL, 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title);
            statement.setString(2, request.content() == null ? "" : request.content().trim());
            statement.setInt(3, type);
            statement.setInt(4, audience);
            statement.setInt(5, STATUS_DRAFT);
            statement.setLong(6, adminId);
            statement.setLong(7, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long newId = key == null ? 0 : key.longValue();
        auditService.record("message", "create", "message", newId, "新建消息 title=" + title);
        return detail(newId);
    }

    @Transactional
    public MessageItem update(long id, UpdateMessageRequest request) {
        MessageItem current = detail(id);
        if (!"DRAFT".equals(current.status())) {
            throw new BizException(ErrorCode.MESSAGE_STATUS_INVALID, "只有草稿消息可以编辑");
        }
        StringBuilder sql = new StringBuilder("UPDATE message SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());
        int changed = 0;

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "消息标题不能为空");
            }
            sql.append(", title = ?");
            args.add(title);
            changed++;
        }
        if (request.content() != null) {
            sql.append(", content = ?");
            args.add(request.content().trim());
            changed++;
        }
        if (request.type() != null) {
            sql.append(", type = ?");
            args.add(parseType(request.type()));
            changed++;
        }
        String audienceText = request.audience();
        if (audienceText != null) {
            int audience = parseAudience(audienceText);
            if (audience == AUDIENCE_SPECIFIED) {
                // 改成定向必须同时给名单，否则发送时无从投递
                if (request.studentIds() == null || request.studentIds().isEmpty()) {
                    throw new BizException(ErrorCode.MESSAGE_TARGET_INVALID, "定向消息必须指定接收学员");
                }
            }
            sql.append(", audience = ?");
            args.add(audience);
            changed++;
        }

        if (changed > 0) {
            sql.append(" WHERE id = ?");
            args.add(id);
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("message", "update", "message", id,
                    "编辑消息 title=" + current.title() + " 状态=" + current.status());
        }
        // 定向名单不进 message 表：它只在发送瞬间生效，草稿阶段改名单无需落库
        return detail(id);
    }

    /**
     * 发送：仅草稿可发送。
     *
     * <p>全体消息只写人数快照，收件行由学员访问消息中心时补齐（懒投递）；
     * 定向消息在发送瞬间写入收件行，接收人不存在或已注销直接拒绝。</p>
     */
    @Transactional
    public MessageItem send(long id, List<Long> studentIds) {
        MessageItem current = detail(id);
        if (!"DRAFT".equals(current.status())) {
            throw new BizException(ErrorCode.MESSAGE_STATUS_INVALID, "只有草稿消息可以发送");
        }
        int audience = "ALL".equals(current.audience()) ? AUDIENCE_ALL : AUDIENCE_SPECIFIED;
        int targetCount;

        if (audience == AUDIENCE_ALL) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM student WHERE status = 1", Long.class);
            targetCount = count == null ? 0 : count.intValue();
        } else {
            List<Long> targets = resolveTargets(studentIds);
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            jdbcTemplate.batchUpdate(
                    "INSERT IGNORE INTO message_receive (message_id, student_id, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?)",
                    targets, targets.size(), (statement, studentId) -> {
                        statement.setLong(1, id);
                        statement.setLong(2, studentId);
                        statement.setObject(3, now);
                        statement.setObject(4, now);
                    });
            targetCount = targets.size();
        }

        jdbcTemplate.update(
                "UPDATE message SET status = ?, sent_at = ?, target_count = ?, updated_by = ? WHERE id = ?",
                STATUS_SENT, LocalDateTime.now(ZoneOffset.UTC), targetCount,
                CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("message", "send", "message", id,
                "发送消息 title=" + current.title() + " 受众=" + current.audience() + " 人数=" + targetCount);
        return detail(id);
    }

    /** 撤回：已发送 → 已撤回；撤回后 C 端立即不可见，收件行保留用于统计 */
    @Transactional
    public MessageItem revoke(long id) {
        MessageItem current = detail(id);
        if (!"SENT".equals(current.status())) {
            throw new BizException(ErrorCode.MESSAGE_STATUS_INVALID, "只有已发送的消息可以撤回");
        }
        jdbcTemplate.update("UPDATE message SET status = ?, updated_by = ? WHERE id = ?",
                STATUS_REVOKED, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("message", "revoke", "message", id, "撤回消息 title=" + current.title());
        return detail(id);
    }

    /** 删除：仅草稿可删；已发送请先撤回，已撤回保留历史 */
    @Transactional
    public void delete(long id) {
        MessageItem current = detail(id);
        if (!"DRAFT".equals(current.status())) {
            throw new BizException(ErrorCode.MESSAGE_STATUS_INVALID, "只有草稿消息可以删除");
        }
        jdbcTemplate.update("DELETE FROM message WHERE id = ? AND status = ?", id, STATUS_DRAFT);
        auditService.record("message", "delete", "message", id, "删除草稿消息 title=" + current.title());
    }

    // ------------------------------------------------------------------ 内部

    /** 定向名单校验：去重、限流、排除不存在与已注销的学员 */
    private List<Long> resolveTargets(List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new BizException(ErrorCode.MESSAGE_TARGET_INVALID, "定向消息必须指定接收学员");
        }
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(studentIds));
        if (unique.size() > MAX_TARGETS) {
            throw new BizException(ErrorCode.MESSAGE_TARGET_INVALID,
                    "单次定向推送最多 " + MAX_TARGETS + " 人，当前 " + unique.size() + " 人");
        }
        for (Long id : unique) {
            if (id == null || id <= 0) {
                throw new BizException(ErrorCode.MESSAGE_TARGET_INVALID, "接收学员 ID 不合法");
            }
        }
        String placeholders = unique.stream().map(item -> "?").collect(Collectors.joining(","));
        List<Long> valid = jdbcTemplate.queryForList(
                "SELECT id FROM student WHERE id IN (" + placeholders + ") AND status <> 3",
                unique.toArray(), Long.class);
        if (valid.size() != unique.size()) {
            Set<Long> validSet = new LinkedHashSet<>(valid);
            List<Long> invalid = unique.stream().filter(item -> !validSet.contains(item)).limit(5).toList();
            throw new BizException(ErrorCode.MESSAGE_TARGET_INVALID,
                    "以下接收学员不存在或已注销：" + invalid + (unique.size() - valid.size() > 5 ? " 等" : ""));
        }
        return valid;
    }

    private RowMapper<MessageItem> rowMapper() {
        return (rs, rowNum) -> new MessageItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                toType(rs.getInt("type")),
                toAudience(rs.getInt("audience")),
                toStatus(rs.getInt("status")),
                rs.getInt("target_count"),
                rs.getInt("read_count"),
                TimeFormats.rfc3339(rs.getObject("sent_at")),
                TimeFormats.rfc3339(rs.getObject("created_at")),
                TimeFormats.rfc3339(rs.getObject("updated_at")));
    }

    private static String toStatus(int value) {
        return switch (value) {
            case STATUS_SENT -> "SENT";
            case STATUS_REVOKED -> "REVOKED";
            default -> "DRAFT";
        };
    }

    private static int parseStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case "SENT" -> STATUS_SENT;
            case "REVOKED" -> STATUS_REVOKED;
            case "DRAFT" -> STATUS_DRAFT;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "状态取值只能是 DRAFT、SENT 或 REVOKED");
        };
    }

    private static String toType(int value) {
        return switch (value) {
            case TYPE_STUDY -> "STUDY";
            case TYPE_ACTIVITY -> "ACTIVITY";
            default -> "SYSTEM";
        };
    }

    private static int parseType(String type) {
        return switch (type.trim().toUpperCase()) {
            case "STUDY" -> TYPE_STUDY;
            case "ACTIVITY" -> TYPE_ACTIVITY;
            case "SYSTEM" -> TYPE_SYSTEM;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "消息类型只能是 SYSTEM、STUDY 或 ACTIVITY");
        };
    }

    private static String toAudience(int value) {
        return value == AUDIENCE_SPECIFIED ? "SPECIFIED" : "ALL";
    }

    private static int parseAudience(String audience) {
        return switch (audience.trim().toUpperCase()) {
            case "ALL" -> AUDIENCE_ALL;
            case "SPECIFIED" -> AUDIENCE_SPECIFIED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "受众只能是 ALL 或 SPECIFIED");
        };
    }

    private static void validatePaging(int page, int size) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "页码必须大于等于 1");
        }
        if (size < 1 || size > PageQuery.MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "每页条数必须在 1-" + PageQuery.MAX_SIZE + " 之间");
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
