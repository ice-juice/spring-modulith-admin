package com.sma.admin.openapi.internal.web;
import java.io.IOException;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.*;
import com.sma.admin.common.*;
@RestControllerAdvice(assignableTypes=OpenController.class) @Order(Ordered.HIGHEST_PRECEDENCE)
class OpenExceptionHandler {
 private final ErrorResponseWriter errors;OpenExceptionHandler(ErrorResponseWriter errors){this.errors=errors;}
 @ExceptionHandler(BizException.class) void business(BizException failure,HttpServletRequest request,HttpServletResponse response)throws IOException{errors.write(request,response,failure.getErrorCode());}
}
