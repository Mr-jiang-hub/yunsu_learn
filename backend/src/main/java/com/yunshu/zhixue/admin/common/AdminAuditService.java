package com.yunshu.zhixue.admin.common;

import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * B 端操作审计（PRD 6.9）：内容变更、启停、排序、重置密码等敏感动作落 operation_log。
 * 写日志失败不影响主流程，只记 error 日志。
 */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);
    private static final int DETAIL_MAX = 1000;

    private final JdbcTemplate jdbcTemplate;

    public AdminAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String moduleCode, String action, String targetType, Long targetId, String detail) {
        try {
            long adminId = CurrentUserContext.require(Audience.ADMIN).id();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT a.display_name, r.name AS role_name FROM admin_user a "
                            + "JOIN admin_role r ON r.id = a.role_id WHERE a.id = ?", adminId);
            String operatorName = rows.isEmpty() ? "" : String.valueOf(rows.get(0).get("display_name"));
            String roleName = rows.isEmpty() ? "" : String.valueOf(rows.get(0).get("role_name"));
            String safeDetail = detail == null ? "" : (detail.length() > DETAIL_MAX ? detail.substring(0, DETAIL_MAX) : detail);

            jdbcTemplate.update(
                    "INSERT INTO operation_log (operator_id, operator_name, role_name, module_code, action, "
                            + "target_type, target_id, result, detail, ip, trace_id) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)",
                    adminId, operatorName, roleName, moduleCode, action, targetType, targetId,
                    safeDetail, clientIp(), TraceIdFilter.current());
        } catch (RuntimeException ex) {
            log.error("写操作日志失败 module={} action={} target={}#{}", moduleCode, action, targetType, targetId, ex);
        }
    }

    private static String clientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }
}
