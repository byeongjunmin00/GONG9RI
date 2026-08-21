package com.gong9ri.gong9ri.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 관리자 1:1 상담방 (support/chat).
 *
 * <p>AI 챗봇의 {@code chat_session}과 <b>재사용하지 않고 따로 둔다.</b> 참여자 구조(구매자 1명 vs
 * 사용자+관리자)와 수명주기(대화가 끝나면 닫힘)가 다르고, 섞으면 양쪽 다 지저분해진다.
 *
 * <p><b>한 회원이 동시에 열어둘 수 있는 방은 1개</b>다 — 무한 생성을 막고, "내 상담"이 하나로 특정돼야
 * 화면이 단순해진다. 닫힌 방은 지우지 않고 이력으로 남긴다(같은 사람이 다시 열면 새 방이 생긴다).
 * 이 불변식은 {@code (member_id, status)} 조합을 서비스에서 확인해 지킨다 — DB 유니크 제약으로 못
 * 거는 건 닫힌 방이 여러 개일 수 있어서다.
 *
 * <p>안 읽은 개수를 양쪽으로 따로 센다. 상담은 비대칭이라(사용자는 자기 방 하나, 관리자는 여러 방)
 * 관리자 목록에서 "답을 기다리는 방"을 위로 올리려면 관리자 쪽 미읽음이 방 단위로 필요하다.
 */
@Entity
@Table(name = "support_room", indexes = {
        @Index(name = "idx_member_status", columnList = "member_id, status"),
        @Index(name = "idx_status_last_message", columnList = "status, last_message_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SupportRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 상담을 연 사람. 구매자·판매자 모두 열 수 있다(플랫폼 문의라 역할을 가리지 않는다). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRoomStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 목록 정렬용 — 마지막 대화가 언제였는지. 메시지가 없으면 생성 시각과 같다. */
    @Column(nullable = false)
    private LocalDateTime lastMessageAt;

    @Column(nullable = false)
    private int unreadForMember;

    @Column(nullable = false)
    private int unreadForAdmin;

    /**
     * 각 측이 <b>어느 시점까지 읽었는지</b>. 미읽음 "개수"만으로는 개별 메시지 옆에 읽음 표시를 할 수
     * 없어서 따로 둔다 — 내가 보낸 메시지가 읽혔는지는 "상대가 마지막으로 읽은 시각이 이 메시지보다
     * 뒤인가"로만 판정할 수 있기 때문(2026-08-21 사용자 요청).
     *
     * <p>메시지마다 읽음 행을 쌓지 않는 이유: 1:1 대화라 읽은 사람이 한 명뿐이고, 시각 하나로 그 이전
     * 메시지가 전부 읽힌 것이 되므로 행을 N개 만들 이유가 없다.
     *
     * <p>nullable — 이 컬럼이 생기기 전의 방들은 값이 없다. 그때는 "아직 안 읽음"으로 취급한다
     * (읽지 않았는데 읽었다고 표시하는 쪽이 더 나쁘다).
     */
    private LocalDateTime memberLastReadAt;

    private LocalDateTime adminLastReadAt;

    public SupportRoom(Member member) {
        this.member = member;
        this.status = SupportRoomStatus.OPEN;
        this.lastMessageAt = LocalDateTime.now();
        this.unreadForMember = 0;
        this.unreadForAdmin = 0;
    }

    /** 메시지가 오갔을 때 — 보낸 쪽 반대편의 미읽음만 올린다. */
    public void onMessageSent(boolean sentByAdmin) {
        this.lastMessageAt = LocalDateTime.now();
        if (sentByAdmin) {
            this.unreadForMember++;
        } else {
            this.unreadForAdmin++;
        }
    }

    public void markReadBy(boolean admin) {
        if (admin) {
            this.unreadForAdmin = 0;
            this.adminLastReadAt = LocalDateTime.now();
        } else {
            this.unreadForMember = 0;
            this.memberLastReadAt = LocalDateTime.now();
        }
    }

    /**
     * 이 방에서 {@code sentByAdmin}이 보낸, {@code sentAt}에 만들어진 메시지를 상대가 읽었는지.
     * 상대의 마지막 읽은 시각이 그 메시지보다 뒤면 읽은 것이다.
     */
    public boolean isReadByCounterpart(boolean sentByAdmin, LocalDateTime sentAt) {
        LocalDateTime counterpartReadAt = sentByAdmin ? memberLastReadAt : adminLastReadAt;
        return counterpartReadAt != null && sentAt != null && !counterpartReadAt.isBefore(sentAt);
    }

    public void close() {
        this.status = SupportRoomStatus.CLOSED;
    }

    public boolean isOpen() {
        return status == SupportRoomStatus.OPEN;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }
}
