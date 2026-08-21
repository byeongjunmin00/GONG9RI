package com.gong9ri.gong9ri.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 존재하는 아이디입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다."),
    PRODUCT_NOT_YET_OPEN(HttpStatus.CONFLICT, "아직 공개되지 않은 상품입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공구팀입니다."),
    INVALID_TARGET_PARTICIPANTS(HttpStatus.BAD_REQUEST,
            "targetParticipants가 해당 상품의 가격 구간(price_tier) 목록에 존재하지 않습니다."),
    TEAM_FULL(HttpStatus.CONFLICT, "이미 정원이 찬 공구팀입니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참가한 공구팀입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제 내역입니다."),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.CONFLICT,
            "결제 확인에 실패했습니다. 실제 결제 상태·금액이 요청과 일치하지 않습니다."),
    PAYMENT_GATEWAY_ERROR(HttpStatus.SERVICE_UNAVAILABLE,
            "결제 서비스와 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
    WEBHOOK_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "웹훅 서명 검증에 실패했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    AI_SUGGESTION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AI 제안을 생성하지 못했습니다. 잠시 후 다시 시도해주세요."),
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅 세션입니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    LOGIN_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "이메일 인증이 필요합니다. 가입 시 받은 메일의 링크를 확인해주세요."),
    INVALID_OR_EXPIRED_TOKEN(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 링크입니다."),
    REVIEW_NOT_ELIGIBLE(HttpStatus.FORBIDDEN, "구매 완료한 상품에만 리뷰를 작성할 수 있습니다."),
    DUPLICATE_REVIEW(HttpStatus.CONFLICT, "이미 이 상품에 리뷰를 작성했습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 리뷰입니다."),
    TEAM_NOT_RECRUITING(HttpStatus.CONFLICT, "모집이 끝난 공구팀은 참여를 취소할 수 없습니다."),
    REFUND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 환불 요청입니다."),
    REFUND_REQUEST_ALREADY_DECIDED(HttpStatus.CONFLICT, "이미 처리된 환불 요청입니다."),
    REFUND_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 처리 대기 중인 환불 요청이 있습니다."),
    TEAM_PAYMENT_REFUND_NOT_ALLOWED(HttpStatus.CONFLICT,
            "공구팀 결제는 참여 취소를 통해서만 환불할 수 있습니다. 직접 환불 요청은 혼자구매 건에만 가능합니다."),
    PAYMENT_NOT_REFUNDABLE(HttpStatus.CONFLICT, "환불할 수 없는 결제 상태입니다."),
    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 문의입니다."),
    INQUIRY_ALREADY_ANSWERED(HttpStatus.CONFLICT, "이미 답변이 등록된 문의는 수정/삭제할 수 없습니다."),
    ANSWER_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 답변이 없습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다. 문의는 관리자에게 해주세요."),
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 계정입니다. 새로 가입해주세요."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    MEMBER_HAS_ACTIVITY(HttpStatus.CONFLICT,
            "상품·결제·리뷰 등 활동 기록이 있는 회원은 삭제할 수 없습니다. 정지 처리를 이용해주세요."),
    // 관리자 1:1 상담(support/chat)
    SUPPORT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상담입니다."),
    SUPPORT_ROOM_CLOSED(HttpStatus.CONFLICT, "이미 종료된 상담입니다."),
    // 상품 삭제(product/admin) — 회원 삭제와 같은 정책이다. 돈·기록이 걸린 상품은 지우지 못하게 막는다.
    PRODUCT_HAS_ACTIVITY(HttpStatus.CONFLICT,
            "결제·공구팀·리뷰가 있는 상품은 삭제할 수 없습니다."),
    // 상품 이미지 업로드(product/image) — 확장자·Content-Type이 아니라 실제 디코딩 가능 여부로 판정한다.
    INVALID_IMAGE_FILE(HttpStatus.BAD_REQUEST, "이미지 파일이 아니거나 지원하지 않는 형식입니다."),
    IMAGE_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지 한 장은 5MB까지 올릴 수 있습니다."),
    TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "상품 이미지는 최대 5장까지 등록할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장에 실패했습니다."),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."),
    // 판매자 주문·배송 상태 관리(mypage/view 007)
    SHIPMENT_STATUS_NOT_APPLICABLE(HttpStatus.CONFLICT,
            "환불되었거나 아직 배송 대상이 아닌 주문은 배송 상태를 변경할 수 없습니다."),
    TRACKING_NUMBER_REQUIRED(HttpStatus.BAD_REQUEST, "배송중/배송완료 상태로 바꾸려면 송장번호를 입력해야 합니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
