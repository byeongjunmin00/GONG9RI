package com.gong9ri.gong9ri.entity;

public enum Role {
    BUYER,
    SELLER,
    // 관리자(admin) — 공개 회원가입(POST /api/auth/signup)으로는 절대 가입할 수 없다
    // (MemberService.signup() 가드). 최초 계정은 배포 후 DB에 직접 심는다(docs/dev/admin/design.md).
    ADMIN
}
