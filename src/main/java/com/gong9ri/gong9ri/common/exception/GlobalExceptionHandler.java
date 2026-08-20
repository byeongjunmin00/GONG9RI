package com.gong9ri.gong9ri.common.exception;

import com.gong9ri.gong9ri.common.response.ApiResponse;
import com.gong9ri.gong9ri.common.web.BrowserRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getErrorCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.failure(e.getErrorCode().name(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_FAILED.getMessage());
        log.warn("Validation failed: {}", message);
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED.name(), message));
    }

    // 잘못된 JSON 등 요청 본문 파싱 실패는 클라이언트 입력 오류이지, 서버 오류(500)가 아니다.
    // Exception.class catch-all보다 먼저 잡히도록 구체 타입으로 별도 처리한다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문 파싱 실패: {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.VALIDATION_FAILED.name(), ErrorCode.VALIDATION_FAILED.getMessage()));
    }

    // 존재하지 않는 정적 리소스(favicon.ico 등) 요청은 클라이언트가 흔히 자동으로 보내는 것이지 서버
    // 오류가 아니다 — Exception.class catch-all이 이걸 500으로 바꿔버리는 걸 실제로 Railway 프로덕션
    // 로그에서 발견했다(2026-08-12, GET /favicon.ico -> 500). 원래 스프링이 의도한 404로 되돌린다.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e,
            HttpServletRequest request) {
        // 사람이 주소창에 오타를 내고 들어온 경우 — 날 JSON 대신 에러 페이지로 보낸다. 프로그램 호출
        // (fetch 등)은 Accept에 text/html이 없어 이 분기를 타지 않으므로 기존 JSON 응답 그대로다
        // (2026-08-20, docs/dev/frontend/error-page/design.md).
        if (BrowserRequests.isBrowserNavigation(request)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/error.html")
                    .build();
        }
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    // 구매자 챗봇 SSE(BuyerChatController)처럼 이미 text/event-stream으로 응답이 커밋된 상태에서
    // AsyncRequestTimeoutException 등이 이 catch-all까지 흘러 들어오면, 여기서 JSON 바디를 새로 쓰려다
    // HttpMessageNotWritableException이 추가로 발생한다(동시성 버그 실측 재현, docs/dev/ai/buyer-chatbot/
    // changes/002-*.md). 응답이 이미 커밋됐으면 더 쓸 게 없으니 null을 반환해 그대로 둔다 — 다른 일반
    // JSON API는 예외 시점에 응답이 아직 커밋되지 않으므로 기존 동작 그대로 유지된다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletResponse response) {
        if (response.isCommitted()) {
            log.warn("이미 커밋된 응답에 대한 예외 — JSON 바디를 쓰지 않고 무시함: {}", e.getMessage());
            return null;
        }
        log.error("Unexpected exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.INTERNAL_SERVER_ERROR.name(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
