package com.yunshu.zhixue.admin.notice;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.notice.dto.CreateNoticeRequest;
import com.yunshu.zhixue.admin.notice.dto.NoticeItem;
import com.yunshu.zhixue.admin.notice.dto.UpdateNoticeRequest;
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
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 公告管理（模块 notice）。状态三态：1 草稿 / 2 已发布 / 3 已下线。
 *
 * <p>状态机刻意收窄，避免运营在列表里做出语义不明的操作：</p>
 * <ul>
 *   <li>草稿：可编辑、可删除、可发布；</li>
 *   <li>已发布：可编辑（C 端 60s 缓存内生效）、可下线，不可删除；</li>
 *   <li>已下线：可编辑、可再次发布，不可删除（保留发布历史）。</li>
 * </ul>
 * <p>删除只允许草稿，因此不存在"删了线上内容"的误操作路径。</p>
 */
@Service
public class AdminNoticeService {

    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_PUBLISHED = 2;
    private static final int STATUS_OFFLINE = 3;

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String OFFLINE = "OFFLINE";

    private static final String SELECT_BASE =
            "SELECT id, title, content, status, pinned, sort_no, published_at, expire_at, "
                    + "created_at, updated_at FROM notice ";

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminNoticeService(JdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageData<NoticeItem> list(int page, int size, String keyword, String status) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND title LIKE ?");
            args.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(parseStatus(status));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notice" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);

        List<NoticeItem> items = jdbcTemplate.query(
                SELECT_BASE + where + " ORDER BY pinned DESC, sort_no ASC, id DESC LIMIT ? OFFSET ?",
                rowMapper(), pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public NoticeItem detail(long id) {
        List<NoticeItem> rows = jdbcTemplate.query(SELECT_BASE + "WHERE id = ?", rowMapper(), id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOTICE_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public NoticeItem create(CreateNoticeRequest request) {
        String title = request.title().trim();
        if (title.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "公告标题不能为空");
        }
        LocalDateTime expireAt = parseExpireAt(request.expireAt());
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO notice (title, content, status, pinned, sort_no, published_at, expire_at, "
                            + "created_by, updated_by) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, title);
            statement.setString(2, request.content() == null ? "" : request.content().trim());
            statement.setInt(3, STATUS_DRAFT);
            statement.setBoolean(4, Boolean.TRUE.equals(request.pinned()));
            statement.setInt(5, request.sortNo() == null ? 0 : request.sortNo());
            if (expireAt == null) {
                statement.setNull(6, Types.TIMESTAMP);
            } else {
                statement.setObject(6, expireAt);
            }
            statement.setLong(7, adminId);
            statement.setLong(8, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long newId = key == null ? 0 : key.longValue();
        auditService.record("notice", "create", "notice", newId, "新建公告 title=" + title);
        return detail(newId);
    }

    @Transactional
    public NoticeItem update(long id, UpdateNoticeRequest request) {
        NoticeItem current = detail(id);
        StringBuilder sql = new StringBuilder("UPDATE notice SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());
        int changed = 0;

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "公告标题不能为空");
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
        if (request.pinned() != null) {
            sql.append(", pinned = ?");
            args.add(request.pinned());
            changed++;
        }
        if (request.sortNo() != null) {
            sql.append(", sort_no = ?");
            args.add(request.sortNo());
            changed++;
        }
        if (request.expireAt() != null) {
            sql.append(", expire_at = ?");
            args.add(parseExpireAt(request.expireAt()));
            changed++;
        } else if (Boolean.TRUE.equals(request.clearExpire())) {
            sql.append(", expire_at = NULL");
            changed++;
        }

        if (changed > 0) {
            sql.append(" WHERE id = ?");
            args.add(id);
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("notice", "update", "notice", id,
                    "编辑公告 title=" + current.title() + " 状态=" + current.status());
        }
        return detail(id);
    }

    /** 发布：草稿/已下线 → 已发布；published_at 只在首次发布时写入，保留原始时间 */
    @Transactional
    public NoticeItem publish(long id) {
        NoticeItem current = detail(id);
        if (PUBLISHED.equals(current.status())) {
            throw new BizException(ErrorCode.NOTICE_STATUS_INVALID, "公告已是发布状态");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "UPDATE notice SET status = ?, published_at = COALESCE(published_at, ?), updated_by = ? WHERE id = ?",
                STATUS_PUBLISHED, now, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("notice", "publish", "notice", id, "发布公告 title=" + current.title());
        return detail(id);
    }

    @Transactional
    public NoticeItem offline(long id) {
        NoticeItem current = detail(id);
        if (!PUBLISHED.equals(current.status())) {
            throw new BizException(ErrorCode.NOTICE_STATUS_INVALID, "只有已发布的公告可以下线");
        }
        jdbcTemplate.update("UPDATE notice SET status = ?, updated_by = ? WHERE id = ?",
                STATUS_OFFLINE, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("notice", "offline", "notice", id, "下线公告 title=" + current.title());
        return detail(id);
    }

    /** 删除：仅草稿可删；已发布请先下线，已下线保留历史 */
    @Transactional
    public void delete(long id) {
        NoticeItem current = detail(id);
        if (!DRAFT.equals(current.status())) {
            throw new BizException(ErrorCode.NOTICE_STATUS_INVALID, "只有草稿公告可以删除");
        }
        jdbcTemplate.update("DELETE FROM notice WHERE id = ? AND status = ?", id, STATUS_DRAFT);
        auditService.record("notice", "delete", "notice", id, "删除草稿公告 title=" + current.title());
    }

    // ------------------------------------------------------------------ 内部

    private RowMapper<NoticeItem> rowMapper() {
        return (rs, rowNum) -> new NoticeItem(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("content"),
                toStatus(rs.getInt("status")),
                rs.getBoolean("pinned"),
                rs.getInt("sort_no"),
                TimeFormats.rfc3339(rs.getObject("published_at")),
                TimeFormats.rfc3339(rs.getObject("expire_at")),
                TimeFormats.rfc3339(rs.getObject("created_at")),
                TimeFormats.rfc3339(rs.getObject("updated_at")));
    }

    private static String toStatus(int value) {
        return switch (value) {
            case STATUS_PUBLISHED -> PUBLISHED;
            case STATUS_OFFLINE -> OFFLINE;
            default -> DRAFT;
        };
    }

    private static int parseStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case DRAFT -> STATUS_DRAFT;
            case PUBLISHED -> STATUS_PUBLISHED;
            case OFFLINE -> STATUS_OFFLINE;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "状态取值只能是 DRAFT、PUBLISHED 或 OFFLINE");
        };
    }

    /** 接受带时区的 RFC 3339；转成 UTC 朴素时间落库（DATETIME(3) 存 UTC） */
    private static LocalDateTime parseExpireAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        try {
            return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeParseException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "失效时间格式不正确，请使用 RFC 3339，如 2026-12-31T16:00:00Z");
            }
        }
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
