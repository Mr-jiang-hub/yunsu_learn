package com.yunshu.zhixue.admin.common;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.AuthPrincipal;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.security.ModulePermissionChecker;
import com.yunshu.zhixue.common.upload.ImageUploadService;
import com.yunshu.zhixue.common.upload.dto.UploadResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * B 端图片上传（04 §5.1 第 69 号 / B-12）。
 *
 * <p>跨模块支撑接口：purpose 决定归属模块与对象前缀，权限按"调用方模块"动态判定，
 * 因此不能用类上的 {@code @RequireModule} 固定成一个模块。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/uploads")
public class AdminUploadController {

    /**
     * purpose → 模块码 + 对象前缀；白名单外的 purpose 一律 10001。
     *
     * <p>只开放 04 §6.10.12 定义的两种用途：库里有 `subject.cover_url` / `course.cover_url`
     * 两列承接 key。阶段与题目没有图片列，若开放对应用途，上传得到的 key 无处绑定，
     * 会稳定产生孤儿文件（04-Q47），因此不随前缀一起放开。</p>
     */
    private static final Map<String, Purpose> PURPOSES = Map.of(
            "SUBJECT_COVER", new Purpose("subject", "subject"),
            "COURSE_COVER", new Purpose("course", "course"));

    private final ImageUploadService imageUploadService;
    private final ModulePermissionChecker permissionChecker;

    public AdminUploadController(ImageUploadService imageUploadService,
                                 ModulePermissionChecker permissionChecker) {
        this.imageUploadService = imageUploadService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> uploadImage(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "SUBJECT_COVER") String purpose) {
        AuthPrincipal principal = CurrentUserContext.require(Audience.ADMIN);
        Purpose target = PURPOSES.get(purpose.toUpperCase());
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "暂不支持该上传用途");
        }
        if (!permissionChecker.hasModule(principal.id(), target.module())) {
            throw new BizException(ErrorCode.NO_MODULE_PERMISSION);
        }
        return ApiResponse.ok(imageUploadService.upload(file, target.prefix()));
    }

    private record Purpose(String module, String prefix) {
    }
}
