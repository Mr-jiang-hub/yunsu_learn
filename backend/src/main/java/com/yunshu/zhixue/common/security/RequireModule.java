package com.yunshu.zhixue.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * B 端模块级权限声明（PRD 2.3）：模块码示例 subject / course / question /
 * interview_settings / learning_path / student / admin_user / role /
 * operation_log / dashboard / notice / message。
 *
 * <p>只做模块级，不做按钮级、数据行级；前端隐藏菜单不是安全边界。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireModule {

    String value();
}
