package com.yunshu.zhixue.admin.user;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.common.TempPassword;
import com.yunshu.zhixue.admin.student.dto.ResetPasswordResponse;
import com.yunshu.zhixue.admin.user.dto.AdminUserDetail;
import com.yunshu.zhixue.admin.user.dto.AdminUserItem;
import com.yunshu.zhixue.admin.user.dto.CreateAdminUserRequest;
import com.yunshu.zhixue.admin.user.dto.UpdateAdminUserRequest;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.api.PageData;
import com.yunshu.zhixue.common.api.PageQuery;
import com.yunshu.zhixue.common.auth.RefreshTokenService;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.util.TimeFormats;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台账号管理（04 §6.10.8 / PRD 6.7）。
 *
 * <p>账号只停用不删除；不允许停用当前登录账号（40027）；重置密码撤销全部 refresh token；
 * 新建账号 password_reset_required=1，首次登录强制改密。</p>
 */
@Service
public class AdminUserService {

    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_DISABLED = 2;

    private static final int SUBJECT_TYPE_ADMIN = 2;
    private static final int REVOKE_PASSWORD_RESET = 2;
    private static final int REVOKE_DISABLED = 3;

    private static final String SELECT_BASE =
            "SELECT a.id, a.account, a.display_name, a.role_id, a.status, a.password_reset_required, "
                    + "a.last_login_at, a.created_at, a.updated_at, r.name AS role_name, r.code AS role_code "
                    + "FROM admin_user a JOIN admin_role r ON r.id = a.role_id ";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AdminAuditService auditService;

    public AdminUserService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
                            RefreshTokenService refreshTokenService, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageData<AdminUserItem> list(int page, int size, Long roleId, String status, String keyword) {
        validatePaging(page, size);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (roleId != null) {
            where.append(" AND a.role_id = ?");
            args.add(roleId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND a.status = ?");
            args.add(parseStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (a.account LIKE ? OR a.display_name LIKE ?)");
            String like = "%" + escapeLike(keyword.trim()) + "%";
            args.add(like);
            args.add(like);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_user a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) (page - 1) * size);

        List<AdminUserItem> items = jdbcTemplate.query(
                SELECT_BASE + where + " ORDER BY a.id ASC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminUserItem(
                        rs.getLong("id"),
                        rs.getString("account"),
                        rs.getString("display_name"),
                        rs.getLong("role_id"),
                        rs.getString("role_name"),
                        rs.getInt("status") == STATUS_ACTIVE ? "ACTIVE" : "DISABLED",
                        TimeFormats.rfc3339(rs.getObject("last_login_at")),
                        TimeFormats.rfc3339(rs.getObject("created_at"))),
                pageArgs.toArray());
        return PageData.of(items, page, size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public AdminUserDetail detail(long id) {
        List<AdminUserDetail> rows = jdbcTemplate.query(
                SELECT_BASE + " WHERE a.id = ?",
                (rs, rowNum) -> new AdminUserDetail(
                        rs.getLong("id"),
                        rs.getString("account"),
                        rs.getString("display_name"),
                        rs.getLong("role_id"),
                        rs.getString("role_name"),
                        rs.getString("role_code"),
                        rs.getInt("status") == STATUS_ACTIVE ? "ACTIVE" : "DISABLED",
                        truthy(rs.getObject("password_reset_required")),
                        TimeFormats.rfc3339(rs.getObject("last_login_at")),
                        TimeFormats.rfc3339(rs.getObject("created_at")),
                        TimeFormats.rfc3339(rs.getObject("updated_at"))),
                id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.ADMIN_USER_NOT_FOUND);
        }
        return rows.get(0);
    }

    @Transactional
    public AdminUserDetail create(CreateAdminUserRequest request) {
        String username = request.username().trim();
        requireAccountAvailable(username);
        requireRoleExists(request.roleId());

        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO admin_user (account, password_hash, display_name, role_id, status, "
                            + "password_reset_required, created_by, updated_by) VALUES (?, ?, ?, ?, 1, 1, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, passwordEncoder.encode(request.password()));
            statement.setString(3, request.displayName().trim());
            statement.setLong(4, request.roleId());
            statement.setLong(5, adminId);
            statement.setLong(6, adminId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        long newId = key == null ? 0 : key.longValue();
        auditService.record("admin_user", "create", "admin_user", newId,
                "新增后台账号 account=" + username + "，首次登录需改密");
        return detail(newId);
    }

    @Transactional
    public AdminUserDetail update(long id, UpdateAdminUserRequest request) {
        detail(id);
        StringBuilder sql = new StringBuilder("UPDATE admin_user SET updated_by = ?");
        List<Object> args = new ArrayList<>();
        args.add(CurrentUserContext.require(Audience.ADMIN).id());

        if (request.displayName() != null) {
            String displayName = request.displayName().trim();
            if (displayName.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "姓名不能为空");
            }
            sql.append(", display_name = ?");
            args.add(displayName);
        }
        if (request.roleId() != null) {
            requireRoleExists(request.roleId());
            sql.append(", role_id = ?");
            args.add(request.roleId());
        }
        sql.append(" WHERE id = ?");
        args.add(id);

        if (args.size() > 2) {
            jdbcTemplate.update(sql.toString(), args.toArray());
            auditService.record("admin_user", "update", "admin_user", id, "编辑后台账号");
        }
        return detail(id);
    }

    @Transactional
    public void disable(long id) {
        long currentAdminId = CurrentUserContext.require(Audience.ADMIN).id();
        if (id == currentAdminId) {
            throw new BizException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        Integer current = jdbcTemplate.query("SELECT status FROM admin_user WHERE id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, id);
        if (current == null) {
            throw new BizException(ErrorCode.ADMIN_USER_NOT_FOUND);
        }
        if (current == STATUS_DISABLED) {
            throw new BizException(ErrorCode.ALREADY_DISABLED);
        }
        jdbcTemplate.update("UPDATE admin_user SET status = ?, updated_by = ? WHERE id = ?",
                STATUS_DISABLED, currentAdminId, id);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_ADMIN, id, REVOKE_DISABLED);
        auditService.record("admin_user", "disable", "admin_user", id, "停用后台账号并撤销全部登录态");
    }

    @Transactional
    public void enable(long id) {
        Integer current = jdbcTemplate.query("SELECT status FROM admin_user WHERE id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, id);
        if (current == null) {
            throw new BizException(ErrorCode.ADMIN_USER_NOT_FOUND);
        }
        if (current == STATUS_ACTIVE) {
            throw new BizException(ErrorCode.ALREADY_ENABLED);
        }
        jdbcTemplate.update("UPDATE admin_user SET status = ?, updated_by = ? WHERE id = ?",
                STATUS_ACTIVE, CurrentUserContext.require(Audience.ADMIN).id(), id);
        auditService.record("admin_user", "enable", "admin_user", id, "启用后台账号");
    }

    @Transactional
    public ResetPasswordResponse resetPassword(long id) {
        detail(id);
        String temporaryPassword = TempPassword.generate();
        jdbcTemplate.update(
                "UPDATE admin_user SET password_hash = ?, password_reset_required = 1, updated_by = ? WHERE id = ?",
                passwordEncoder.encode(temporaryPassword), CurrentUserContext.require(Audience.ADMIN).id(), id);
        refreshTokenService.revokeAllFor(SUBJECT_TYPE_ADMIN, id, REVOKE_PASSWORD_RESET);
        auditService.record("admin_user", "reset-password", "admin_user", id,
                "重置后台账号密码，强制下次登录改密");
        return new ResetPasswordResponse(temporaryPassword, true,
                TimeFormats.rfc3339(LocalDateTime.now(ZoneOffset.UTC)));
    }

    private void requireAccountAvailable(String account) {
        List<Long> rows = jdbcTemplate.queryForList(
                "SELECT id FROM admin_user WHERE account = ?", Long.class, account);
        if (!rows.isEmpty()) {
            throw new BizException(ErrorCode.ADMIN_USER_DUPLICATED);
        }
    }

    private void requireRoleExists(long roleId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_role WHERE id = ?", Long.class, roleId);
        if (count == null || count == 0) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }

    private static int parseStatus(String status) {
        return switch (status.trim().toUpperCase()) {
            case "ACTIVE" -> STATUS_ACTIVE;
            case "DISABLED" -> STATUS_DISABLED;
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "状态取值只能是 ACTIVE 或 DISABLED");
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
