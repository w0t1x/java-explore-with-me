
const App = (() => {
  const STORAGE_KEYS = {
    users: 'ewm-users',
    session: 'ewm-session',
    favorites: 'ewm-favorites'
  };

  const DEFAULT_LOCATION = { lat: 55.751244, lon: 37.618423 };

  const state = {
    users: loadJson(STORAGE_KEYS.users, []),
    session: loadJson(STORAGE_KEYS.session, { role: 'guest', userId: null, name: '', email: '' }),
    favorites: loadJson(STORAGE_KEYS.favorites, [])
  };

  function $(selector, root = document) { return root.querySelector(selector); }
  function $all(selector, root = document) { return Array.from(root.querySelectorAll(selector)); }

  function loadJson(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback)); }
    catch { return fallback; }
  }
  function saveJson(key, value) { localStorage.setItem(key, JSON.stringify(value)); }

  function ensureSeedData() {
    const users = loadJson(STORAGE_KEYS.users, []);
    if (!users.some((u) => u.role === 'admin' && String(u.email).toLowerCase() === 'admin@ewm.local')) {
      users.push({
        id: 'admin-local',
        backendUserId: null,
        name: 'Администратор',
        email: 'admin@ewm.local',
        password: 'admin123',
        role: 'admin'
      });
      saveJson(STORAGE_KEYS.users, users);
    }
    state.users = users;
  }

  ensureSeedData();

  function saveUsers(users) {
    state.users = users;
    saveJson(STORAGE_KEYS.users, users);
  }

  function saveSession(session) {
    state.session = session;
    saveJson(STORAGE_KEYS.session, session);
    syncHeader();
    window.dispatchEvent(new CustomEvent('session:changed'));
  }

  function clearSession() {
    saveSession({ role: 'guest', userId: null, name: '', email: '' });
  }

  function currentSession() { return state.session; }
  function currentUser() { return state.users.find((u) => String(u.id) === String(state.session.userId)) || null; }
  function isAuthenticated() { return currentSession().role !== 'guest'; }
  function isAdmin() { return currentSession().role === 'admin'; }

  function loadFavorites() { return loadJson(STORAGE_KEYS.favorites, []); }
  function saveFavorites(list) {
    state.favorites = list;
    saveJson(STORAGE_KEYS.favorites, list);
    window.dispatchEvent(new CustomEvent('favorites:changed'));
  }

  function showEmpty(target, text) {
    if (target) target.innerHTML = `<div class="empty-state">${text}</div>`;
  }

  function setText(selector, text) {
    const el = typeof selector === 'string' ? $(selector) : selector;
    if (el) el.textContent = text;
  }

  function setHtml(selector, html) {
    const el = typeof selector === 'string' ? $(selector) : selector;
    if (el) el.innerHTML = html;
  }

  async function apiFetch(url, options = {}) {
    const response = await fetch(url, {
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options
    });
    const raw = await response.text();
    let data;
    try { data = raw ? JSON.parse(raw) : null; } catch { data = raw; }
    if (!response.ok) {
      const message = data?.message || data?.reason || response.statusText;
      throw new Error(message);
    }
    return data;
  }

  function formatDate(value) {
    if (!value) return '—';
    const normalized = String(value).replace(' ', 'T');
    const date = new Date(normalized);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString('ru-RU', {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  function formatStatsDate(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  function normalizeDateTimeLocal(value) {
    if (!value) return '';
    const raw = String(value).trim().replace('T', ' ');
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(raw)) return `${raw}:00`;
    return raw;
  }

  function shortText(value, limit = 140) {
    if (!value) return 'Описание пока не заполнено.';
    const text = String(value);
    return text.length > limit ? `${text.slice(0, limit).trim()}…` : text;
  }

  function getCategoryName(event) { return event?.category?.name || event?.category || 'Событие'; }
  function getInitiatorName(event) { return event?.initiator?.name || event?.initiator || 'Организатор не указан'; }
  function eventUri(id) { return `/events/${id}`; }

  function isFavorite(eventId) {
    return loadFavorites().some((item) => String(item.id) === String(eventId));
  }

  function toggleFavorite(event) {
    if (!isAuthenticated()) {
      openAuthModal('login');
      return;
    }
    const list = loadFavorites();
    const exists = list.some((item) => String(item.id) === String(event.id));
    const next = exists
      ? list.filter((item) => String(item.id) !== String(event.id))
      : [{
          id: event.id,
          title: event.title,
          eventDate: event.eventDate,
          annotation: event.annotation || event.description,
          views: event.views,
          category: getCategoryName(event)
        }, ...list];
    saveFavorites(next);
  }

  function syncHeader() {
    const session = currentSession();
    $('#roleLabel') && ($('#roleLabel').textContent =
      session.role === 'admin' ? 'Администратор' :
      session.role === 'user' ? 'Пользователь' : 'Гость');

    $('#openAuthBtn')?.classList.toggle('hidden', session.role !== 'guest');
    $('#profileBox')?.classList.toggle('hidden', session.role === 'guest');
    $('#cabinetLink')?.classList.toggle('hidden', !['user', 'admin'].includes(session.role));
    $('#adminLink')?.classList.toggle('hidden', session.role !== 'admin');
    $('#profileName') && ($('#profileName').textContent = session.name || (session.role === 'admin' ? 'Администратор' : 'Пользователь'));
  }

  function openAuthModal(mode = 'login', forcedRole = null) {
    const modal = $('#authModal');
    if (!modal) return;
    modal.classList.remove('hidden');
    setAuthMode(mode);
    const roleInput = $('#loginRole');
    if (forcedRole && roleInput) roleInput.value = forcedRole;
  }

  function closeAuthModal() { $('#authModal')?.classList.add('hidden'); }

  function setAuthMode(mode) {
    const loginTab = $('#authModeLogin');
    const registerTab = $('#authModeRegister');
    const loginForm = $('#loginForm');
    const registerForm = $('#registerForm');
    if (loginTab) loginTab.classList.toggle('active', mode === 'login');
    if (registerTab) registerTab.classList.toggle('active', mode === 'register');
    if (loginForm) loginForm.classList.toggle('hidden', mode !== 'login');
    if (registerForm) registerForm.classList.toggle('hidden', mode !== 'register');
  }

  function getNextLocalId() {
    const nums = state.users
      .filter((u) => /^local-\d+$/.test(String(u.id)))
      .map((u) => Number(String(u.id).replace('local-', '')));
    return `local-${(nums.length ? Math.max(...nums) : 0) + 1}`;
  }

  async function registerUser({ name, email, password }) {
    const normalizedEmail = email.trim().toLowerCase();
    const existingByEmail = state.users.find((u) => String(u.email).toLowerCase() === normalizedEmail);
    if (existingByEmail) throw new Error('Пользователь с таким email уже зарегистрирован.');

    const backendUser = await apiFetch('/api/main/admin/users', {
      method: 'POST',
      body: JSON.stringify({ name, email: normalizedEmail })
    });

    const user = {
      id: getNextLocalId(),
      backendUserId: backendUser.id,
      name,
      email: normalizedEmail,
      password,
      role: 'user'
    };
    const users = [...state.users, user];
    saveUsers(users);
    saveSession({ role: 'user', userId: user.id, name: user.name, email: user.email });
    return user;
  }

  function loginUser({ email, password, role = 'user' }) {
    const normalizedEmail = email.trim().toLowerCase();
    const user = state.users.find((u) => String(u.email).toLowerCase() === normalizedEmail && u.role === role);
    if (!user || user.password !== password) throw new Error('Неверный email или пароль.');
    saveSession({ role: user.role, userId: user.id, name: user.name, email: user.email });
    return user;
  }

  function attachAuthHandlers() {
    $('#openAuthBtn')?.addEventListener('click', () => openAuthModal('login'));
    $('#heroLoginBtn')?.addEventListener('click', () => openAuthModal('login'));
    $('#guestLoginCta')?.addEventListener('click', () => openAuthModal('login'));
    $('#logoutBtn')?.addEventListener('click', () => {
      clearSession();
      if (location.pathname === '/profile' || location.pathname.endsWith('/user.html') || location.pathname === '/admin' || location.pathname.endsWith('/admin.html')) {
        location.href = '/';
      } else {
        syncHeader();
      }
    });
    $('#closeAuthBtn')?.addEventListener('click', closeAuthModal);
    $('#closeAuthBackdrop')?.addEventListener('click', closeAuthModal);
    $('#authModeLogin')?.addEventListener('click', () => setAuthMode('login'));
    $('#authModeRegister')?.addEventListener('click', () => setAuthMode('register'));

    $('#loginForm')?.addEventListener('submit', (event) => {
      event.preventDefault();
      const email = $('#loginEmail')?.value.trim();
      const password = $('#loginPassword')?.value;
      const role = $('#loginRole')?.value || 'user';
      if (!email || !password) return alert('Заполните email и пароль.');
      try {
        const user = loginUser({ email, password, role });
        closeAuthModal();
        location.href = user.role === 'admin' ? '/admin' : '/profile';
      } catch (error) {
        alert(error.message);
      }
    });

    $('#registerForm')?.addEventListener('submit', async (event) => {
      event.preventDefault();
      const name = $('#registerName')?.value.trim();
      const email = $('#registerEmail')?.value.trim();
      const password = $('#registerPassword')?.value;
      const confirm = $('#registerPasswordConfirm')?.value;
      if (!name) return alert('Укажите имя.');
      if (!email) return alert('Укажите email.');
      if (!password) return alert('Укажите пароль.');
      if (password !== confirm) return alert('Пароли не совпадают.');
      try {
        await registerUser({ name, email, password });
        closeAuthModal();
        location.href = '/profile';
      } catch (error) {
        alert(`Не удалось зарегистрироваться: ${error.message}`);
      }
    });

    syncHeader();
  }

  function guardPage(role) {
    const session = currentSession();
    if (role === 'user') return ['user', 'admin'].includes(session.role);
    if (role === 'admin') return session.role === 'admin';
    return true;
  }

  function renderAccessDenied(target, { title, text, ctaText = 'Войти', admin = false }) {
    target.innerHTML = `
      <section class="hero">
        <div class="container">
          <div class="section-card">
            <p class="section-label">Доступ ограничен</p>
            <h1 style="font-size:2.5rem">${title}</h1>
            <p class="lead-text">${text}</p>
            <div class="toolbar" style="margin-top:18px">
              <a href="/" class="secondary-btn">На главную</a>
              <button class="primary-btn" id="accessLoginBtn">${ctaText}</button>
            </div>
          </div>
        </div>
      </section>`;
    $('#accessLoginBtn', target)?.addEventListener('click', () => openAuthModal('login', admin ? 'admin' : 'user'));
  }

  function createEventCard(event, { onOpen, allowFavorite = true, allowRequest = false, onRequest, extraActions = '' } = {}) {
    const node = document.createElement('article');
    node.className = 'event-card';
    node.innerHTML = `
      <div class="event-cover"></div>
      <div class="event-body">
        <div class="event-topline"><span>${getCategoryName(event)}</span><span>Просмотры: ${event.views ?? 0}</span></div>
        <h3 class="event-title">${event.title || 'Без названия'}</h3>
        <p class="event-text">${shortText(event.annotation || event.description, 140)}</p>
        <div class="event-meta">Дата: ${formatDate(event.eventDate)}<br>Формат: ${event.paid ? 'Платное' : 'Бесплатное'}<br>Организатор: ${getInitiatorName(event)}</div>
      </div>
      <div class="event-actions">
        <button class="ghost-btn small" data-open>Подробнее</button>
        ${allowFavorite ? `<button class="ghost-btn small" data-favorite>${isFavorite(event.id) ? 'Убрать' : 'В избранное'}</button>` : ''}
        ${allowRequest ? `<button class="ghost-btn small" data-request>Участвовать</button>` : ''}
        ${extraActions}
      </div>`;
    $('[data-open]', node).addEventListener('click', () => onOpen?.(event.id));
    if (allowFavorite) $('[data-favorite]', node).addEventListener('click', () => toggleFavorite(event));
    if (allowRequest) $('[data-request]', node).addEventListener('click', () => onRequest?.(event));
    return node;
  }

  function createFavoriteCard(item, { onOpen, onRemove } = {}) {
    const card = document.createElement('article');
    card.className = 'favorite-card';
    card.innerHTML = `
      <div class="favorite-body">
        <div class="favorite-topline"><span>${item.category}</span><span>Просмотры: ${item.views ?? 0}</span></div>
        <h3 class="favorite-title">${item.title}</h3>
        <p class="favorite-text">${shortText(item.annotation, 120)}</p>
        <div class="favorite-meta">Дата: ${formatDate(item.eventDate)}</div>
      </div>
      <div class="event-actions">
        <button class="ghost-btn small" data-open>Открыть</button>
        <button class="ghost-btn small" data-remove>Удалить</button>
      </div>`;
    $('[data-open]', card).addEventListener('click', () => onOpen?.(item.id));
    $('[data-remove]', card).addEventListener('click', () => onRemove?.(item.id));
    return card;
  }

  function buildModalRow(label, value) {
    return `<div class="modal-row"><strong>${label}</strong><div>${value ?? '—'}</div></div>`;
  }

  async function sendDemoHit(eventId) {
    const payload = { app: 'ewm-main-service', uri: eventUri(eventId), ip: '127.0.0.1', timestamp: formatStatsDate(new Date()) };
    return apiFetch('/api/stats/hit', { method: 'POST', body: JSON.stringify(payload) });
  }

  async function loadEventStats(eventId) {
    const start = new Date(); start.setHours(0, 0, 0, 0);
    const end = new Date(start); end.setDate(end.getDate() + 1);
    const params = new URLSearchParams({ start: formatStatsDate(start), end: formatStatsDate(end), unique: 'false' });
    params.append('uris', eventUri(eventId));
    const stats = await apiFetch(`/api/stats/stats?${params.toString()}`);
    return Array.isArray(stats) ? stats[0] : null;
  }

  async function requestParticipation(eventId) {
    const user = currentUser();
    if (!user?.backendUserId) throw new Error('Сначала войдите как зарегистрированный пользователь.');
    return apiFetch(`/api/main/users/${user.backendUserId}/requests?eventId=${eventId}`, { method: 'POST' });
  }

  async function loadPublishedComments(eventId) {
    return apiFetch(`/api/main/events/${eventId}/comments?from=0&size=20`);
  }

  async function loadUserComments(userId) {
    return apiFetch(`/api/main/users/${userId}/comments?from=0&size=20`);
  }

  async function createComment(userId, eventId, text) {
    return apiFetch(`/api/main/users/${userId}/comments?eventId=${eventId}`, {
      method: 'POST',
      body: JSON.stringify({ text })
    });
  }

  async function updateComment(userId, commentId, text) {
    return apiFetch(`/api/main/users/${userId}/comments/${commentId}`, {
      method: 'PATCH',
      body: JSON.stringify({ text })
    });
  }

  async function deleteComment(userId, commentId) {
    return apiFetch(`/api/main/users/${userId}/comments/${commentId}`, { method: 'DELETE' });
  }

  async function adminSearchComments(status = 'PENDING', eventId = '') {
    const params = new URLSearchParams({ status, from: '0', size: '20' });
    if (eventId) params.append('eventId', eventId);
    return apiFetch(`/api/main/admin/comments?${params.toString()}`);
  }

  async function moderateComment(commentId, action, rejectionReason = '') {
    const payload = { action };
    if (rejectionReason) payload.rejectionReason = rejectionReason;
    return apiFetch(`/api/main/admin/comments/${commentId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    });
  }

  async function adminDeleteComment(commentId) {
    return apiFetch(`/api/main/admin/comments/${commentId}`, { method: 'DELETE' });
  }

  function renderPublishedCommentsHtml(comments) {
    if (!Array.isArray(comments) || !comments.length) {
      return '<div class="empty-state">Комментариев пока нет.</div>';
    }
    return comments.map((comment) => `
      <div class="list-card">
        <div class="list-topline"><span>${comment.author?.name || 'Пользователь'}</span><span>${formatDate(comment.createdOn)}</span></div>
        <div class="small-text" style="margin-top:10px; white-space:pre-wrap">${comment.text || ''}</div>
      </div>`).join('');
  }

  async function openEventModal(id, options = {}) {
    const { mode = 'guest', onShowStats } = options;
    try {
      const event = await apiFetch(`/api/main/events/${id}`);
      const target = $('#eventModalContent');
      const isUserMode = mode === 'user';
      const isAdminMode = mode === 'admin';
      const user = currentUser();
      const comments = await loadPublishedComments(event.id).catch(() => []);
      const actions = [];
      if (isUserMode) actions.push(`<button class="secondary-btn" id="favoriteModalBtn">${isFavorite(event.id) ? 'Убрать из избранного' : 'Добавить в избранное'}</button>`);
      if (isUserMode) actions.push('<button class="ghost-btn" id="requestModalBtn">Подать заявку</button>');
      if (isAdminMode) actions.push('<button class="ghost-btn" id="hitModalBtn">Записать просмотр</button>');
      if (isAdminMode) actions.push('<button class="primary-btn" id="statsModalBtn">Показать статистику</button>');
      target.innerHTML = `
        <div class="modal-header">
          <p class="section-label">Карточка события</p>
          <h2>${event.title || 'Без названия'}</h2>
          <p class="lead-text">${shortText(event.annotation, 220)}</p>
        </div>
        <div class="modal-grid">
          ${buildModalRow('Категория', getCategoryName(event))}
          ${buildModalRow('Дата события', formatDate(event.eventDate))}
          ${buildModalRow('Создано', formatDate(event.createdOn))}
          ${buildModalRow('Опубликовано', formatDate(event.publishedOn))}
          ${buildModalRow('Платность', event.paid ? 'Платное' : 'Бесплатное')}
          ${buildModalRow('Просмотры', event.views ?? 0)}
          ${buildModalRow('Инициатор', getInitiatorName(event))}
          ${buildModalRow('Лимит участников', event.participantLimit ?? '—')}
        </div>
        <div class="modal-description">${event.description || 'Полное описание пока не заполнено.'}</div>
        ${mode === 'guest' ? '<div class="empty-state">Войдите или зарегистрируйтесь, чтобы подавать заявки, сохранять избранное и оставлять комментарии.</div>' : ''}
        <div class="event-actions">${actions.join('')}</div>
        <div class="divider"></div>
        <div>
          <p class="section-label">Комментарии</p>
          <h3 style="margin-bottom:12px">Комментарии к событию</h3>
          <div id="modalCommentsList" class="list-grid">${renderPublishedCommentsHtml(comments)}</div>
          ${isUserMode && user?.backendUserId ? `
            <form id="modalCommentForm" class="form-grid profile-form" style="margin-top:16px">
              <label class="field field-wide"><span>Новый комментарий</span><textarea id="modalCommentText" placeholder="Напишите комментарий. После отправки он поступит на модерацию."></textarea></label>
              <div class="form-actions" style="grid-column:1/-1"><button class="primary-btn" type="submit">Отправить комментарий</button></div>
            </form>` : ''}
        </div>`;
      if (isUserMode) $('#favoriteModalBtn')?.addEventListener('click', () => { toggleFavorite(event); closeEventModal(); });
      if (isUserMode) $('#requestModalBtn')?.addEventListener('click', async () => {
        try {
          await requestParticipation(event.id);
          alert('Заявка на участие отправлена.');
          closeEventModal();
          window.dispatchEvent(new CustomEvent('requests:changed'));
        } catch (error) {
          alert(`Не удалось отправить заявку: ${error.message}`);
        }
      });
      if (isUserMode) $('#modalCommentForm')?.addEventListener('submit', async (evt) => {
        evt.preventDefault();
        const text = $('#modalCommentText')?.value.trim();
        if (!text) return alert('Введите текст комментария.');
        try {
          await createComment(user.backendUserId, event.id, text);
          alert('Комментарий отправлен на модерацию администратора.');
          closeEventModal();
          window.dispatchEvent(new CustomEvent('comments:changed'));
        } catch (error) {
          alert(`Не удалось добавить комментарий: ${error.message}`);
        }
      });
      if (isAdminMode) $('#hitModalBtn')?.addEventListener('click', async () => {
        try {
          await sendDemoHit(event.id);
          alert('Просмотр события записан в статистику.');
        } catch (error) {
          alert(`Не удалось записать просмотр: ${error.message}`);
        }
      });
      if (isAdminMode) $('#statsModalBtn')?.addEventListener('click', async () => {
        const stat = await loadEventStats(event.id);
        closeEventModal();
        onShowStats?.(stat, event);
      });
      $('#eventModal')?.classList.remove('hidden');
    } catch (error) {
      alert(`Не удалось открыть карточку события: ${error.message}`);
    }
  }

  function closeEventModal() { $('#eventModal')?.classList.add('hidden'); }
  function attachModalHandlers() {
    $('#closeModalBtn')?.addEventListener('click', closeEventModal);
    $('#closeModalBackdrop')?.addEventListener('click', closeEventModal);
  }

  return {
    $, $all, apiFetch, currentSession, currentUser, saveSession, clearSession, isAuthenticated, isAdmin,
    loadFavorites, saveFavorites, isFavorite, toggleFavorite, showEmpty, setText, setHtml, formatDate,
    formatStatsDate, normalizeDateTimeLocal, shortText, getCategoryName, getInitiatorName, eventUri,
    DEFAULT_LOCATION, attachAuthHandlers, openAuthModal, closeAuthModal, guardPage, renderAccessDenied,
    createEventCard, createFavoriteCard, syncHeader, attachModalHandlers, openEventModal,
    loadEventStats, sendDemoHit, requestParticipation, loadPublishedComments, loadUserComments,
    createComment, updateComment, deleteComment, adminSearchComments, moderateComment, adminDeleteComment
  };
})();
