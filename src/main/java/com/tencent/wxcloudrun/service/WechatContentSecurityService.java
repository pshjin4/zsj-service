package com.tencent.wxcloudrun.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class WechatContentSecurityService {
    private static final int MAX_IMAGE_BYTES = 1024 * 1024;
    private static final int TEXT_CHUNK_SIZE = 2000;
    private static final String WECHAT_API = "https://api.weixin.qq.com";
    private static final String CLOUD_ENV_ID = "prod-d3gn4j2r46c8daa7e";

    @Value("${WECHAT_MINIAPP_APPID:}")
    private String appId;

    @Value("${WECHAT_MINIAPP_SECRET:}")
    private String appSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String accessToken;
    private volatile long accessTokenExpiresAt;

    public Map<String, Object> check(Map<String, Object> request, String openid) {
        if (openid == null || openid.trim().isEmpty()) throw new IllegalArgumentException("无法确认微信用户身份");
        String type = string(request.get("type"));
        if ("text".equals(type)) return checkText(request, openid);
        if ("image".equals(type)) return checkImage(request, openid);
        throw new IllegalArgumentException("内容检测类型无效");
    }

    public static String diagnosticCode(Exception exception) {
        if (exception instanceof SafeCheckException) return ((SafeCheckException) exception).diagnosticCode;
        if (exception instanceof RestClientResponseException) {
            return "WX_HTTP_ERROR_" + ((RestClientResponseException) exception).getRawStatusCode();
        }
        if (exception instanceof ResourceAccessException) return "WX_NETWORK_ERROR";
        StackTraceElement[] trace = exception.getStackTrace();
        if (trace.length > 0) {
            StackTraceElement location = trace[0];
            String owner = location.getClassName();
            owner = owner.substring(owner.lastIndexOf('.') + 1);
            return "CHECK_FAILED_" + exception.getClass().getSimpleName() + "_" + owner + "_" + location.getMethodName();
        }
        return "CHECK_FAILED_" + exception.getClass().getSimpleName();
    }

    private Map<String, Object> checkText(Map<String, Object> request, String openid) {
        Object contentsValue = request.get("contents");
        if (!(contentsValue instanceof List)) throw new IllegalArgumentException("待检测文本无效");
        int scene = number(request.get("scene"), 4);
        if (scene < 1 || scene > 4) throw new IllegalArgumentException("检测场景无效");
        for (Object value : (List<?>) contentsValue) {
            if (!(value instanceof String)) throw new IllegalArgumentException("待检测文本无效");
            for (String content : chunks(((String) value).trim())) {
                if (content.isEmpty()) continue;
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("content", content);
                body.put("version", 2);
                body.put("scene", scene);
                body.put("openid", openid);
                Map<String, Object> result = postForMap(apiUrl("/wxa/msg_sec_check"), body);
                Map<String, Object> mapped = classify(result);
                if ("CONTENT_RISK".equals(mapped.get("code"))) return mapped;
            }
        }
        return response("OK");
    }

    private Map<String, Object> checkImage(Map<String, Object> request, String openid) {
        Object fileIdValue = request.get("fileID");
        if (!(fileIdValue instanceof String)) throw new IllegalArgumentException("待检测图片无效");
        String fileId = (String) fileIdValue;
        if (!fileId.startsWith("cloud://" + CLOUD_ENV_ID + ".")) throw new IllegalArgumentException("待检测图片无效");
        Map<String, Object> fileRequest = new java.util.HashMap<>();
        fileRequest.put("env", CLOUD_ENV_ID);
        Map<String, Object> fileItem = new java.util.HashMap<>();
        fileItem.put("fileid", fileId);
        fileItem.put("max_age", 600);
        fileRequest.put("file_list", Collections.singletonList(fileItem));
        Map<String, Object> fileResponse = postForMap(apiUrl("/tcb/batchdownloadfile"), fileRequest);
        classify(fileResponse);
        Object fileListValue = fileResponse.get("file_list");
        if (!(fileListValue instanceof List) || ((List<?>) fileListValue).isEmpty()) {
            throw new SafeCheckException("CLOUD_FILE_URL_MISSING");
        }
        Object itemValue = ((List<?>) fileListValue).get(0);
        if (!(itemValue instanceof Map)) throw new SafeCheckException("CLOUD_FILE_URL_MISSING");
        Map<?, ?> fileResult = (Map<?, ?>) itemValue;
        int fileStatus = number(fileResult.get("status"), 0);
        if (fileStatus != 0) throw new SafeCheckException("CLOUD_FILE_ERROR_" + fileStatus);
        Object downloadUrlValue = fileResult.get("download_url");
        if (!(downloadUrlValue instanceof String)) throw new SafeCheckException("CLOUD_FILE_URL_MISSING");
        byte[] image = restTemplate.getForObject((String) downloadUrlValue, byte[].class);
        if (image == null) throw new SafeCheckException("CLOUD_FILE_EMPTY");
        if (image.length == 0 || image.length > MAX_IMAGE_BYTES) throw new IllegalArgumentException("图片大小超出限制");
        String contentType = detectImageType(image);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        ByteArrayResource file = new ByteArrayResource(image) {
            @Override public String getFilename() { return "upload" + extension(contentType); }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        form.add("media", new HttpEntity<>(file, fileHeaders));
        form.add("version", "2");
        form.add("scene", "4");
        form.add("openid", openid);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        Map<String, Object> result = postForMap(
                apiUrl("/wxa/img_sec_check"), new HttpEntity<>(form, headers));
        return classify(result);
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiresAt) return accessToken;
        if (appId == null || appId.trim().isEmpty() || appSecret == null || appSecret.trim().isEmpty()) {
            throw new SafeCheckException("CONFIG_MISSING");
        }
        String url = UriComponentsBuilder.fromHttpUrl(WECHAT_API + "/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .toUriString();
        Map<String, Object> tokenResponse = getForMap(url);
        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            int errcode = tokenResponse == null ? -1 : number(tokenResponse.get("errcode"), -1);
            throw new SafeCheckException("WX_TOKEN_ERROR_" + errcode);
        }
        accessToken = String.valueOf(tokenResponse.get("access_token"));
        int expiresIn = number(tokenResponse.get("expires_in"), 7200);
        accessTokenExpiresAt = System.currentTimeMillis() + Math.max(60, expiresIn - 300) * 1000L;
        return accessToken;
    }

    private String apiUrl(String path) {
        return UriComponentsBuilder.fromHttpUrl(WECHAT_API + path)
                .queryParam("access_token", getAccessToken()).toUriString();
    }

    private Map<String, Object> getForMap(String url) {
        String body = restTemplate.getForObject(url, String.class);
        return parseJsonObject(body);
    }

    private Map<String, Object> postForMap(String url, Object request) {
        String body = restTemplate.postForObject(url, request, String.class);
        return parseJsonObject(body);
    }

    private Map<String, Object> parseJsonObject(String body) {
        if (body == null || body.trim().isEmpty()) throw new SafeCheckException("WX_EMPTY_RESPONSE");
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            throw new SafeCheckException("WX_INVALID_JSON_RESPONSE");
        }
    }

    private Map<String, Object> classify(Map<String, Object> result) {
        if (result == null) throw new SafeCheckException("WX_EMPTY_RESPONSE");
        int errcode = number(result.get("errcode"), 0);
        if (errcode == 87014) return response("CONTENT_RISK");
        if (errcode != 0) throw new SafeCheckException("WX_API_ERROR_" + errcode);
        Object resultValue = result.get("result");
        if (resultValue instanceof Map) {
            Object suggest = ((Map<?, ?>) resultValue).get("suggest");
            if (suggest != null && !"pass".equalsIgnoreCase(String.valueOf(suggest))) return response("CONTENT_RISK");
        }
        return response("OK");
    }

    private static List<String> chunks(String text) {
        List<String> result = new ArrayList<>();
        int[] chars = text.codePoints().toArray();
        for (int start = 0; start < chars.length; start += TEXT_CHUNK_SIZE) {
            result.add(new String(chars, start, Math.min(TEXT_CHUNK_SIZE, chars.length - start)));
        }
        return result;
    }

    private static String detectImageType(byte[] image) {
        if (image.length >= 3 && (image[0] & 255) == 255 && (image[1] & 255) == 216 && (image[2] & 255) == 255) return "image/jpeg";
        if (image.length >= 8 && (image[0] & 255) == 137 && image[1] == 80 && image[2] == 78 && image[3] == 71) return "image/png";
        if (image.length >= 6 && new String(image, 0, 6, StandardCharsets.US_ASCII).matches("GIF8[79]a")) return "image/gif";
        throw new IllegalArgumentException("仅支持 JPEG、PNG 或 GIF 图片");
    }

    private static String extension(String contentType) {
        if ("image/png".equals(contentType)) return ".png";
        if ("image/gif".equals(contentType)) return ".gif";
        return ".jpg";
    }

    private static Map<String, Object> response(String code) {
        return Collections.<String, Object>singletonMap("code", code);
    }

    public static final class SafeCheckException extends RuntimeException {
        private final String diagnosticCode;
        SafeCheckException(String diagnosticCode) {
            super(diagnosticCode);
            this.diagnosticCode = diagnosticCode;
        }
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int number(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; }
    }
}
