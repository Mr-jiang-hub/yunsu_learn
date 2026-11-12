package com.yunshu.zhixue.admin.course;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.common.storage.CoverUrlResolver;
import com.yunshu.zhixue.admin.common.dto.SortOrderRequest;
import com.yunshu.zhixue.admin.course.dto.CourseItem;
import com.yunshu.zhixue.admin.course.dto.CreateCourseRequest;
import com.yunshu.zhixue.admin.course.dto.UpdateCourseRequest;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 课程管理（04 §6.10.3 / PRD 6.3）。课程必须挂在同归属学科的阶段下，
 * 停用不物理删除，已开始学员仍可完成。
 */
@Service
public class AdminCourseService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 2;
    private static final Set<String> ALLOWED_HOSTS = Set.of("bilibili.com", "www.bilibili.com", "b23.tv");

    private static final String SELECT_FROM =
            " FROM course c "
                    + "JOIN stage st ON st.id = c.stage_id "
                    + "JOIN learning_path p ON p.id = st.path_id "
                    + "JOIN subject s ON s.id = p.subject_id ";

    /** 列表不返回 description（长文本） */
    private static final String SELECT_LIST =
            "SELECT c.id, c.title, c.bilibili_url, c.cover_url, c.duration_seconds, c.sort_no, c.status, "
                    + "c.created_at, c.updated_at, c.stage_id, st.name AS stage_name, "
                    + "p.subject_id, s.name AS subject_name" + SELECT_FROM;

    private static final String SELECT_DETAIL =
            "SELECT c.id, c.title, c.bilibili_url, c.cover_url, c.duration_seconds, c.sort_no, c.status, "
                    + "c.description, c.created_at, c.updated_at, c.stage_id, st.name AS stage_name, "
                    + "p.subject_id, s.name AS subject_name" + SELECT_FROM;

    private final JdbcTemplate jdbcTemplate;
    private final CoverUrlResolver coverUrlResolver;
    private final AdminAuditService auditService;

    public AdminCourseService(JdbcTemplate jdbcTemplate, CoverUrlResolver coverUrlResolver,
                              AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.coverUrlResolver = coverUrlResolver;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageData<CourseItem> list(int page, int size, Long subjectId, Long stageId,
                                     String keyword, String status) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (subjectId != null) {
            where.append(" AND p.subject_id = ?");
            args.add(subjectId);
        }
        if (stageId != null) {
            where.append(" AND c.stage_id = ?");
            args.add(stageId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND c.title LIKE ?");
            args.add("%" + escapeLike(keyword.trim()) + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND c.status = ?");
            args.add(parseStatus(status));
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + SELECT_FROM + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);
        List<CourseItem> items = jdbcTemplate.query(
                SELECT_LIST + where + " ORDER BY c.sort_no ASC, c.id ASC LIMIT ? OFFSET ?",
                rowMapper(false), pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public CourseItem detail(long id) {
        List<CourseItem> rows = jdbcTemplate.query(SELECT_DETAIL + " WHERE c.id = ?", rowMapper(true), id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.COURSE_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public CourseItem create(CreateCourseRequest request) {
        String title = request.title().trim();
        requireStageBelongsToSubject(request.stageId(), request.subjectId());
        String url = requireBilibiliUrl(request.bilibiliUrl());
        long adminId = CurrentUserContext.require(Audience.ADMIN).id();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO course (stage_id, title, bilibili_url, description, duration_seconds, cover_url, "
                            + "sort_no, status, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, request.stageId());
            statement.setString(2, title);
            statement.setString(3, url);
            statement.setString(4, request.description() == null ? "" : request.description().trim());
            if (request.durationSeconds() == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, request.durationSeconds());
            }
            statement.setString(6, request.coverKey() == null ? "" : request.coverKey().trim());
            statement.setInt(7, request.sortNo() == null ? 0 : request.sortNo());
            statement.setLong(8, adminId);
            statement.setLong(9, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long newId = key == null ? 0 : key.longValue();
        auditService.record("course", "create", "course", newId, "新增课程 title=" + title);
        return detail(newId);
    }

    @Transactional
    public CourseItem update(long id, UpdateCourseRequest request) {
        CourseItem current = detail(id);
        long targetStageId = request.stageId() == null ? current.stageId() : request.stageId();
        long targetSubjectId = request.subjectId() == null ? current.subjectId() : request.subjectId();
        if (request.stageId() != null || request.subjectId() != null) {
            requireStageBelongsToSubject(targetStageId, targetSubjectId);
        }

        StringBuilder sql = new StringBuilder("UPDATE course SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());

        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "课程标题不能为空");
            }
            sql.append(", title = ?");
            args.add(title);
        }
        if (request.stageId() != null) {
            sql.append(", stage_id = ?");
            args.add(request.stageId());
        }
        if (request.bilibiliUrl() != null) {
            sql.append(", bilibili_url = ?");
            args.add(requireBilibiliUrl(request.bilibiliUrl()));
        }
        if (request.description() != null) {
            sql.append(", description = ?");
            args.add(request.description().trim());
        }
        if (request.durationSeconds() != null) {
            sql.append(", duration_seconds = ?");
            args.add(request.durationSeconds());
        }
        if (request.coverKey() != null) {
            sql.append(", cover_url = ?");
            args.add(request.coverKey().trim());
        }
        if (request.sortNo() != null) {
            sql.append(", sort_no = ?");
            args.add(request.sortNo());
        }
        sql.append(" WHERE id = ?");
        args.add(id);

        if (args.size() > 2) {
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("course", "update", "course", id, "编辑课程");
        }
        return detail(id);
    }

    @Transactional
    public void enable(long id) {
        changeStatus(id, STATUS_ENABLED, ErrorCode.ALREADY_ENABLED, "enable");
    }

    @Transactional
    public void disable(long id) {
        changeStatus(id, STATUS_DISABLED, ErrorCode.ALREADY_DISABLED, "disable");
    }

    @Transactional
    public void reorder(SortOrderRequest request) {
        Set<Long> seen = new HashSet<>();
        for (SortOrderRequest.Item item : request.items()) {
            if (!seen.add(item.id())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "排序列表存在重复 ID");
            }
        }
        for (SortOrderRequest.Item item : request.items()) {
            int updated = jdbcTemplate.update(
                    "UPDATE course SET sort_no = ?, updated_by = ? WHERE id = ?",
                    item.sortNo(), CurrentUserContext.require(Audience.ADMIN).id(), item.id());
            if (updated == 0) {
                throw new BizException(ErrorCode.COURSE_NOT_FOUND);
            }
        }
        auditService.record("course", "update", "course", null,
                "调整课程排序，共 " + request.items().size() + " 条");
    }

    private void changeStatus(long id, int target, ErrorCode alreadyCode, String action) {
        Integer current = jdbcTemplate.query("SELECT status FROM course WHERE id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, id);
        if (current == null) {
            throw new BizException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (current == target) {
            throw new BizException(alreadyCode);
        }
        jdbcTemplate.update("UPDATE course SET status = ?, updated_by = ? WHERE id = ?",
                target, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("course", action, "course", id,
                "课程状态变更为 " + (target == STATUS_ENABLED ? "ENABLED" : "DISABLED"));
    }

    /** 40009：阶段不存在或不属于所选学科 */
    private void requireStageBelongsToSubject(long stageId, long subjectId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.id, p.subject_id FROM stage st JOIN learning_path p ON p.id = st.path_id WHERE st.id = ?",
                stageId);
        if (rows.isEmpty() || ((Number) rows.get(0).get("subject_id")).longValue() != subjectId) {
            throw new BizException(ErrorCode.COURSE_STAGE_MISMATCH);
        }
    }

    /** 40008：仅 http(s) 且 host 属于 B 站白名单 */
    static String requireBilibiliUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                throw new BizException(ErrorCode.COURSE_BILIBILI_URL_INVALID);
            }
            String normalized = host.toLowerCase();
            if (!ALLOWED_HOSTS.contains(normalized) && !normalized.endsWith(".bilibili.com")) {
                throw new BizException(ErrorCode.COURSE_BILIBILI_URL_INVALID);
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            throw new BizException(ErrorCode.COURSE_BILIBILI_URL_INVALID);
        }
    }

    private RowMapper<CourseItem> rowMapper(boolean withDescription) {
        return (rs, rowNum) -> {
            Object duration = rs.getObject("duration_seconds");
            return new CourseItem(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getLong("subject_id"),
                    rs.getString("subject_name"),
                    rs.getLong("stage_id"),
                    rs.getString("stage_name"),
                    rs.getString("bilibili_url"),
                    coverUrlResolver.resolve(rs.getString("cover_url")),
                    duration instanceof Number number ? number.intValue() : null,
                    rs.getInt("sort_no"),
                    rs.getInt("status") == STATUS_ENABLED ? "ENABLED" : "DISABLED",
                    withDescription ? rs.getString("description") : null,
                    TimeFormats.rfc3339(rs.getObject("created_at")),
                    TimeFormats.rfc3339(rs.getObject("updated_at")));
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

    private static int parseStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case "ENABLED" -> STATUS_ENABLED;
            case "DISABLED" -> STATUS_DISABLED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "状态取值只能是 ENABLED 或 DISABLED");
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
