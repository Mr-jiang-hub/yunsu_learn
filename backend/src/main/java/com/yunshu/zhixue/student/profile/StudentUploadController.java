package com.yunshu.zhixue.student.profile;

import com.yunshu.zhixue.common.api.ApiResponse;
import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.exception.BizException;
import com.yunshu.zhixue.common.security.Audience;
import com.yunshu.zhixue.common.security.CurrentUserContext;
import com.yunshu.zhixue.common.upload.ImageUploadService;
import com.yunshu.zhixue.common.upload.dto.UploadResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * C 端图片上传（04 §6.9）：当前只服务头像。
 *
 * <p>数据库存 `key`，接口返回短时签名 `url`；先上传拿 key，再 PATCH 到个人资料。</p>
 */
@RestController
@RequestMapping("/api/v1/c/uploads")
public class StudentUploadController {

    private final ImageUploadService imageUploadService;

    public StudentUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadResponse> uploadImage(@RequestPart("file") MultipartFile file,
                                                   @RequestParam(defaultValue = "AVATAR") String purpose) {
        // 鉴权先走一遍，确保未登录不消耗存储
        CurrentUserContext.require(Audience.C);
        if (!"AVATAR".equalsIgnoreCase(purpose)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "暂只支持 purpose=AVATAR");
        }
        return ApiResponse.ok(imageUploadService.uploadAvatar(file));
    }
}
