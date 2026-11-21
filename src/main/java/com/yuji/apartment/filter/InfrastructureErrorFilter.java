package com.yuji.apartment.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;

/** Handles failures before MVC (including Redis-backed session loading/saving). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+1)
public class InfrastructureErrorFilter extends OncePerRequestFilter {
    private final ObjectMapper json;
    public InfrastructureErrorFilter(ObjectMapper json){this.json=json;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        try{chain.doFilter(request,response);}
        catch(Exception exception){
            if(isUnavailable(exception)&&!response.isCommitted()){
                response.resetBuffer();response.setStatus(503);
                response.setContentType("application/problem+json");response.setCharacterEncoding("UTF-8");
                var body=new LinkedHashMap<String,Object>();
                body.put("type","about:blank");body.put("title","Service Unavailable");body.put("status",503);
                body.put("detail","服务暂时不可用，请稍后重试");
                if(MDC.get("requestId")!=null)body.put("requestId",MDC.get("requestId"));
                json.writeValue(response.getOutputStream(),body);
                return;
            }
            if(exception instanceof IOException io)throw io;
            if(exception instanceof ServletException servlet)throw servlet;
            if(exception instanceof RuntimeException runtime)throw runtime;
            throw new ServletException(exception);
        }
    }
    private static boolean isUnavailable(Throwable exception){
        for(Throwable cause=exception;cause!=null;cause=cause.getCause()){
            if(cause instanceof RedisConnectionFailureException || cause instanceof RedisSystemException
                    || cause instanceof CannotGetJdbcConnectionException || cause instanceof QueryTimeoutException)return true;
            if(cause.getCause()==cause)break;
        }
        return false;
    }
}
