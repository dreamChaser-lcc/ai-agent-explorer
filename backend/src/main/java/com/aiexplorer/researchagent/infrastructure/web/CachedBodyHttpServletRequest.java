package com.aiexplorer.researchagent.infrastructure.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * SP8 教学演示：可重复读取请求体的包装器（可删）。
 *
 * 原理：Servlet 的 getInputStream() 是消耗型流——读一次就空。
 * 这里在构造时一次性读完存进字节数组，getInputStream() 每次返回【新的】ByteArrayInputStream，
 * 这样 Filter 读完日志后，Controller 的 @RequestBody 仍能读到完整 body。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream buffer = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public int read() {
                return buffer.read();
            }

            @Override
            public boolean isFinished() {
                return buffer.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                // 演示用：不支持异步读
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    /** 供 Filter 打日志/脱敏使用（业务读取请走 getInputStream）。 */
    public String getBodyText() {
        return new String(cachedBody, StandardCharsets.UTF_8);
    }
}