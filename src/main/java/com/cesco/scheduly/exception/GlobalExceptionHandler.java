package com.cesco.scheduly.exception;

import com.cesco.scheduly.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        ApiResponse errorResponse = new ApiResponse(ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiResponse> handleInvalidInputException(InvalidInputException ex, WebRequest request) {
        ApiResponse errorResponse = new ApiResponse(ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class) // 일반적인 잘못된 인자 예외 처리
    public ResponseEntity<ApiResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        ApiResponse errorResponse = new ApiResponse("잘못된 요청입니다: " + ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class) // 그 외 모든 예외 처리
    public ResponseEntity<ApiResponse> handleGlobalException(Exception ex, WebRequest request) {
        // 중요: 실제 운영 환경에서는 에러 로깅을 철저히 해야 합니다.
        System.err.println("Unhandled exception: " + ex.getMessage());
        ex.printStackTrace();
        ApiResponse errorResponse = new ApiResponse("서버 내부 오류가 발생했습니다. 관리자에게 문의하세요.");
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}