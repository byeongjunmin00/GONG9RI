/**
 * admin-dashboard.js — 관리자 대시보드(admin/dashboard.html) 전용 스크립트
 *
 * AdminGuard.requireAdmin()으로 접근 확인 후 GET /api/admin/dashboard 한 번만 호출해 요약 카드를
 * 채운다(무거운 집계 없음, AdminService.dashboard() 참고).
 */
(function () {
  var pageAlertEl = document.getElementById('page-alert');
  var pageAlertTextEl = document.getElementById('page-alert-text');
  var statusEl = document.getElementById('dashboard-status');
  var summaryCardsEl = document.getElementById('summary-cards');

  var totalMembersEl = document.getElementById('summary-total-members');
  var totalBuyersEl = document.getElementById('summary-total-buyers');
  var totalSellersEl = document.getElementById('summary-total-sellers');
  var totalProductsEl = document.getElementById('summary-total-products');
  var totalPaymentsEl = document.getElementById('summary-total-payments');
  var pendingRefundsEl = document.getElementById('summary-pending-refunds');

  if (
    !pageAlertEl || !pageAlertTextEl || !statusEl || !summaryCardsEl ||
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

  window.AdminGuard.requireAdmin().then(function (member) {
    if (!member) {
      return;
    }

    window.Api.get('/admin/dashboard')
      .then(function (summary) {
        totalMembersEl.textContent = summary.totalMembers + '명';
        totalBuyersEl.textContent = summary.totalBuyers + '명';
        totalSellersEl.textContent = summary.totalSellers + '명';
        totalProductsEl.textContent = summary.totalProducts + '개';
        totalPaymentsEl.textContent = summary.totalPayments + '건';
        pendingRefundsEl.textContent = summary.pendingRefundRequests + '건';

        statusEl.hidden = true;
        summaryCardsEl.hidden = false;
      })
      .catch(function (err) {
        console.error('[admin-dashboard.js] failed to load summary:', err);
        var message = (err && err.message) || '대시보드 정보를 불러오지 못했습니다.';
        showError(message);
      });
  });
})();
