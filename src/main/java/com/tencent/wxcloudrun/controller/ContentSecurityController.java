package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.service.WechatContentSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/content-safety")
public class ContentSecurityController {
    private static final Logger logger = LoggerFactory.getLogger(ContentSecurityController.class);
    private final WechatContentSecurityService service;

    public ContentSecurityController(WechatContentSecurityService service) {
        this.service = service;
    }

    @PostMapping("/check")
    public ApiResponse check(@RequestHeader(value = "X-WX-OPENID", required = false) String openid,
                             @RequestBody Map<String, Object> request) {
        try {
            return ApiResponse.ok(service.check(request, openid));
        } catch (IllegalArgumentException exception) {
            return ApiResponse.ok(Collections.singletonMap("code", "INVALID_INPUT"));
        } catch (Exception exception) {
            // Do not log user submitted text, image data, access tokens, or OpenIDs.
            String diagnostic = WechatContentSecurityService.diagnosticCode(exception);
            logger.error("WeChat content safety check failed: {}", diagnostic);
            Map<String, Object> result = new HashMap<>();
            result.put("code", "CHECK_UNAVAILABLE");
            result.put("diagnostic", diagnostic);
            return ApiResponse.ok(result);
        }
    }
}
