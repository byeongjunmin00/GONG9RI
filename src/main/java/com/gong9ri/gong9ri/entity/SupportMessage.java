package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 상담 메시지 (support/chat).
 *
 * <p><b>실시간이지만 저장이 먼저다.</b> WebSocket으로 받은 메시지를 저장한 뒤에 브로드캐스트한다 —
 * 반대로 하면 화면에는 떴는데 DB엔 없는 메시지가 생긴다. 관리자가 접속 중이 아니어도 메시지는
 * 남아서, 나중에 상담 목록에서 확인할 수 있다("실시간이면 좋고, 아니어도 유실 없음").
 *
 * <p>보낸 사람을 {@code sender}(회원)로 두고 <b>관리자 여부를 따로 저장하지 않는다</b> — 역할은
 * {@code sender.role}로 알 수 있고, 나중에 그 회원의 역할이 바뀌어도 "이 메시지는 관리자가 보낸
 * 것"이라는 사실이 흔들리면 안 되므로 {@code sentByAdmin}을 보낸 시점 기준으로 박아둔다.
 */
@Entity
@Table(name = "support_message", indexes = {
        @Index(name = "idx_room_created", columnList = "room_id, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SupportMessage {

    /** 한 메시지 최대 길이 — 상담 대화라 길 필요가 없고, 서버가 잘라내지 않고 거절한다. */
    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private SupportRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    /** 보낸 시점 기준으로 박아둔다 — 나중에 역할이 바뀌어도 대화 기록의 의미가 흔들리면 안 된다. */
    @Column(nullable = false)
    private boolean sentByAdmin;

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SupportMessage(SupportRoom room, Member sender, boolean sentByAdmin, String content) {
        this.room = room;
        this.sender = sender;
        this.sentByAdmin = sentByAdmin;
        this.content = content;
    }
}
