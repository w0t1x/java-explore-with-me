const App = (() => {
  const state = {
    favorites: loadJson('ewm-favorites', []),
    session: loadJson('ewm-session', { role: 'guest', name: '', userId: '' })
  };

  function $(selector, root = document) { return root.querySelector(selector); }
  function $all(selector, root = document) { return Array.from(root.querySelectorAll(selector)); }

  function loadJson(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback)); }
    catch { return fallback; }
  }
  function saveJson(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
  function loadSession() { return state.session; }
  function saveSession(session) { state.session = session; saveJson('ewm-session', session); syncHeader(); }
  function clearSession() { saveSession({ role: 'guest', name: '', userId: '' }); }
  function loadFavorites() { return state.favorites; }
  function saveFavorites(list) { state.favorites = list; saveJson('ewm-favorites', list); }

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
    const date = new Date(String(value).replace(' ', 'T'));
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString('ru-RU', { day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
  function formatStatsDate(date) {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }
  function shortText(value, limit = 140) {
    if (!value) return 'Описание пока не заполнено.';
    const text = String(value);
    return text.length > limit ? `${text.slice(0, limit).trim()}…` : text;
  }
  function getCategoryName(event) { return event?.category?.name || event?.category || 'Событие'; }
  function getInitiatorName(event) { return event?.initiator?.name || event?.initiator || 'Организатор не указан'; }
  function eventUri(id) { return `/events/${id}`; }

  function showEmpty(target, text) { target.innerHTML = `<div class="empty-state">${text}</div>`; }
  function setText(selector, text) { const el = $(selector); if (el) el.textContent = text; }

  function isFavorite(eventId) { return state.favorites.some((item) => String(item.id) === String(eventId)); }
  function toggleFavorite(event) {
    const list = [...state.favorites];
    const exists = list.some((item) => String(item.id) === String(event.id));
    const next = exists
      ? list.filter((item) => String(item.id) !== String(event.id))
      : [{ id: event.id, title: event.title, eventDate: event.eventDate, annotation: event.annotation || event.description, views: event.views, category: getCategoryName(event) }, ...list];
    saveFavorites(next);
    window.dispatchEvent(new CustomEvent('favorites:changed'));
  }

  function renderHeaderSession() {
    const session = loadSession();
    const authBtn = $('#openAuthBtn');
    const profileBox = $('#profileBox');
    const profileName = $('#profileName');
    const cabinetLink = $('#cabinetLink');
    const adminLink = $('#adminLink');
    if (authBtn) authBtn.classList.toggle('hidden', session.role !== 'guest');
    if (profileBox) profileBox.classList.toggle('hidden', session.role === 'guest');
    if (profileName) profileName.textContent = session.name || (session.role === 'admin' ? 'Администратор' : 'Пользователь');
    if (cabinetLink) cabinetLink.classList.toggle('hidden', !['user','admin'].includes(session.role));
    if (adminLink) adminLink.classList.toggle('hidden', session.role !== 'admin');
  }

  function syncHeader() { renderHeaderSession(); updateRoleNotice(); }

  function updateRoleNotice() {
    const roleLabel = $('#roleLabel');
    if (!roleLabel) return;
    const session = loadSession();
    const map = { guest: 'Гость', user: 'Зарегистрированный пользователь', admin: 'Администратор' };
    roleLabel.textContent = map[session.role] || 'Гость';
  }

  function openAuthModal(defaultRole = 'user') {
    const modal = $('#authModal');
    if (!modal) return;
    modal.classList.remove('hidden');
    const session = loadSession();
    const chosen = session.role !== 'guest' ? session.role : defaultRole;
    $all('[data-role]', modal).forEach((btn) => btn.classList.toggle('active', btn.dataset.role === chosen));
    const roleInput = $('#authRole');
    const nameInput = $('#authName');
    const userIdInput = $('#authUserId');
    const adminCodeWrap = $('#adminCodeWrap');
    const adminCode = $('#authAdminCode');
    if (roleInput) roleInput.value = chosen;
    if (nameInput) nameInput.value = session.name || '';
    if (userIdInput) userIdInput.value = session.userId || '1';
    if (adminCodeWrap) adminCodeWrap.classList.toggle('hidden', chosen !== 'admin');
    if (adminCode) adminCode.value = '';
  }

  function closeAuthModal() { $('#authModal')?.classList.add('hidden'); }

  function attachAuthHandlers() {
    $('#openAuthBtn')?.addEventListener('click', () => openAuthModal('user'));
    $('#heroLoginBtn')?.addEventListener('click', () => openAuthModal('user'));
    $('#ctaGuestBtn')?.addEventListener('click', () => openAuthModal('user'));
    $('#logoutBtn')?.addEventListener('click', () => {
      clearSession();
      if (location.pathname.endsWith('/user.html') || location.pathname.endsWith('/admin.html')) location.href = '/';
      else syncHeader();
    });
    $('#closeAuthBtn')?.addEventListener('click', closeAuthModal);
    $('#closeAuthBackdrop')?.addEventListener('click', closeAuthModal);
    $all('[data-role]').forEach((btn) => btn.addEventListener('click', () => {
      const role = btn.dataset.role;
      $('#authRole').value = role;
      $all('[data-role]').forEach((x) => x.classList.toggle('active', x === btn));
      $('#adminCodeWrap')?.classList.toggle('hidden', role !== 'admin');
    }));
    $('#authForm')?.addEventListener('submit', (event) => {
      event.preventDefault();
      const role = $('#authRole').value;
      const name = $('#authName').value.trim() || (role === 'admin' ? 'Администратор' : 'Пользователь');
      const userId = $('#authUserId').value.trim() || '1';
      if (role === 'admin') {
        const code = $('#authAdminCode').value.trim();
        if (code !== 'admin123') {
          alert('Неверный код администратора. Для демонстрации используйте код: admin123');
          return;
        }
      }
      saveSession({ role, name, userId });
      closeAuthModal();
      if (role === 'admin') location.href = '/admin.html';
      else if (role === 'user') location.href = '/user.html';
      else location.href = '/';
    });
    syncHeader();
  }

  function guardPage(role) {
    const session = loadSession();
    if (role === 'user' && !['user','admin'].includes(session.role)) return false;
    if (role === 'admin' && session.role !== 'admin') return false;
    return true;
  }

  function renderAccessDenied(target, title, text) {
    target.innerHTML = `
      <section class="container section">
        <div class="access-card">
          <p class="section-label">Доступ ограничен</p>
          <h1 style="font-size:2.3rem">${title}</h1>
          <p class="lead-text">${text}</p>
          <div class="access-actions">
            <a href="/" class="secondary-btn">На главную</a>
            <button class="primary-btn" id="accessLoginBtn">Войти</button>
          </div>
        </div>
      </section>`;
    $('#accessLoginBtn', target)?.addEventListener('click', () => openAuthModal(role === 'admin' ? 'admin' : 'user'));
  }

  function createEventCard(event, { onOpen, showFavorite = true } = {}) {
    const node = document.createElement('article');
    node.className = 'event-card';
    node.innerHTML = `
      <div class="event-cover"></div>
      <div class="event-body">
        <div class="event-topline">
          <span>${getCategoryName(event)}</span>
          <span>Просмотры: ${event.views ?? 0}</span>
        </div>
        <h3 class="event-title">${event.title || 'Без названия'}</h3>
        <p class="event-text">${shortText(event.annotation || event.description, 140)}</p>
        <div class="event-meta">
          Дата: ${formatDate(event.eventDate)}<br>
          Формат: ${event.paid ? 'Платное' : 'Бесплатное'}<br>
          Организатор: ${getInitiatorName(event)}
        </div>
      </div>
      <div class="event-actions">
        <button class="ghost-btn small" data-open>Подробнее</button>
        ${showFavorite ? `<button class="ghost-btn small" data-favorite>${isFavorite(event.id) ? 'Убрать' : 'В избранное'}</button>` : ''}
      </div>`;
    $('[data-open]', node).addEventListener('click', () => onOpen?.(event.id));
    if (showFavorite) $('[data-favorite]', node).addEventListener('click', () => toggleFavorite(event));
    return node;
  }

  function createFavoriteCard(item, { onOpen, onRemove } = {}) {
    const card = document.createElement('article');
    card.className = 'favorite-card';
    card.innerHTML = `
      <div class="favorite-body">
        <div class="favorite-topline">
          <span>${item.category}</span>
          <span>Просмотры: ${item.views ?? 0}</span>
        </div>
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
    const start = new Date(); start.setHours(0,0,0,0);
    const end = new Date(start); end.setDate(end.getDate() + 1);
    const params = new URLSearchParams({ start: formatStatsDate(start), end: formatStatsDate(end), unique: 'false' });
    params.append('uris', eventUri(eventId));
    const stats = await apiFetch(`/api/stats/stats?${params.toString()}`);
    return Array.isArray(stats) ? stats[0] : null;
  }

  async function openEventModal(id, { onShowStats } = {}) {
    try {
      const event = await apiFetch(`/api/main/events/${id}`);
      const target = $('#eventModalContent');
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
        <div class="modal-actions">
          <button class="secondary-btn" id="modalFavoriteBtn">${isFavorite(event.id) ? 'Убрать из избранного' : 'Добавить в избранное'}</button>
          <button class="ghost-btn" id="modalHitBtn">Записать просмотр</button>
          <button class="primary-btn" id="modalStatsBtn">Показать статистику</button>
        </div>`;
      $('#modalFavoriteBtn').addEventListener('click', () => { toggleFavorite(event); closeEventModal(); });
      $('#modalHitBtn').addEventListener('click', async () => { await sendDemoHit(event.id); alert('Просмотр события зафиксирован.'); });
      $('#modalStatsBtn').addEventListener('click', async () => {
        const stat = await loadEventStats(event.id);
        closeEventModal();
        onShowStats?.(stat, event);
      });
      $('#eventModal').classList.remove('hidden');
    } catch (error) {
      alert(`Не удалось открыть карточку события: ${error.message}`);
    }
  }
  function closeEventModal() { $('#eventModal')?.classList.add('hidden'); }
  function attachModalHandlers() { $('#closeModalBtn')?.addEventListener('click', closeEventModal); $('#closeModalBackdrop')?.addEventListener('click', closeEventModal); }

  return {
    $, $all, state, apiFetch, formatDate, formatStatsDate, shortText, getCategoryName, getInitiatorName, eventUri,
    showEmpty, setText, loadSession, saveSession, clearSession, attachAuthHandlers, openAuthModal, closeAuthModal,
    guardPage, renderAccessDenied, loadFavorites, saveFavorites, isFavorite, toggleFavorite, createEventCard, createFavoriteCard,
    syncHeader, attachModalHandlers, openEventModal, closeEventModal, loadEventStats, sendDemoHit
  };
})();
