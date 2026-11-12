package com.yunshu.zhixue.common.security;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.config.AppProperties;
import com.yunshu.zhixue.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(new AppProperties(
            new AppProperties.Security(
                    new AppProperties.Chain("c-secret-for-test-0123456789abcdef", Duration.ofMinutes(30)),
                    new AppProperties.Chain("admin-secret-for-test-0123456789abcdef", Duration.ofMinutes(30)),
                    Duration.ofDays(7),
                    // 32 字节 Base64：仅用于单测构造，与运行期配置无关
                    "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                    "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA="),
            new AppProperties.Cors("http://localhost:5173"),
            new AppProperties.Features(true, false),
            new AppProperties.Async(4, 8, 200)));

    @Test
    void createsAndParsesAccessToken() {
        String token = provider.createAccessToken(1001L, Audience.C);

        AuthPrincipal principal = provider.parseAccessToken(token, Audience.C);

        assertThat(principal.id()).isEqualTo(1001L);
        assertThat(principal.audience()).isEqualTo(Audience.C);
    }

    @Test
    void rejectsTokenFromAnotherAudience() {
        String cToken = provider.createAccessToken(1001L, Audience.C);

        assertThatThrownBy(() -> provider.parseAccessToken(cToken, Audience.ADMIN))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_AUDIENCE_MISMATCH);
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> provider.parseAccessToken("not-a-jwt", Audience.C))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
    }
}
