/**
 * api.js — REST API 호출 공통 fetch 래퍼 (뼈대)
 *
 * - base path: /api (docs/api/ 기준)
 * - 인증: 세션 기반(JSESSIONID 쿠키) — credentials: 'same-origin'으로 쿠키를 함께 전송한다.
 * - 공통 응답 형식(docs/api/README.md):
 *     성공: { "success": true, "data": { ... } }
 *     실패: { "success": false, "code": "...", "message": "..." }
 *   성공 시 data만 꺼내서 반환하고, 실패 시 code/message를 담은 Error를 throw한다.
 *
 * 이 파일은 아직 실제로 호출하는 개별 페이지가 없는 상태의 공통 뼈대다.
 * 향후 개별 페이지에서 window.Api.get/post/put/patch/del을 그대로 사용한다.
 */
(function () {
  var API_BASE = '/api';

  /**
   * @param {string} path  '/products' 처럼 API_BASE 뒤에 붙는 경로
   * @param {object} [options]
   * @param {string} [options.method='GET']
   * @param {*} [options.body]     JSON.stringify 대상 (있으면 Content-Type: application/json)
   * @param {object} [options.headers]
   */
  function request(path, options) {
    options = options || {};
    var method = options.method || 'GET';
    var headers = Object.assign({}, options.headers);
    var fetchOptions = {
      method: method,
      headers: headers,
      credentials: 'same-origin',
    };

    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      fetchOptions.body = JSON.stringify(options.body);
    }

    return fetch(API_BASE + path, fetchOptions).then(function (res) {
      // 204 No Content 등 본문이 없는 응답
      if (res.status === 204) {
        return null;
      }

      return res
        .json()
        .catch(function () {
          return null; // 본문이 JSON이 아니거나 비어있는 경우
        })
        .then(function (json) {
          if (!res.ok || (json && json.success === false)) {
            var code = (json && json.code) || 'UNKNOWN_ERROR';
            var message = (json && json.message) || ('요청 처리 중 오류가 발생했습니다 (status ' + res.status + ')');
            var error = new Error(message);
            error.code = code;
            error.status = res.status;
            throw error;
          }

          if (json === null) {
            return null;
          }

          return json.data !== undefined ? json.data : json;
        });
    });
  }

  window.Api = {
    get: function (path, options) {
      return request(path, Object.assign({}, options, { method: 'GET' }));
    },
    post: function (path, body, options) {
      return request(path, Object.assign({}, options, { method: 'POST', body: body }));
    },
    put: function (path, body, options) {
      return request(path, Object.assign({}, options, { method: 'PUT', body: body }));
    },
    patch: function (path, body, options) {
      return request(path, Object.assign({}, options, { method: 'PATCH', body: body }));
    },
    del: function (path, options) {
      return request(path, Object.assign({}, options, { method: 'DELETE' }));
    },
  };
})();
