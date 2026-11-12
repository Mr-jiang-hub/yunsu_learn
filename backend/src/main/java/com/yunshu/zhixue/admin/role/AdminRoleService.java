package com.yunshu.zhixue.admin.role;

import com.yunshu.zhixue.admin.common.AdminAuditService;
import com.yunshu.zhixue.admin.role.dto.RoleDetail;
import com.yunshu.zhixue.admin.role.dto.RoleItem;
import com.yunshu.zhixue.admin.role.dto.UpdateRoleModulesRequest;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色权限（04 §6.10.9 / PRD 6.8）。
 *
 * <p>固定三个预置角色，不提供新增/删除；super_admin 的模块集不可修改（40031）；
 * 模块编码必须在白名单内（40032）。模块权限缓存 TTL 60s，变更后 ≤60 秒生效。</p>
 */
@Service
public class AdminRoleService {

    /** 与 V2/V3 种子 / 04 §6.10.9 一致的模块白名单 */
    private static final List<String> MODULE_WHITELIST = List.of(
            "subject", "course", "question", "interview_settings", "learning_path",
            "student", "admin_user", "role", "operation_log", "dashboard", "notice", "message");

    private static final String SUPER_ADMIN_CODE = "super_admin";

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminRoleService(JdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RoleItem> list() {
        return jdbcTemplate.query(
                "SELECT r.id, r.code, r.name, r.description, "
                        + "(SELECT COUNT(*) FROM admin_role_module m WHERE m.role_id = r.id) AS module_count "
                        + "FROM admin_role r ORDER BY r.id ASC",
                (rs, rowNum) -> new RoleItem(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("module_count"),
                        true));
    }

    @Transactional(readOnly = true)
    public RoleDetail detail(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, code, name, description FROM admin_role WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.ROLE_NOT_FOUND);
        }
        Map<String, Object> row = rows.get(0);
        List<String> modules = jdbcTemplate.queryForList(
                "SELECT module_code FROM admin_role_module WHERE role_id = ? ORDER BY module_code ASC",
                String.class, id);
        return new RoleDetail(
                ((Number) row.get("id")).longValue(),
                (String) row.get("code"),
                (String) row.get("name"),
                (String) row.get("description"),
                modules);
    }

    /** 全量覆盖角色模块：先校验超级管理员不可改、编码白名单，再整体替换 */
    @Transactional
    public RoleDetail updateModules(long id, UpdateRoleModulesRequest request) {
        RoleDetail current = detail(id);
        if (SUPER_ADMIN_CODE.equals(current.code())) {
            throw new BizException(ErrorCode.SUPER_ADMIN_IMMUTABLE);
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String code : request.moduleCodes()) {
            String trimmed = code == null ? "" : code.trim();
            if (!MODULE_WHITELIST.contains(trimmed)) {
                throw new BizException(ErrorCode.MODULE_CODE_INVALID, "模块编码不合法: " + trimmed);
            }
            normalized.add(trimmed);
        }

        long adminId = CurrentUserContext.require(Audience.ADMIN).id();
        jdbcTemplate.update("DELETE FROM admin_role_module WHERE role_id = ?", id);
        List<Object[]> batchArgs = new ArrayList<>();
        for (String code : normalized) {
            batchArgs.add(new Object[]{id, code, adminId, adminId});
        }
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO admin_role_module (role_id, module_code, created_by, updated_by) VALUES (?, ?, ?, ?)",
                    batchArgs);
        }
        auditService.record("role", "update", "role", id,
                "角色模块权限全量覆盖为 " + String.join(",", normalized) + "（≤60 秒生效）");
        return detail(id);
    }
}
