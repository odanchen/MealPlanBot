package app.mealplan.web;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class ApiErrors {
    public record Body(String code,String message,Map<String,String> fieldErrors,Long currentVersion) {}
    public static final class Failure extends RuntimeException {
        public final int status; public final String code; public final Long currentVersion;
        public Failure(int status,String code,String message,Long currentVersion) { super(message); this.status=status; this.code=code; this.currentVersion=currentVersion; }
    }
    public static Failure notFound() { return new Failure(404,"not_found","Resource not found",null); }
    public static Failure forbidden() { return new Failure(403,"forbidden","Action is not permitted",null); }
    public static Failure invalid(String message) { return new Failure(422,"invalid_input",message,null); }
    @ExceptionHandler(Failure.class) ResponseEntity<Body> failure(Failure e) { return response(e.status,e.code,e.getMessage(),null,e.currentVersion); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Body> validation(MethodArgumentNotValidException e) {
        var fields=new java.util.TreeMap<String,String>();
        for(var error:e.getBindingResult().getFieldErrors()) fields.put(error.getField(),"Invalid value");
        return response(422,"invalid_input","Check the supplied fields",fields,null);
    }
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ResponseEntity<Body> invalid(Exception e) { return response(422,"invalid_input","Invalid request",null,null); }
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<Body> missing(Exception e) { return response(404,"not_found","Resource not found",null,null); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<Body> denied(Exception e) { return response(403,"forbidden","Action is not permitted",null,null); }
    @ExceptionHandler(Exception.class) ResponseEntity<Body> unexpected(Exception e) { return response(500,"internal_error","Request could not be completed",null,null); }
    private ResponseEntity<Body> response(int status,String code,String message,Map<String,String> fields,Long version) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new Body(code,message,fields,version));
    }
}
