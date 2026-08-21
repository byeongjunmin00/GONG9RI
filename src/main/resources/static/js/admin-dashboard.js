/**
 * admin-dashboard.js — 관리자 대시보드(admin/dashboard.html) 전용 스크립트
 *
 * AdminGuard.requireAdmin()으로 접근 확인 후 반환된 member 객체로 프로필을 채우고,
 * GET /api/admin/dashboard를 호출해 요약 KPI 카드를 채운다.
 * (중복으로 /api/auth/me를 호출하지 않는 프로젝트 컨벤션 준수)
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var statusEl = document.getElementById('dashboard-status');
  var quickActionsSectionEl = document.getElementById('quick-actions-section');

  var userNameEl = document.getElementById('summary-user-name');
  var userEmailEl = document.getElementById('summary-user-email');

  var totalMembersEl = document.getElementById('summary-total-members');
  var totalBuyersEl = document.getElementById('summary-total-buyers');
  var totalSellersEl = document.getElementById('summary-total-sellers');
  var totalProductsEl = document.getElementById('summary-total-products');
  var totalPaymentsEl = document.getElementById('summary-total-payments');
  var pendingRefundsEl = document.getElementById('summary-pending-refunds');

  var cardMembersEl = document.getElementById('card-members');
  var cardBuyersEl = document.getElementById('card-buyers');
  var cardSellersEl = document.getElementById('card-sellers');
  var cardProductsEl = document.getElementById('card-products');
  var cardPendingRefundsEl = document.getElementById('card-pending-refunds');

  if (
    !pageAlertEl || !pageAlertTextEl || !statusEl ||
    !totalMembersEl || !totalBuyersEl || !totalSellersEl ||
    !totalProductsEl || !totalPaymentsEl || !pendingRefundsEl ||
    !window.AdminGuard
  ) {
    return;
  }

  function showError(text) {
    pageAlertEl.hidden = false;
    pageAlertTextEl.textContent = text;
    statusEl.hidden = true;
  }

  function bindCardNavigation() {
    if (cardMembersEl) {
      cardMembersEl.addEventListener('click', function () {
        window.location.href = '/admin/members.html';
      });
    }
    if (cardBuyersEl) {
      cardBuyersEl.addEventListener('click', function () {
        window.location.href = '/admin/members.html';
      });
    }
    if (cardSellersEl) {
      cardSellersEl.addEventListener('click', function () {
        window.location.href = '/admin/members.html';
      });
    }
    if (cardProductsEl) {
      cardProductsEl.addEventListener('click', function () {
        window.location.href = '/admin/products.html';
      });
    }
    // "전체 결제" 카드는 별도 관리자 전용 목록 페이지가 없으므로 클릭 이벤트를 추가하지 않는다.
    if (cardPendingRefundsEl) {
      cardPendingRefundsEl.addEventListener('click', function () {
        window.location.href = '/admin/refunds.html?status=PENDING';
      });
    }
  }

  window.AdminGuard.requireAdmin().then(function (member) {
    if (!member) {
      return;
    }

    // AdminGuard가 이미 /api/auth/me를 호출하여 가져온 member 객체를 재활용한다.
    if (userNameEl) {
      userNameEl.textContent = member.name ? member.name + ' (관리자)' : '시스템 관리자';
    }
    // 관리자도 프로필 사진을 쓸 수 있다(member/profile-image 노출).
    window.Avatar.fill(document.querySelector('.mypage-profile__avatar'),
        member.name, member.profileImageUrl);
    if (userEmailEl) {
      userEmailEl.textContent = member.email || '';
    }

    bindCardNavigation();

    window.Api.get('/admin/dashboard')
      .then(function (summary) {
        totalMembersEl.textContent = summary.totalMembers + '명';
        totalBuyersEl.textContent = summary.totalBuyers + '명';
        totalSellersEl.textContent = summary.totalSellers + '명';
        totalProductsEl.textContent = summary.totalProducts + '개';
        totalPaymentsEl.textContent = summary.totalPayments + '건';
        pendingRefundsEl.textContent = summary.pendingRefundRequests + '건';

        statusEl.hidden = true;
        if (quickActionsSectionEl) {
          quickActionsSectionEl.hidden = false;
        }
      })
      .catch(function (err) {
        console.error('[admin-dashboard.js] failed to load summary:', err);
        var message = (err && err.message) || '대시보드 정보를 불러오지 못했습니다.';
        showError(message);
      });
  });
})();
