/**
 * product-image-picker.js — 상품 이미지 선택·업로드·미리보기 (product/image)
 *
 * 판매자 상품 등록(seller/products/new.html)과 수정(edit.html)이 함께 쓴다. 두 페이지가 같은 마크업
 * (#imageFiles, #imageUrl, #image-preview-list ...)을 갖고 있어 로직을 한 곳에 둔다.
 *
 * - 파일을 고르면 **즉시 업로드**하고(POST /api/seller/products/images) 서버가 준 경로를 목록에 담는다.
 *   폼 제출까지 기다리지 않아 미리보기를 바로 보여줄 수 있고, 상품 등록 요청은 지금처럼 JSON 하나로
 *   유지된다(multipart로 갈아엎지 않아도 됨).
 * - 외부 이미지 주소도 같은 목록에 담긴다 — 업로드한 파일과 구분하지 않는다.
 * - 폼 제출 시 window.ProductImagePicker.getUrls()로 순서대로 꺼내 imageUrls에 실어 보낸다.
 *
 * 서버가 최종 방어선이다(장수·크기·실제 이미지 여부). 여기 검증은 사용자가 기다렸다가 거절당하는 걸
 * 줄이기 위한 것이지 보안 장치가 아니다.
 */
(function () {
  var MAX_IMAGES = 5;
  var MAX_FILE_BYTES = 5 * 1024 * 1024;

  var fileInput = document.getElementById('imageFiles');
  var urlInput = document.getElementById('imageUrl');
  var addUrlBtn = document.getElementById('add-image-url-btn');
  var listEl = document.getElementById('image-preview-list');
  var statusEl = document.getElementById('image-upload-status');

  if (!fileInput || !listEl || !window.Api) {
    return;
  }

  var urls = [];

  function showStatus(message, isError) {
    if (!statusEl) {
      return;
    }
    statusEl.textContent = message;
    statusEl.className = 'form-alert ' + (isError ? 'form-alert--error' : 'form-alert--success');
    statusEl.hidden = !message;
  }

  function clearStatus() {
    showStatus('', false);
  }

  function render() {
    listEl.innerHTML = '';
    urls.forEach(function (url, index) {
      var item = document.createElement('li');
      item.className = 'image-preview-item';

      var img = document.createElement('img');
      img.className = 'image-preview-thumb';
      img.src = url;
      img.alt = '상품 이미지 ' + (index + 1);
      // 주소를 잘못 입력했거나 외부 이미지가 사라진 경우 — 깨진 아이콘 대신 상태를 알려준다.
      img.addEventListener('error', function () {
        item.classList.add('is-broken');
        img.alt = '불러올 수 없는 이미지';
      });
      item.appendChild(img);

      if (index === 0) {
        var badge = document.createElement('span');
        badge.className = 'image-preview-badge';
        badge.textContent = '대표';
        item.appendChild(badge);
      }

      var removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'image-preview-remove';
      removeBtn.setAttribute('aria-label', (index + 1) + '번째 이미지 빼기');
      removeBtn.textContent = '×';
      removeBtn.addEventListener('click', function () {
        urls.splice(index, 1);
        render();
        clearStatus();
      });
      item.appendChild(removeBtn);

      listEl.appendChild(item);
    });
  }

  function addUrl(url) {
    if (!url) {
      return false;
    }
    if (urls.length >= MAX_IMAGES) {
      showStatus('이미지는 최대 ' + MAX_IMAGES + '장까지 등록할 수 있어요.', true);
      return false;
    }
    if (urls.indexOf(url) !== -1) {
      showStatus('이미 추가된 이미지예요.', true);
      return false;
    }
    urls.push(url);
    render();
    return true;
  }

  function uploadFile(file) {
    var formData = new FormData();
    formData.append('file', file);
    // window.Api는 JSON 전용(Content-Type을 직접 세팅)이라 여기선 fetch를 직접 쓴다 —
    // multipart는 브라우저가 boundary를 포함한 Content-Type을 스스로 만들어야 해서
    // 직접 지정하면 오히려 깨진다.
    return fetch('/api/seller/products/images', {
      method: 'POST',
      body: formData,
      credentials: 'same-origin',
    })
      .then(function (res) {
        return res.json().catch(function () { return null; }).then(function (json) {
          if (!res.ok || !json || json.success === false) {
            var message = (json && json.message) || '이미지 업로드에 실패했어요.';
            throw new Error(message);
          }
          return json.data.url;
        });
      });
  }

  fileInput.addEventListener('change', function () {
    var files = Array.prototype.slice.call(fileInput.files || []);
    if (!files.length) {
      return;
    }
    clearStatus();

    var remaining = MAX_IMAGES - urls.length;
    if (remaining <= 0) {
      showStatus('이미지는 최대 ' + MAX_IMAGES + '장까지 등록할 수 있어요.', true);
      fileInput.value = '';
      return;
    }
    if (files.length > remaining) {
      showStatus(remaining + '장만 더 올릴 수 있어서 앞의 ' + remaining + '장만 올릴게요.', true);
      files = files.slice(0, remaining);
    }

    var tooLarge = files.filter(function (f) { return f.size > MAX_FILE_BYTES; });
    if (tooLarge.length) {
      showStatus('한 장에 5MB까지만 올릴 수 있어요. 큰 사진 ' + tooLarge.length + '장은 제외했어요.', true);
      files = files.filter(function (f) { return f.size <= MAX_FILE_BYTES; });
    }
    if (!files.length) {
      fileInput.value = '';
      return;
    }

    fileInput.disabled = true;
    showStatus('사진 ' + files.length + '장 올리는 중…', false);

    // 순서대로 올려야 사용자가 고른 순서가 그대로 유지된다(대표 이미지가 첫 장이므로 순서가 의미를 갖는다).
    files.reduce(function (chain, file) {
      return chain.then(function () {
        return uploadFile(file).then(function (url) { addUrl(url); });
      });
    }, Promise.resolve())
      .then(function () {
        showStatus('사진을 올렸어요.', false);
      })
      .catch(function (err) {
        console.error('[product-image-picker.js] 업로드 실패:', err);
        showStatus(err.message || '이미지 업로드에 실패했어요.', true);
      })
      .then(function () {
        fileInput.disabled = false;
        fileInput.value = '';
      });
  });

  if (addUrlBtn && urlInput) {
    addUrlBtn.addEventListener('click', function () {
      clearStatus();
      var value = urlInput.value.trim();
      if (!value) {
        return;
      }
      if (addUrl(value)) {
        urlInput.value = '';
      }
    });
  }

  window.ProductImagePicker = {
    /** 폼 제출 시 imageUrls로 보낼 값 — 화면에 보이는 순서 그대로. */
    getUrls: function () {
      return urls.slice();
    },
    /** 수정 화면에서 기존 이미지를 채울 때 사용. */
    setUrls: function (initial) {
      urls = (initial || []).filter(function (u) { return typeof u === 'string' && u; }).slice(0, MAX_IMAGES);
      render();
    },
  };
})();
