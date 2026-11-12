/* =========================================================
   云枢智学 · UI 工具库（图标 / 弹窗 / 提示 / 图表）
   ========================================================= */
(function () {
  'use strict';

  const PATHS = {
    home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
    map: '<path d="M9 20 3 17V4l6 3 6-3 6 3v13l-6-3-6 3Z"/><path d="M9 7v13"/><path d="M15 4v13"/>',
    edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
    sparkles: '<path d="m12 3 1.9 4.6L18.5 9.5 13.9 11.4 12 16l-1.9-4.6L5.5 9.5l4.6-1.9L12 3Z"/><path d="M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9L19 15Z"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    user: '<path d="M20 21a8 8 0 0 0-16 0"/><circle cx="12" cy="8" r="4"/>',
    book: '<path d="M4 4h11a3 3 0 0 1 3 3v13H7a3 3 0 0 1-3-3V4Z"/><path d="M8 4v16"/>',
    chart: '<path d="M3 3v18h18"/><path d="M7 15l4-5 3 3 5-7"/>',
    users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    shield: '<path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6l-8-3Z"/><path d="m9 12 2 2 4-4"/>',
    file: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9l-6-6Z"/><path d="M14 3v6h6"/><path d="M8 13h8"/><path d="M8 17h5"/>',
    plus: '<path d="M12 5v14"/><path d="M5 12h14"/>',
    trash: '<path d="M3 6h18"/><path d="M8 6V4h8v2"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
    check: '<path d="m20 6-11 11-5-5"/>',
    x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
    play: '<path d="m8 5 11 7-11 7V5Z"/>',
    arrowRight: '<path d="M5 12h14"/><path d="m13 6 6 6-6 6"/>',
    arrowUp: '<path d="M12 19V5"/><path d="m6 11 6-6 6 6"/>',
    arrowDown: '<path d="M12 5v14"/><path d="m6 13 6 6 6-6"/>',
    chevronRight: '<path d="m9 6 6 6-6 6"/>',
    chevronLeft: '<path d="m15 6-6 6 6 6"/>',
    chevronDown: '<path d="m6 9 6 6 6-6"/>',
    upload: '<path d="M12 16V4"/><path d="m6 10 6-6 6 6"/><path d="M4 20h16"/>',
    download: '<path d="M12 4v12"/><path d="m6 10 6 6 6-6"/><path d="M4 20h16"/>',
    filter: '<path d="M3 5h18l-7 8v5l-4 2v-7L3 5Z"/>',
    logout: '<path d="M15 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4"/><path d="M10 17l5-5-5-5"/><path d="M15 12H3"/>',
    bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>',
    target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
    flame: '<path d="M12 3c1 3 4 4 4 8a4 4 0 0 1-8 0c0-2 1-3 2-4 0 2 1 3 2 3 1-2 0-4 0-7Z"/>',
    bot: '<rect x="4" y="7" width="16" height="12" rx="4"/><path d="M12 3v4"/><circle cx="9" cy="13" r="1"/><circle cx="15" cy="13" r="1"/><path d="M9 17h6"/>',
    stack: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 12 9 5 9-5"/><path d="m3 16 9 5 9-5"/>',
    coffee: '<path d="M4 9h12v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V9Z"/><path d="M16 10h2a2 2 0 0 1 0 4h-2"/><path d="M6 4v2"/><path d="M10 4v2"/>',
    lock: '<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
    phone: '<path d="M7 3h3l2 5-2 2a14 14 0 0 0 5 5l2-2 5 2v3a2 2 0 0 1-2 2A17 17 0 0 1 5 5a2 2 0 0 1 2-2Z"/>',
    key: '<circle cx="8" cy="15" r="4"/><path d="m11 12 9-9"/><path d="m17 6 2 2"/><path d="m14 9 2 2"/>',
    eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
    refresh: '<path d="M21 12a9 9 0 1 1-3-6.7"/><path d="M21 4v5h-5"/>',
    alert: '<path d="M12 3 2 21h20L12 3Z"/><path d="M12 9v5"/><path d="M12 18h.01"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6"/><path d="M12 8h.01"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4"/><path d="M16 3v4"/><path d="M3 10h18"/>',
    video: '<rect x="3" y="6" width="13" height="12" rx="2"/><path d="m16 10 5-3v10l-5-3"/>',
    message: '<path d="M21 12a8 8 0 0 1-8 8H7l-4 3v-7a8 8 0 0 1 8-9h2a8 8 0 0 1 8 5Z"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0"/><path d="M12 18v3"/><path d="M9 21h6"/>',
    volume: '<path d="M11 5 6 9H3v6h3l5 4V5Z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/><path d="M18 6a8 8 0 0 1 0 12"/>',
    volumeOff: '<path d="M11 5 6 9H3v6h3l5 4V5Z"/><path d="m16 9 5 6"/><path d="m21 9-5 6"/>',
    star: '<path d="m12 3 2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3Z"/>',
    trophy: '<path d="M8 4h8v5a4 4 0 0 1-8 0V4Z"/><path d="M8 6H4v2a4 4 0 0 0 4 4"/><path d="M16 6h4v2a4 4 0 0 1-4 4"/><path d="M12 13v4"/><path d="M8 21h8"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M19 12a7 7 0 0 0-.1-1l2-1.5-2-3.4-2.3 1a7 7 0 0 0-1.7-1L14.5 3h-5l-.4 2.1a7 7 0 0 0-1.7 1l-2.3-1-2 3.4L5.1 11a7 7 0 0 0 0 2l-2 1.5 2 3.4 2.3-1a7 7 0 0 0 1.7 1l.4 2.1h5l.4-2.1a7 7 0 0 0 1.7-1l2.3 1 2-3.4-2-1.5a7 7 0 0 0 .1-1Z"/>',
    help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 1-1 1.7v.5"/><path d="M12 17h.01"/>',
    pause: '<path d="M8 5v14"/><path d="M16 5v14"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    layers: '<path d="m12 3 9 5-9 5-9-5 9-5Z"/><path d="m3 13 9 5 9-5"/>',
    zap: '<path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z"/>'
  };

  function icon(name, size) {
    const p = PATHS[name] || PATHS.info;
    const s = size || 18;
    return '<svg class="ic" width="' + s + '" height="' + s + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + p + '</svg>';
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function fmtDate(str) {
    if (!str) return '';
    return String(str);
  }

  function typeLabel(t) {
    return { judge: '判断题', single: '单选题', multi: '多选题', short: '简答题' }[t] || t;
  }

  function initials(name) {
    if (!name) return '?';
    return String(name).trim().slice(0, 1);
  }

  function avatarHtml(name, cls, src) {
    if (src) return '<span class="avatar ' + (cls || '') + '"><img src="' + esc(src) + '" alt=""></span>';
    return '<span class="avatar ' + (cls || '') + '">' + esc(initials(name)) + '</span>';
  }

  function uid(prefix) {
    return (prefix || 'id') + '-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 6);
  }

  /* ---------------- Toast ---------------- */
  let toastWrap = null;
  function toast(msg, type) {
    if (!toastWrap) {
      toastWrap = document.createElement('div');
      toastWrap.className = 'toast-wrap';
      document.body.appendChild(toastWrap);
    }
    const t = document.createElement('div');
    t.className = 'toast ' + (type || 'ok');
    const ic = type === 'err' ? 'alert' : (type === 'info' ? 'info' : 'check');
    t.innerHTML = icon(ic, 17) + '<span>' + esc(msg) + '</span>';
    toastWrap.appendChild(t);
    setTimeout(function () {
      t.style.transition = 'opacity .2s, transform .2s';
      t.style.opacity = '0';
      t.style.transform = 'translateY(-6px)';
      setTimeout(function () { t.remove(); }, 220);
    }, 2400);
  }

  /* ---------------- Modal ---------------- */
  function modal(opts) {
    opts = opts || {};
    const ov = document.createElement('div');
    ov.className = 'overlay';
    const acts = (opts.actions || []).map(function (a, i) {
      return '<button class="btn ' + (a.cls || 'btn-ghost') + '" data-act="' + i + '">' + (a.icon ? icon(a.icon, 15) : '') + esc(a.label) + '</button>';
    }).join('');
    ov.innerHTML =
      '<div class="modal ' + (opts.wide ? 'wide' : '') + '" role="dialog" aria-modal="true">' +
        '<div class="modal-head">' +
          '<div><h3>' + esc(opts.title || '') + '</h3>' + (opts.subtitle ? '<p>' + esc(opts.subtitle) + '</p>' : '') + '</div>' +
          '<button class="x-btn" data-close aria-label="关闭">' + icon('x', 16) + '</button>' +
        '</div>' +
        '<div class="modal-body">' + (opts.body || '') + '</div>' +
        (acts ? '<div class="modal-foot">' + acts + '</div>' : '') +
      '</div>';
    document.body.appendChild(ov);
    document.body.style.overflow = 'hidden';

    function close() {
      ov.style.opacity = '0';
      ov.style.transition = 'opacity .16s';
      setTimeout(function () {
        ov.remove();
        if (!document.querySelector('.overlay')) document.body.style.overflow = '';
      }, 160);
      if (opts.onClose) opts.onClose();
    }

    ov.addEventListener('click', function (e) {
      if (e.target === ov) { close(); return; }
      if (e.target.closest('[data-close]')) { close(); return; }
      const btn = e.target.closest('[data-act]');
      if (btn) {
        const act = opts.actions[Number(btn.getAttribute('data-act'))];
        if (act && act.onClick) {
          const keep = act.onClick(ov, close);
          if (keep !== false) close();
        } else close();
      }
    });
    const onKey = function (e) { if (e.key === 'Escape') { close(); document.removeEventListener('keydown', onKey); } };
    document.addEventListener('keydown', onKey);
    return { el: ov, close: close };
  }

  function confirm(title, text, onOk, okLabel, danger) {
    return modal({
      title: title,
      body: '<p style="font-size:13.5px;color:var(--ink-2);line-height:1.75">' + text + '</p>',
      actions: [
        { label: '取消', cls: 'btn-ghost' },
        { label: okLabel || '确认', cls: danger ? 'btn-danger' : 'btn-primary', onClick: function () { if (onOk) onOk(); } }
      ]
    });
  }

  /* ---------------- 图表 ---------------- */
  function lineChart(labels, series, opts) {
    opts = opts || {};
    const w = 640, h = 210, pl = 38, pr = 16, pt = 16, pb = 28;
    const all = [];
    series.forEach(function (s) { s.data.forEach(function (v) { all.push(v); }); });
    let max = opts.max || Math.max.apply(null, all.concat([1]));
    max = Math.ceil(max / 10) * 10 || 10;
    const iw = w - pl - pr, ih = h - pt - pb;
    const step = labels.length > 1 ? iw / (labels.length - 1) : iw;
    const yOf = function (v) { return pt + ih * (1 - v / max); };
    const xOf = function (i) { return pl + step * i; };

    let g = '';
    for (let i = 0; i <= 4; i++) {
      const y = pt + ih * i / 4;
      const val = Math.round(max * (1 - i / 4));
      g += '<line x1="' + pl + '" y1="' + y + '" x2="' + (w - pr) + '" y2="' + y + '" stroke="#EEF0F6" stroke-width="1"/>';
      g += '<text x="' + (pl - 8) + '" y="' + (y + 4) + '" text-anchor="end" font-size="10" fill="#98A2B3">' + val + '</text>';
    }
    labels.forEach(function (lb, i) {
      g += '<text x="' + xOf(i) + '" y="' + (h - 8) + '" text-anchor="middle" font-size="10" fill="#98A2B3">' + esc(lb) + '</text>';
    });

    series.forEach(function (s, si) {
      const pts = s.data.map(function (v, i) { return xOf(i) + ',' + yOf(v); }).join(' ');
      if (si === 0) {
        const area = 'M' + xOf(0) + ',' + (pt + ih) + ' L' + s.data.map(function (v, i) { return xOf(i) + ',' + yOf(v); }).join(' L') + ' L' + xOf(s.data.length - 1) + ',' + (pt + ih) + ' Z';
        g += '<defs><linearGradient id="lg' + si + '" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + (s.color || '#4F46E5') + '" stop-opacity=".20"/><stop offset="1" stop-color="' + (s.color || '#4F46E5') + '" stop-opacity="0"/></linearGradient></defs>';
        g += '<path d="' + area + '" fill="url(#lg' + si + ')"/>';
      }
      g += '<polyline points="' + pts + '" fill="none" stroke="' + (s.color || '#4F46E5') + '" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>';
      s.data.forEach(function (v, i) {
        g += '<circle cx="' + xOf(i) + '" cy="' + yOf(v) + '" r="3.4" fill="#fff" stroke="' + (s.color || '#4F46E5') + '" stroke-width="2"/>';
      });
    });
    return '<svg viewBox="0 0 ' + w + ' ' + h + '" style="width:100%;height:auto;display:block">' + g + '</svg>';
  }

  function barChart(labels, values, opts) {
    opts = opts || {};
    const w = 640, h = 210, pl = 38, pr = 16, pt = 16, pb = 34;
    const max = opts.max || Math.max.apply(null, values.concat([1]));
    const iw = w - pl - pr, ih = h - pt - pb;
    const bw = Math.min(46, iw / values.length * 0.5);
    const gap = iw / values.length;
    let g = '';
    for (let i = 0; i <= 4; i++) {
      const y = pt + ih * i / 4;
      g += '<line x1="' + pl + '" y1="' + y + '" x2="' + (w - pr) + '" y2="' + y + '" stroke="#EEF0F6"/>';
      g += '<text x="' + (pl - 8) + '" y="' + (y + 4) + '" text-anchor="end" font-size="10" fill="#98A2B3">' + Math.round(max * (1 - i / 4)) + '</text>';
    }
    values.forEach(function (v, i) {
      const x = pl + gap * i + (gap - bw) / 2;
      const bh = ih * (v / max);
      const y = pt + ih - bh;
      const color = opts.colors ? opts.colors[i] : '#4F46E5';
      g += '<rect x="' + x + '" y="' + y + '" width="' + bw + '" height="' + bh + '" rx="7" fill="' + color + '" opacity=".9"/>';
      g += '<text x="' + (x + bw / 2) + '" y="' + (y - 7) + '" text-anchor="middle" font-size="10.5" font-weight="700" fill="#2A3142">' + v + (opts.suffix || '') + '</text>';
      g += '<text x="' + (x + bw / 2) + '" y="' + (h - 10) + '" text-anchor="middle" font-size="10" fill="#98A2B3">' + esc(labels[i]) + '</text>';
    });
    return '<svg viewBox="0 0 ' + w + ' ' + h + '" style="width:100%;height:auto;display:block">' + g + '</svg>';
  }

  function radarChart(labels, values, opts) {
    opts = opts || {};
    const w = 340, h = 260, cx = 170, cy = 128, R = 76;
    const n = labels.length;
    const ang = function (i) { return -Math.PI / 2 + i * 2 * Math.PI / n; };
    const pt = function (i, r) { return [cx + Math.cos(ang(i)) * r, cy + Math.sin(ang(i)) * r]; };
    let g = '';
    [0.25, 0.5, 0.75, 1].forEach(function (k) {
      const pts = [];
      for (let i = 0; i < n; i++) { const p = pt(i, R * k); pts.push(p[0].toFixed(1) + ',' + p[1].toFixed(1)); }
      g += '<polygon points="' + pts.join(' ') + '" fill="none" stroke="#E7E9F2" stroke-width="1"/>';
    });
    for (let i = 0; i < n; i++) {
      const p = pt(i, R);
      g += '<line x1="' + cx + '" y1="' + cy + '" x2="' + p[0] + '" y2="' + p[1] + '" stroke="#EEF0F6"/>';
      const lp = pt(i, R + 24);
      const anchor = Math.abs(lp[0] - cx) < 6 ? 'middle' : (lp[0] > cx ? 'start' : 'end');
      g += '<text x="' + lp[0] + '" y="' + (lp[1] + 4) + '" text-anchor="' + anchor + '" font-size="10.5" fill="#6B7280">' + esc(labels[i]) + '</text>';
    }
    const pts = values.map(function (v, i) { const p = pt(i, R * Math.max(0, Math.min(100, v)) / 100); return p[0].toFixed(1) + ',' + p[1].toFixed(1); });
    g += '<polygon points="' + pts.join(' ') + '" fill="rgba(79,70,229,.16)" stroke="#4F46E5" stroke-width="2"/>';
    values.forEach(function (v, i) {
      const p = pt(i, R * Math.max(0, Math.min(100, v)) / 100);
      g += '<circle cx="' + p[0] + '" cy="' + p[1] + '" r="3.2" fill="#fff" stroke="#4F46E5" stroke-width="2"/>';
    });
    return '<svg viewBox="0 0 ' + w + ' ' + h + '" style="width:100%;max-width:340px;height:auto;display:block;margin:0 auto">' + g + '</svg>';
  }

  /* ---------------- 小工具 ---------------- */
  function progressHtml(pct, cls) {
    return '<div class="progress ' + (cls || '') + '"><i style="width:' + Math.max(0, Math.min(100, pct)) + '%"></i></div>';
  }

  function emptyHtml(title, desc, ic) {
    return '<div class="empty"><div class="em-ic">' + icon(ic || 'info', 26) + '</div><h4>' + esc(title) + '</h4><p class="small">' + esc(desc || '') + '</p></div>';
  }

  function scoreColor(v) {
    if (v >= 80) return '#12B76A';
    if (v >= 60) return '#4F46E5';
    if (v >= 40) return '#F79009';
    return '#F04438';
  }

  window.UI = {
    icon: icon, esc: esc, $: $, $$: $$,
    toast: toast, modal: modal, confirm: confirm,
    lineChart: lineChart, barChart: barChart, radarChart: radarChart,
    fmtDate: fmtDate, typeLabel: typeLabel, initials: initials, avatarHtml: avatarHtml,
    uid: uid, progressHtml: progressHtml, emptyHtml: emptyHtml, scoreColor: scoreColor
  };
})();
