(() => {
  const { $, apiFetch, showEmpty, setText, attachAuthHandlers, attachModalHandlers, guardPage, renderAccessDenied, loadSession, openEventModal, loadEventStats } = App;

  function setHealth(el, ok, text) {
    el.textContent = text;
    el.classList.remove('ok', 'fail');
    el.classList.add(ok ? 'ok' : 'fail');
  }

  async function checkHealth() {
    const mainEl = $('#mainHealth');
    const statsEl = $('#statsHealth');
    try {
      const main = await apiFetch('/api/main/actuator/health');
      setHealth(mainEl, true, `Основной сервис: ${main.status || 'UP'}`);
    } catch (error) {
      setHealth(mainEl, false, `Основной сервис: ${error.message}`);
    }
    try {
      const stats = await apiFetch('/api/stats/actuator/health');
      setHealth(statsEl, true, `Статистика: ${stats.status || 'UP'}`);
    } catch (error) {
      setHealth(statsEl, false, `Статистика: ${error.message}`);
    }
  }

  async function loadAdminEvents() {
    try {
      const events = await apiFetch('/api/main/admin/events?from=0&size=12');
      setText('#adminEventsMeta', `Событий: ${events.length}`);
      const target = $('#adminEventsList');
      if (!events.length) return showEmpty(target, 'Список событий для администратора пока пуст.');
      target.innerHTML = '';
      events.forEach((event) => {
        const card = App.createEventCard(event, { onOpen: (id) => openEventModal(id, { onShowStats: showStatsInBox }), showFavorite: false });
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#adminEventsList'), `Не удалось загрузить события администратора: ${error.message}`);
      setText('#adminEventsMeta', 'Ошибка загрузки');
    }
  }

  async function loadAdminUsers() {
    try {
      const users = await apiFetch('/api/main/admin/users?from=0&size=12');
      setText('#adminUsersMeta', `Пользователей: ${users.length}`);
      const target = $('#adminUsersList');
      if (!users.length) return showEmpty(target, 'Пользователи не найдены.');
      target.innerHTML = '';
      users.forEach((user) => {
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Пользователь #${user.id ?? '—'}</span><span>${user.email || 'email не указан'}</span></div>
          <h3 style="margin:12px 0 8px">${user.name || 'Без имени'}</h3>
          <div class="small-text">ID: ${user.id ?? '—'}</div>`;
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#adminUsersList'), `Не удалось загрузить пользователей: ${error.message}`);
      setText('#adminUsersMeta', 'Ошибка загрузки');
    }
  }

  function showStatsInBox(stat, event) {
    const box = $('#statsResult');
    box.innerHTML = stat
      ? `Для события <strong>${event.title}</strong> по URI <code>${stat.uri}</code> найдено <strong>${stat.hits}</strong> просмотров.`
      : `Для события <strong>${event.title}</strong> статистика по URI <code>${App.eventUri(event.id)}</code> пока не найдена.`;
    document.getElementById('services').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  async function handleStatsForm(event) {
    event.preventDefault();
    const id = $('#statsEventId').value.trim();
    if (!id) return;
    try {
      const stat = await loadEventStats(id);
      $('#statsResult').innerHTML = stat
        ? `URI: <code>${stat.uri}</code><br>Приложение: <strong>${stat.app}</strong><br>Просмотров: <strong>${stat.hits}</strong>`
        : `Для URI <code>/events/${id}</code> статистика пока не найдена.`;
    } catch (error) {
      $('#statsResult').textContent = `Не удалось получить статистику: ${error.message}`;
    }
  }

  async function initPage() {
    attachAuthHandlers();
    attachModalHandlers();
    const root = $('#adminApp');
    if (!guardPage('admin')) {
      renderAccessDenied(root, 'Админ-панель доступна только администратору', 'Чтобы попасть в эту страницу, выполните вход с ролью администратора. Для демонстрации используйте код admin123.');
      return;
    }
    const session = loadSession();
    setText('#adminNameValue', session.name || 'Администратор');
    $('#statsForm').addEventListener('submit', handleStatsForm);
    $('#refreshAdminBtn').addEventListener('click', () => { checkHealth(); loadAdminEvents(); loadAdminUsers(); });
    await Promise.allSettled([checkHealth(), loadAdminEvents(), loadAdminUsers()]);
  }

  initPage();
})();
