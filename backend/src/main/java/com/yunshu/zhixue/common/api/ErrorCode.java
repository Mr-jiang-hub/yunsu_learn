package com.yunshu.zhixue.common.api;

import org.springframework.http.HttpStatus;

/**
 * 错误码登记入口。
 *
 * <p>码段规则见 docs/design/04.API接口设计.md 第 3 章：
 * 0 成功；10000–19999 通用；20000–29999 认证授权；30000–39999 C 端业务；
 * 40000–49999 B 端业务；50000–59999 外部依赖。</p>
 *
 * <p>本类当前只登记基础工程用到的通用、认证与存储码。业务码随模块实现按 04 文档
 * 原样补充，已发布的码不得复用或改语义。</p>
 */
public enum ErrorCode {

    OK(0, "ok", HttpStatus.OK),

    // 10000–19999 通用与协议
    PARAM_INVALID(10001, "请求参数校验失败", HttpStatus.OK),
    BODY_UNREADABLE(10002, "请求格式错误", HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(10003, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(10004, "Content-Type 不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RESOURCE_NOT_FOUND(10006, "接口不存在", HttpStatus.NOT_FOUND),
    PAYLOAD_TOO_LARGE(10005, "请求体过大", HttpStatus.PAYLOAD_TOO_LARGE),
    SYSTEM_ERROR(10100, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(10101, "服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE),

    // 20000–29999 认证与授权
    UNAUTHENTICATED(20001, "未登录", HttpStatus.UNAUTHORIZED),
    ACCESS_TOKEN_EXPIRED(20002, "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    ACCESS_TOKEN_INVALID(20003, "登录凭证无效，请重新登录", HttpStatus.UNAUTHORIZED),
    NO_MODULE_PERMISSION(20004, "无该模块访问权限", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(20005, "账号已停用，请联系管理员", HttpStatus.FORBIDDEN),
    ACCOUNT_CANCELLED(20006, "账号已注销", HttpStatus.FORBIDDEN),
    TOKEN_AUDIENCE_MISMATCH(20007, "登录凭证与当前端不匹配", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(20008, "刷新凭证无效或已撤销，请重新登录", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_EXPIRED(20009, "刷新凭证已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    MUST_CHANGE_PASSWORD(20010, "请先修改密码", HttpStatus.FORBIDDEN),
    LOGIN_LOCKED(20011, "登录失败次数过多，账号已锁定", HttpStatus.TOO_MANY_REQUESTS),

    // 30000–39999 C 端业务（认证段见 04 §3.4）
    PHONE_ALREADY_REGISTERED(30001, "手机号已注册", HttpStatus.OK),
    SMS_CODE_INVALID(30002, "验证码错误或已过期", HttpStatus.OK),
    SMS_SEND_TOO_FREQUENT(30003, "验证码发送过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    SMS_DAILY_LIMIT(30004, "单日验证码发送次数超限", HttpStatus.TOO_MANY_REQUESTS),
    PASSWORD_POLICY(30005, "密码不符合要求", HttpStatus.OK),
    LOGIN_CREDENTIAL_INVALID(30006, "手机号或密码错误", HttpStatus.OK),
    PASSWORD_LOGIN_LOCKED(30007, "密码错误次数过多，请使用验证码登录", HttpStatus.OK),
    PASSWORD_OLD_MISMATCH(30008, "原密码错误", HttpStatus.OK),
    PHONE_ALREADY_TAKEN(30009, "新手机号已被占用", HttpStatus.OK),
    PHONE_UNCHANGED(30010, "新手机号与原手机号相同", HttpStatus.OK),
    CANCEL_CODE_INVALID(30011, "注销确认验证码错误或过期", HttpStatus.OK),
    PRACTICE_NOT_FOUND(30020, "练习不存在或无权访问", HttpStatus.FORBIDDEN),
    PRACTICE_FINISHED(30021, "练习已结束", HttpStatus.OK),
    PRACTICE_EXPIRED(30022, "练习已过期", HttpStatus.OK),
    PRACTICE_NOT_IN_PROGRESS(30023, "练习不在进行中", HttpStatus.OK),
    QUESTION_NOT_IN_PRACTICE(30024, "题目不属于该练习", HttpStatus.OK),
    ANSWER_IMMUTABLE(30025, "该题已作答，不能重复提交", HttpStatus.OK),
    PRACTICE_VOIDED(30026, "练习已作废", HttpStatus.OK),
    STAGE_NO_QUESTIONS(30027, "该阶段暂无可用题目", HttpStatus.OK),
    WRONG_BOOK_EMPTY(30028, "错题本为空", HttpStatus.OK),
    WRONG_QUESTION_NOT_FOUND(30029, "错题不存在或无权访问", HttpStatus.FORBIDDEN),
    SELF_ASSESSMENT_INVALID(30030, "当前判分状态不允许自评", HttpStatus.OK),
    CONTENT_UNAVAILABLE(30031, "内容暂不可学习", HttpStatus.OK),
    PRACTICE_UNANSWERED(30033, "练习尚有未作答题目", HttpStatus.OK),
    PRACTICE_NOT_FINISHED(30034, "练习尚未结束，无最终结果", HttpStatus.OK),
    INTERVIEW_QUOTA_EXHAUSTED(30040, "今日面试次数已用完", HttpStatus.OK),
    INTERVIEW_SESSION_NOT_FOUND(30041, "面试会话不存在或无权访问", HttpStatus.FORBIDDEN),
    INTERVIEW_EXPIRED(30042, "面试已过期", HttpStatus.OK),
    INTERVIEW_VOIDED(30043, "面试已作废", HttpStatus.OK),
    INTERVIEW_NOT_IN_PROGRESS(30044, "面试不在进行中", HttpStatus.OK),
    INTERVIEW_COMPLETED(30045, "面试已完成", HttpStatus.OK),
    INTERVIEW_ROUND_INVALID(30046, "面试轮次非法", HttpStatus.OK),
    INTERVIEW_ROUND_SUBMITTED(30047, "该轮已提交且下一题已生成", HttpStatus.OK),
    INTERVIEW_CONFIG_INCOMPLETE(30048, "学科面试配置不完整", HttpStatus.OK),
    INTERVIEW_DIFFICULTY_DISABLED(30049, "难度档位已停用", HttpStatus.OK),
    INTERVIEW_NO_STAGE(30050, "学科无可用阶段", HttpStatus.OK),
    INTERVIEW_REPORT_PENDING(30051, "报告尚未生成", HttpStatus.OK),
    INTERVIEW_REPORT_ABSENT(30052, "该会话无报告", HttpStatus.OK),
    INTERVIEW_FINISH_NOT_ALLOWED(30053, "当前状态不允许提前结束", HttpStatus.OK),
    INTERVIEW_ROUND_UNFINISHED(30054, "当前轮次尚未完成", HttpStatus.OK),
    AUDIO_FORMAT_UNSUPPORTED(30060, "音频格式不支持", HttpStatus.OK),
    AUDIO_TOO_LARGE(30061, "音频过大或时长超限", HttpStatus.OK),
    AUDIO_EMPTY(30062, "音频为空", HttpStatus.OK),
    IMAGE_FORMAT_UNSUPPORTED(30070, "图片格式不支持", HttpStatus.OK),
    IMAGE_TOO_LARGE(30071, "图片过大", HttpStatus.OK),
    IMAGE_DIMENSION_EXCEEDED(30072, "图片尺寸超限", HttpStatus.OK),
    UPLOAD_EMPTY(30073, "上传文件为空", HttpStatus.OK),

    // 40000–49999 B 端业务
    SUBJECT_NAME_DUPLICATED(40001, "学科名称已存在", HttpStatus.OK),
    SUBJECT_NOT_FOUND(40002, "学科不存在", HttpStatus.OK),
    LEARNING_PATH_SUBJECT_DUPLICATED(40003, "该学科已存在学习路线", HttpStatus.OK),
    LEARNING_PATH_NOT_FOUND(40004, "学习路线不存在", HttpStatus.OK),
    STAGE_NOT_FOUND(40005, "阶段不存在", HttpStatus.OK),
    STAGE_ORDER_INVALID(40006, "阶段排序不合法", HttpStatus.OK),
    COURSE_NOT_FOUND(40007, "课程不存在", HttpStatus.OK),
    COURSE_BILIBILI_URL_INVALID(40008, "B 站链接格式不正确，仅支持 bilibili.com / b23.tv", HttpStatus.OK),
    COURSE_STAGE_MISMATCH(40009, "所选阶段不属于该学科", HttpStatus.OK),
    QUESTION_NOT_FOUND(40010, "题目不存在", HttpStatus.OK),
    QUESTION_TYPE_IMMUTABLE(40011, "题目已有作答记录，题型不可修改", HttpStatus.OK),
    QUESTION_OPTION_INVALID(40012, "选项配置不符合题型要求", HttpStatus.OK),
    QUESTION_SCORING_POINT_REQUIRED(40013, "简答题至少需要一条评分点", HttpStatus.OK),
    QUESTION_CONTENT_DUPLICATED(40014, "同阶段已存在相同题干的题目", HttpStatus.OK),
    IMPORT_FILE_TYPE_INVALID(40019, "导入文件类型不支持，请使用 xlsx 模板", HttpStatus.OK),
    IMPORT_FILE_TOO_LARGE(40020, "导入文件过大，请拆分后重试", HttpStatus.OK),
    IMPORT_TASK_NOT_FOUND(40021, "导入任务不存在或无权访问", HttpStatus.OK),
    IMPORT_TASK_PROCESSING(40022, "导入任务仍在处理中", HttpStatus.OK),
    IMPORT_FILE_EMPTY(40023, "导入文件为空或无数据行", HttpStatus.OK),
    IMPORT_ROW_LIMIT_EXCEEDED(40024, "导入行数超过上限（1000 行）", HttpStatus.OK),
    DIMENSION_NOT_FOUND(40015, "面试维度不存在", HttpStatus.OK),
    DIMENSION_NAME_DUPLICATED(40016, "同学科下维度名称已存在", HttpStatus.OK),
    DIFFICULTY_NOT_FOUND(40017, "难度档位不存在", HttpStatus.OK),
    DIFFICULTY_DUPLICATED(40018, "难度名称已存在", HttpStatus.OK),
    STUDENT_NOT_FOUND(40025, "学员不存在", HttpStatus.OK),
    STUDENT_CANCELLED(40026, "学员已注销，不可执行该操作", HttpStatus.OK),
    CANNOT_DISABLE_SELF(40027, "不能停用当前登录账号", HttpStatus.OK),
    ADMIN_USER_DUPLICATED(40028, "后台账号已存在", HttpStatus.OK),
    ADMIN_USER_NOT_FOUND(40029, "后台账号不存在", HttpStatus.OK),
    ROLE_NOT_FOUND(40030, "角色不存在", HttpStatus.OK),
    SUPER_ADMIN_IMMUTABLE(40031, "超级管理员权限不可修改", HttpStatus.OK),
    MODULE_CODE_INVALID(40032, "模块编码不合法", HttpStatus.OK),
    DASHBOARD_RANGE_INVALID(40033, "看板时间范围不支持", HttpStatus.OK),
    ALREADY_DISABLED(40034, "该记录已是停用状态", HttpStatus.OK),
    ALREADY_ENABLED(40035, "该记录已是启用状态", HttpStatus.OK),
    ADMIN_LOGIN_CREDENTIAL_INVALID(40036, "后台账号或密码错误", HttpStatus.OK),
    NOTICE_NOT_FOUND(40037, "公告不存在", HttpStatus.OK),
    NOTICE_STATUS_INVALID(40038, "公告当前状态不允许该操作", HttpStatus.OK),
    MESSAGE_NOT_FOUND(40039, "消息不存在", HttpStatus.OK),
    MESSAGE_STATUS_INVALID(40040, "消息当前状态不允许该操作", HttpStatus.OK),
    MESSAGE_TARGET_INVALID(40041, "消息接收人不合法", HttpStatus.OK),
    IDEMPOTENCY_KEY_REUSED(40042, "该幂等键已用于其他请求", HttpStatus.OK),

    // 50000–59999 外部依赖
    SMS_GATEWAY_FAILED(50001, "短信发送失败，请稍后重试", HttpStatus.BAD_GATEWAY),
    INTERVIEW_OPENING_FAILED(50002, "面试开场题生成失败，次数已返还", HttpStatus.BAD_GATEWAY),
    STORAGE_UNAVAILABLE(50004, "文件存储服务暂不可用", HttpStatus.BAD_GATEWAY),
    AI_CONTENT_UNPARSEABLE(50006, "AI 返回内容无法解析", HttpStatus.BAD_GATEWAY);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
