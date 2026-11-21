package com.yuji.apartment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private ProblemDetail problem(HttpStatusCode status,String detail){
        var problem=ProblemDetail.forStatusAndDetail(status,detail);
        String requestId=MDC.get("requestId");
        if(requestId!=null)problem.setProperty("requestId",requestId);
        return problem;
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception){
        Map<String,String> errors=new LinkedHashMap<>();
        for(FieldError error:exception.getBindingResult().getFieldErrors())
            errors.putIfAbsent(error.getField(),error.isBindingFailure()?"参数格式不正确":error.getDefaultMessage());
        var result=problem(HttpStatus.BAD_REQUEST,"请检查提交的数据");
        result.setTitle("参数校验失败");result.setProperty("errors",errors);return result;
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception){
        return ResponseEntity.status(exception.getStatusCode()).headers(exception.getHeaders())
                .body(problem(exception.getStatusCode(),exception.getReason()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,ServletRequestBindingException.class})
    public ProblemDetail malformed(Exception exception){return problem(HttpStatus.BAD_REQUEST,"请求参数缺失或格式不正确");}
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(Exception exception){return problem(HttpStatus.PAYLOAD_TOO_LARGE,"上传图片不能超过5MB");}
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail unsupportedMedia(Exception exception){return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"请求内容类型不支持");}
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflict(Exception exception){return problem(HttpStatus.CONFLICT,"数据已存在或仍有关联记录，请刷新后重试");}
    @ExceptionHandler({CannotGetJdbcConnectionException.class,QueryTimeoutException.class})
    public ProblemDetail unavailable(Exception exception){return problem(HttpStatus.SERVICE_UNAVAILABLE,"数据库暂时不可用，请稍后重试");}
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail notFound(Exception exception){return problem(HttpStatus.NOT_FOUND,"请求的资源不存在");}
    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception exception){
        log.error("请求处理失败，异常类型={}",exception.getClass().getName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,"处理失败，请凭请求编号联系管理员");
    }
}
