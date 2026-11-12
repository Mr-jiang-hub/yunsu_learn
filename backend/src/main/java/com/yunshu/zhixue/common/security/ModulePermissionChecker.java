package com.yunshu.zhixue.common.security;

/**
 * B 端模块权限查询。角色只保存“可访问模块集合”，超级管理员固定全模块。
 */
public interface ModulePermissionChecker {

    boolean hasModule(long adminUserId, String moduleCode);
}
