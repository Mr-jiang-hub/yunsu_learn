/* =========================================================
   云枢智学 · B 端共享逻辑
   ========================================================= */
(function () {
  'use strict';

  const A = {};

  function pad(n) { return String(n).padStart(2, '0'); }
  function nowStr() {
    const d = new Date();
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  A.session = function () {
    let s = Store.get('adminSession', null);
    if (!s) {
      s = { name: '周老师', account: 'zhou', role: '超级管理员' };
      Store.set('adminSession', s);
    }
    return s;
  };
  A.setSession = function (s) { Store.set('adminSession', s); };
  A.logout = function () {
    Store.del('adminSession');
    location.href = 'login.html';
  };

  A.data = function () { return DATA.content(); };
  A.save = function (d) { DATA.saveContent(d); };

  A.log = function (module, action, target) {
    const d = A.data();
    const s = A.session();
    d.logs.unshift({
      id: UI.uid('log'),
      who: s.role === '超级管理员' ? '超级管理员' : s.role,
      module: module,
      action: action,
      target: target || '',
      time: nowStr()
    });
    d.logs = d.logs.slice(0, 200);
    A.save(d);
  };

  A.renderShell = function (active, title, sub) {
    const s = A.session();
    const side = document.getElementById('app-side');
    const top = document.getElementById('app-top');
    if (side) {
      const items = [
        { key: 'dashboard', label: '数据看板', href: 'dashboard.html', icon: 'chart' },
        { key: 'content', label: '内容管理', href: 'content.html', icon: 'layers' },
        { key: 'paths', label: '学习路线', href: 'paths.html', icon: 'map' },
        { key: 'students', label: '学员管理', href: 'students.html', icon: 'users' },
        { key: 'users', label: '权限管理', href: 'users.html', icon: 'shield' },
        { key: 'logs', label: '操作日志', href: 'logs.html', icon: 'file' }
      ];
      // 按角色模块权限过滤菜单
      var need = {
        dashboard: ['数据看板'],
        content: ['学科管理', '课程库', '刷题题库', '面试设置'],
        paths: ['学习路线'],
        students: ['学员管理'],
        users: ['系统用户', '角色管理'],
        logs: ['操作日志']
      };
      var role = A.data().roles.find(function (r) { return r.name === s.role; });
      var allowed = role ? items.filter(function (it) {
        return (need[it.key] || []).some(function (m) { return role.modules.indexOf(m) >= 0; });
      }) : items;
      if (!allowed.length) allowed = items.filter(function (it) { return it.key === 'dashboard'; });
      side.innerHTML =
        '<div class="side-brand"><span class="brand-mark">云</span><span>云枢智学 · 运营台</span></div>' +
        '<div class="side-sec">运营</div>' +
        allowed.map(function (it) {
          return '<a class="side-link ' + (active === it.key ? 'active' : '') + '" href="' + it.href + '">' + UI.icon(it.icon, 17) + '<span>' + it.label + '</span></a>';
        }).join('') +
        '<div class="side-foot"><b>演示环境</b>数据保存在本地浏览器，仅用于交互演示。</div>';
    }
    if (top) {
      top.innerHTML =
        '<div><div class="tb-title">' + UI.esc(title || '') + '</div>' + (sub ? '<div class="tb-sub">' + UI.esc(sub) + '</div>' : '') + '</div>' +
        '<div class="row" style="gap:12px">' +
          '<a class="btn btn-sm btn-ghost" href="../c/home.html">' + UI.icon('arrowRight', 14) + '返回学员端</a>' +
          '<div class="row" style="gap:8px">' + UI.avatarHtml(s.name, 'sm') +
            '<div><div style="font-size:12.5px;font-weight:600;line-height:1.2">' + UI.esc(s.name) + '</div>' +
            '<div style="font-size:10.5px;color:var(--muted)">' + UI.esc(s.role) + '</div></div></div>' +
          '<button class="btn btn-sm btn-ghost" id="adminLogout" title="退出登录">' + UI.icon('logout', 15) + '</button>' +
        '</div>';
      const lb = document.getElementById('adminLogout');
      if (lb) lb.addEventListener('click', function () {
        UI.confirm('退出登录', '确定退出运营后台吗？', function () { A.logout(); }, '退出', true);
      });
    }
  };

  /* 生成表格行时的辅助 */
  A.subjectOf = function (routeId) {
    const d = A.data();
    const r = d.routes.find(function (x) { return x.id === routeId; });
    if (!r) return null;
    return d.subjects.find(function (s) { return s.id === r.subjectId; });
  };
  A.routeName = function (routeId) {
    const r = A.data().routes.find(function (x) { return x.id === routeId; });
    return r ? r.name : '—';
  };
  A.questionCount = function () {
    let n = 0;
    A.data().routes.forEach(function (r) { r.stages.forEach(function (s) { n += s.questions.length; }); });
    return n;
  };
  A.courseCount = function () {
    let n = 0;
    A.data().routes.forEach(function (r) { r.stages.forEach(function (s) { n += s.courses.length; }); });
    return n;
  };
  A.stageCount = function () {
    let n = 0;
    A.data().routes.forEach(function (r) { n += r.stages.length; });
    return n;
  };

  window.AdminApp = A;
})();
