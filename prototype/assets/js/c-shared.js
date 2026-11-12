/* =========================================================
   云枢智学 · C 端共享逻辑
   ========================================================= */
(function () {
  'use strict';

  const C = {};

  /* ---------------- 时间 ---------------- */
  function pad(n) { return String(n).padStart(2, '0'); }
  function nowStr() {
    const d = new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }
  function todayStr() {
    const d = new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  /* ---------------- 用户 ---------------- */
  C.user = function () { return Store.get('user', null); };
  C.setUser = function (u) { Store.set('user', u); };
  C.logout = function () {
    Store.del('user');
    location.href = 'login.html';
  };

  C.init = function (active) {
    DATA.seedUserState();
    const u = C.user();
    if (!u) { location.replace('login.html'); return null; }
    C.renderHeader(active);
    return u;
  };

  /* ---------------- 导航 ---------------- */
  C.renderHeader = function (active) {
    const host = document.getElementById('app-header');
    if (!host) return;
    const u = C.user();
    const route = u && u.mainRoute ? DATA.findRoute(u.mainRoute) : null;
    let currentStage = null;
    if (route) currentStage = C.currentStage(route);
    const quizHref = currentStage ? ('quiz.html?stage=' + currentStage.id) : 'routes.html';

    const items = [
      { key: 'home', label: '首页', href: 'home.html', icon: 'home' },
      { key: 'routes', label: '学习路线', href: 'routes.html', icon: 'map' },
      { key: 'quiz', label: '刷题', href: quizHref, icon: 'edit' },
      { key: 'interview', label: 'AI 面试', href: 'interview.html', icon: 'sparkles' },
      { key: 'records', label: '记录', href: 'records.html', icon: 'clock' }
    ];

    host.innerHTML =
      '<header class="topnav"><div class="nav-inner">' +
        '<a class="brand" href="home.html"><span class="brand-mark">云</span><span>云枢智学</span></a>' +
        '<nav class="nav-links">' +
          items.map(function (it) {
            return '<a href="' + it.href + '" class="' + (active === it.key ? 'active' : '') + '">' + UI.icon(it.icon, 16) + it.label + '</a>';
          }).join('') +
        '</nav>' +
        '<div class="nav-user">' +
          '<div class="user-chip" id="userChip">' + UI.avatarHtml(u ? u.nickname : '?', 'sm', u && u.avatar) +
            '<span class="semibold" style="font-size:13px">' + UI.esc(u ? u.nickname : '') + '</span>' +
            UI.icon('chevronDown', 14) +
          '</div>' +
          '<div class="user-menu" id="userMenu">' +
            '<a href="profile.html">' + UI.icon('user', 15) + '个人中心</a>' +
            '<a href="wrongbook.html">' + UI.icon('book', 15) + '我的错题本</a>' +
            '<a href="records.html">' + UI.icon('clock', 15) + '学习与面试记录</a>' +
            '<button class="danger" id="logoutBtn">' + UI.icon('logout', 15) + '退出登录</button>' +
          '</div>' +
        '</div>' +
      '</div></header>';

    const chip = document.getElementById('userChip');
    const menu = document.getElementById('userMenu');
    if (chip) {
      chip.addEventListener('click', function (e) {
        e.stopPropagation();
        menu.classList.toggle('open');
      });
      document.addEventListener('click', function () { menu.classList.remove('open'); });
    }
    const lb = document.getElementById('logoutBtn');
    if (lb) lb.addEventListener('click', function () {
      UI.confirm('退出登录', '退出后需要重新登录才能继续学习，确定退出吗？', function () { C.logout(); }, '退出登录', true);
    });
  };

  /* ---------------- 内容查询 ---------------- */
  C.route = function () {
    const u = C.user();
    return u && u.mainRoute ? DATA.findRoute(u.mainRoute) : null;
  };
  C.subject = function (route) {
    const r = route || C.route();
    if (!r) return null;
    return DATA.content().subjects.find(function (s) { return s.id === r.subjectId; });
  };
  C.routeOfSubject = function (subjectId) {
    return DATA.content().routes.find(function (r) { return r.subjectId === subjectId; });
  };
  C.currentStage = function (route) {
    if (!route) return null;
    for (const s of route.stages) {
      const st = C.stageStats(s.id);
      if (st.done < st.total) return s;
    }
    return route.stages[route.stages.length - 1];
  };

  /* ---------------- 课程进度 ---------------- */
  C.progress = function () { return Store.get('progress', {}); };
  C.isCompleted = function (courseId) {
    const p = C.progress()[courseId];
    return !!(p && p.completedAt);
  };
  C.isStarted = function (courseId) {
    const p = C.progress()[courseId];
    return !!(p && p.startedAt);
  };
  C.markStarted = function (courseId) {
    const p = C.progress();
    if (!p[courseId]) p[courseId] = { startedAt: nowStr(), completedAt: null };
    Store.set('progress', p);
  };
  C.markCompleted = function (courseId) {
    const p = C.progress();
    if (!p[courseId]) p[courseId] = { startedAt: nowStr(), completedAt: null };
    p[courseId].completedAt = nowStr();
    Store.set('progress', p);
  };
  C.unmarkCompleted = function (courseId) {
    const p = C.progress();
    if (p[courseId]) { p[courseId].completedAt = null; Store.set('progress', p); }
  };
  C.stageStats = function (stageId) {
    const found = DATA.findStage(stageId);
    if (!found) return { total: 0, done: 0, pct: 0 };
    const total = found.stage.courses.length;
    const done = found.stage.courses.filter(function (c) { return C.isCompleted(c.id); }).length;
    return { total: total, done: done, pct: total ? Math.round(done / total * 100) : 0 };
  };
  C.routeStats = function (routeId) {
    const route = DATA.findRoute(routeId);
    if (!route) return { total: 0, done: 0, pct: 0 };
    let total = 0, done = 0;
    route.stages.forEach(function (s) {
      s.courses.forEach(function (c) {
        total++;
        if (C.isCompleted(c.id)) done++;
      });
    });
    return { total: total, done: done, pct: total ? Math.round(done / total * 100) : 0 };
  };
  C.nextCourse = function (routeId) {
    const route = DATA.findRoute(routeId);
    if (!route) return null;
    for (const s of route.stages) {
      for (const c of s.courses) {
        if (!C.isCompleted(c.id)) return { course: c, stage: s, route: route };
      }
    }
    return null;
  };

  /* ---------------- 练习 ---------------- */
  C.practices = function () { return Store.get('practices', []); };
  C.addPractice = function (rec) {
    const list = C.practices();
    list.unshift(rec);
    Store.set('practices', list.slice(0, 200));
  };
  C.mastery = function (stageId) {
    const list = C.practices().filter(function (p) { return p.stageId === stageId && p.type === 'stage'; });
    if (!list.length) return null;
    const latest = list[0];
    return {
      accuracy: latest.accuracy,
      total: latest.total,
      at: latest.at,
      sample: latest.total < 10,
      history: list.slice(0, 5).map(function (p) { return p.accuracy; }).reverse()
    };
  };

  /* ---------------- 错题本 ---------------- */
  C.wrong = function () { return Store.get('wrong', {}); };
  C.addWrong = function (qid, meta) {
    const w = C.wrong();
    if (!w[qid] || !w[qid].active) w[qid] = Object.assign({ addedAt: nowStr(), active: true }, meta || {});
    Store.set('wrong', w);
  };
  C.removeWrong = function (qid) {
    const w = C.wrong();
    if (w[qid]) { w[qid].active = false; w[qid].removedAt = nowStr(); }
    Store.set('wrong', w);
  };
  C.wrongList = function (filter) {
    const w = C.wrong();
    const out = [];
    Object.keys(w).forEach(function (qid) {
      if (!w[qid].active) return;
      const f = DATA.findQuestion(qid);
      if (!f) return;
      if (filter && filter.subjectId && f.route.subjectId !== filter.subjectId) return;
      if (filter && filter.stageId && f.stage.id !== filter.stageId) return;
      out.push({ q: f.question, stage: f.stage, route: f.route, meta: w[qid] });
    });
    return out;
  };

  /* ---------------- 面试 ---------------- */
  C.interviews = function () { return Store.get('interviews', []); };
  C.findInterview = function (id) {
    return C.interviews().find(function (i) { return i.id === id; });
  };
  C.saveInterview = function (iv) {
    const list = C.interviews();
    const i = list.findIndex(function (x) { return x.id === iv.id; });
    if (i >= 0) list[i] = iv; else list.unshift(iv);
    Store.set('interviews', list);
  };
  C.ongoingInterview = function () {
    return C.interviews().find(function (i) { return i.status === 'ongoing'; }) || null;
  };

  /* ---------------- 判分 ---------------- */
  function letter(i) { return String.fromCharCode(65 + i); }
  C.answerText = function (q, ans) {
    if (q.type === 'short') return ans || '';
    return (ans || []).slice().sort(function (a, b) { return a - b; }).map(function (i) { return letter(i); }).join('、') || '未作答';
  };

  C.judge = function (q, ans) {
    if (q.type === 'short') {
      const text = String(ans || '').trim();
      let score = 0;
      const hits = (q.points || []).map(function (p) {
        const hit = !!text && p.keywords.some(function (k) { return text.toLowerCase().indexOf(String(k).toLowerCase()) >= 0; });
        if (hit) score += p.score;
        return { text: p.text, hit: hit, score: p.score };
      });
      return { correct: score >= 60, score: score, hits: hits, reference: q.reference, degraded: false };
    }
    const a = (ans || []).slice().sort(function (x, y) { return x - y; }).join(',');
    const b = (q.answer || []).slice().sort(function (x, y) { return x - y; }).join(',');
    return { correct: a === b, score: a === b ? 100 : 0, hits: [], reference: '' };
  };

  /* ---------------- 小工具 ---------------- */
  C.nowStr = nowStr;
  C.todayStr = todayStr;
  C.gradient = function (subject) {
    if (!subject) return 'linear-gradient(135deg,#4F46E5,#7C3AED)';
    return 'linear-gradient(135deg,' + subject.color + ',' + subject.color2 + ')';
  };
  C.stageHref = function (stageId) { return 'stage.html?id=' + encodeURIComponent(stageId); };
  C.routeHref = function (routeId) { return 'route.html?id=' + encodeURIComponent(routeId); };

  window.C = C;
})();
