
(() => {
  const { $, apiFetch, attachAuthHandlers, attachModalHandlers, guardPage, renderAccessDenied, openEventModal, loadEventStats, showEmpty, setText, adminSearchComments, moderateComment, adminDeleteComment } = App;

  function setHealth(el, ok, text) {
    el.textContent = text;
    el.classList.remove('ok', 'fail');
    el.classList.add(ok ? 'ok' : 'fail');
  }

  async function checkHealth() {
    try {
      const main = await apiFetch('/api/main/actuator/health');
      setHealth($('#mainHealth'), true, `Основной сервис: ${main.status || 'UP'}`);
    } catch (error) {
      setHealth($('#mainHealth'), false, `Основной сервис: ${error.message}`);
    }
    try {
      const stats = await apiFetch('/api/stats/actuator/health');
      setHealth($('#statsHealth'), true, `Статистика: ${stats.status || 'UP'}`);
    } catch (error) {
      setHealth($('#statsHealth'), false, `Статистика: ${error.message}`);
    }
  }

  async function loadAdminUsers() {
    try {
      const users = await apiFetch('/api/main/admin/users?from=0&size=20');
      setText('#adminUsersMeta', `Пользователей: ${users.length}`);
      const target = $('#adminUsersList');
      if (!users.length) return showEmpty(target, 'Пользователи не найдены.');
      target.innerHTML = '';
      users.forEach((user) => {
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Пользователь #${user.id ?? '—'}</span><span>${user.email || '—'}</span></div>
          <h3 style="margin:12px 0 8px">${user.name || 'Без имени'}</h3>
          <div class="event-actions" style="padding-top:16px"><button class="ghost-btn small" data-delete>Удалить</button></div>`;
        card.querySelector('[data-delete]').addEventListener('click', async () => {
          if (!confirm(`Удалить пользователя ${user.name || user.email}?`)) return;
          try {
            await apiFetch(`/api/main/admin/users/${user.id}`, { method: 'DELETE' });
            loadAdminUsers();
          } catch (error) {
            alert(`Не удалось удалить пользователя: ${error.message}`);
          }
        });
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#adminUsersList'), `Не удалось загрузить пользователей: ${error.message}`);
      setText('#adminUsersMeta', 'Ошибка загрузки');
    }
  }

  async function updateAdminEventState(eventId, stateAction) {
    try {
      await apiFetch(`/api/main/admin/events/${eventId}`, {
        method: 'PATCH',
        body: JSON.stringify({ stateAction })
      });
      loadAdminEvents();
    } catch (error) {
      alert(`Не удалось изменить статус события: ${error.message}`);
    }
  }

  function showStatsInBox(stat, event) {
    const box = $('#statsResult');
    box.innerHTML = stat
      ? `Для события <strong>${event.title}</strong> по URI <code>${stat.uri}</code> найдено <strong>${stat.hits}</strong> просмотров.`
      : `Для события <strong>${event.title}</strong> статистика по URI <code>${App.eventUri(event.id)}</code> пока не найдена.`;
    document.getElementById('services').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  async function loadAdminEvents() {
    try {
      const events = await apiFetch('/api/main/admin/events?from=0&size=20');
      setText('#adminEventsMeta', `Событий: ${events.length}`);
      const target = $('#adminEventsList');
      if (!events.length) return showEmpty(target, 'События не найдены.');
      target.innerHTML = '';
      events.forEach((event) => {
        const state = event.state || 'UNKNOWN';
        const extraActions = `
          <span class="chip">${state}</span>
          <button class="ghost-btn small" data-publish="${event.id}">Опубликовать</button>
          <button class="ghost-btn small" data-reject="${event.id}">Отклонить</button>
        `;
        const card = App.createEventCard(event, {
          onOpen: (id) => openEventModal(id, { mode: 'admin', onShowStats: showStatsInBox }),
          allowFavorite: false,
          allowRequest: false,
          extraActions
        });
        card.querySelector(`[data-publish="${event.id}"]`)?.addEventListener('click', () => updateAdminEventState(event.id, 'PUBLISH_EVENT'));
        card.querySelector(`[data-reject="${event.id}"]`)?.addEventListener('click', () => updateAdminEventState(event.id, 'REJECT_EVENT'));
        target.appendChild(card);
      });
      fillCompilationEventOptions(events);
    } catch (error) {
      showEmpty($('#adminEventsList'), `Не удалось загрузить события: ${error.message}`);
      setText('#adminEventsMeta', 'Ошибка загрузки');
    }
  }

  async function loadAdminCategories() {
    try {
      const categories = await apiFetch('/api/main/categories?from=0&size=50');
      const target = $('#adminCategoriesList');
      if (!categories.length) return showEmpty(target, 'Категории пока не созданы.');
      target.innerHTML = '';
      categories.forEach((category) => {
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Категория #${category.id}</span></div>
          <h3 style="margin:12px 0 8px">${category.name}</h3>
          <div class="event-actions" style="padding-top:16px">
            <button class="ghost-btn small" data-edit>Переименовать</button>
            <button class="ghost-btn small" data-delete>Удалить</button>
          </div>`;
        card.querySelector('[data-edit]').addEventListener('click', async () => {
          const newName = prompt('Новое название категории:', category.name);
          if (!newName || newName.trim() === category.name) return;
          try {
            await apiFetch(`/api/main/admin/categories/${category.id}`, {
              method: 'PATCH',
              body: JSON.stringify({ id: category.id, name: newName.trim() })
            });
            loadAdminCategories();
          } catch (error) {
            alert(`Не удалось изменить категорию: ${error.message}`);
          }
        });
        card.querySelector('[data-delete]').addEventListener('click', async () => {
          if (!confirm(`Удалить категорию "${category.name}"?`)) return;
          try {
            await apiFetch(`/api/main/admin/categories/${category.id}`, { method: 'DELETE' });
            loadAdminCategories();
          } catch (error) {
            alert(`Не удалось удалить категорию: ${error.message}`);
          }
        });
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#adminCategoriesList'), `Не удалось загрузить категории: ${error.message}`);
    }
  }

  async function createCategory(event) {
    event.preventDefault();
    const name = $('#newCategoryName').value.trim();
    if (!name) return alert('Укажите название категории.');
    try {
      await apiFetch('/api/main/admin/categories', {
        method: 'POST',
        body: JSON.stringify({ name })
      });
      $('#newCategoryName').value = '';
      loadAdminCategories();
    } catch (error) {
      alert(`Не удалось создать категорию: ${error.message}`);
    }
  }

  function parseIdsCsv(text) {
    return text.split(',').map((part) => part.trim()).filter(Boolean).map((value) => Number(value)).filter((value) => Number.isFinite(value));
  }

  function fillCompilationEventOptions(events) {
    const box = $('#compEventHints');
    if (!box) return;
    box.innerHTML = events.slice(0, 12).map((event) => `<span class="chip">${event.id}: ${event.title}</span>`).join('');
  }

  async function loadAdminCompilations() {
    try {
      const compilations = await apiFetch('/api/main/compilations?from=0&size=20');
      const target = $('#adminCompilationsList');
      if (!compilations.length) return showEmpty(target, 'Подборки пока не созданы.');
      target.innerHTML = '';
      compilations.forEach((compilation) => {
        const events = Array.isArray(compilation.events) ? compilation.events : [];
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Подборка #${compilation.id ?? '—'}</span><span>${compilation.pinned ? 'Закреплена' : 'Обычная'}</span></div>
          <h3 style="margin:12px 0 8px">${compilation.title || 'Без названия'}</h3>
          <div class="small-text">Событий внутри: ${events.length}</div>
          <div class="event-actions" style="padding-top:16px">
            <button class="ghost-btn small" data-edit>Изменить</button>
            <button class="ghost-btn small" data-delete>Удалить</button>
          </div>`;
        card.querySelector('[data-edit]').addEventListener('click', async () => {
          const title = prompt('Новое название подборки:', compilation.title || '');
          if (!title) return;
          const rawIds = prompt('Список ID событий через запятую:', events.map((event) => event.id).join(', ')) || '';
          const pinned = confirm('Закрепить подборку на главной странице?');
          try {
            await apiFetch(`/api/main/admin/compilations/${compilation.id}`, {
              method: 'PATCH',
              body: JSON.stringify({ title: title.trim(), pinned, events: parseIdsCsv(rawIds) })
            });
            loadAdminCompilations();
          } catch (error) {
            alert(`Не удалось изменить подборку: ${error.message}`);
          }
        });
        card.querySelector('[data-delete]').addEventListener('click', async () => {
          if (!confirm(`Удалить подборку "${compilation.title}"?`)) return;
          try {
            await apiFetch(`/api/main/admin/compilations/${compilation.id}`, { method: 'DELETE' });
            loadAdminCompilations();
          } catch (error) {
            alert(`Не удалось удалить подборку: ${error.message}`);
          }
        });
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#adminCompilationsList'), `Не удалось загрузить подборки: ${error.message}`);
    }
  }

  async function createCompilation(event) {
    event.preventDefault();
    const title = $('#newCompilationTitle').value.trim();
    const events = parseIdsCsv($('#newCompilationEvents').value);
    const pinned = $('#newCompilationPinned').checked;
    if (!title) return alert('Укажите название подборки.');
    try {
      await apiFetch('/api/main/admin/compilations', {
        method: 'POST',
        body: JSON.stringify({ title, pinned, events })
      });
      $('#newCompilationForm').reset();
      loadAdminCompilations();
    } catch (error) {
      alert(`Не удалось создать подборку: ${error.message}`);
    }
  }

  async function loadAdminComments(status = 'PENDING') {
    try {
      const comments = await adminSearchComments(status, '');
      setText('#adminCommentsMeta', `Комментариев: ${comments.length}`);
      const target = $('#adminCommentsList');
      if (!comments.length) return showEmpty(target, 'Комментарии по выбранному статусу не найдены.');
      target.innerHTML = '';
      comments.forEach((comment) => {
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Комментарий #${comment.id ?? '—'}</span><span>${comment.status || status}</span></div>
          <h3 style="margin:12px 0 8px">Событие ID: ${comment.eventId ?? '—'}</h3>
          <div class="small-text">Автор: ${comment.author?.name || '—'}</div>
          <div class="small-text" style="margin-top:10px; white-space:pre-wrap">${comment.text || ''}</div>
          ${comment.rejectionReason ? `<div class="small-text" style="margin-top:8px">Причина отклонения: ${comment.rejectionReason}</div>` : ''}
          <div class="event-actions" style="padding-top:16px">
            <button class="ghost-btn small" data-publish>Публиковать</button>
            <button class="ghost-btn small" data-reject>Отклонить</button>
            <button class="ghost-btn small" data-delete>Удалить</button>
          </div>`;
        card.querySelector('[data-publish]').addEventListener('click', async () => {
          try {
            await moderateComment(comment.id, 'PUBLISH');
            loadAdminComments(status);
          } catch (error) {
            alert(`Не удалось опубликовать комментарий: ${error.message}`);
          }
        });
        card.querySelector('[data-reject]').addEventListener('click', async () => {
          const reason = prompt('Причина отклонения комментария:', comment.rejectionReason || '') || '';
          try {
            await moderateComment(comment.id, 'REJECT', reason.trim());
            loadAdminComments(status);
          } catch (error) {
            alert(`Не удалось отклонить комментарий: ${error.message}`);
          }
        });
        card.querySelector('[data-delete]').addEventListener('click', async () => {
          if (!confirm('Удалить комментарий?')) return;
          try {
            await adminDeleteComment(comment.id);
            loadAdminComments(status);
          } catch (error) {
            alert(`Не удалось удалить комментарий: ${error.message}`);
          }
        });
        target.appendChild(card);
      });
      document.querySelectorAll('[data-comment-status]').forEach((btn) => btn.classList.toggle('active', btn.dataset.commentStatus === status));
    } catch (error) {
      showEmpty($('#adminCommentsList'), `Не удалось загрузить комментарии: ${error.message}`);
      setText('#adminCommentsMeta', 'Ошибка загрузки');
    }
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

  async function init() {
    attachAuthHandlers();
    attachModalHandlers();
    const root = $('#adminRoot');
    if (!guardPage('admin')) {
      renderAccessDenied(root, {
        title: 'Админ-панель доступна только администратору',
        text: 'Чтобы попасть в это окно, выполните вход под администратором.',
        admin: true
      });
      return;
    }

    $('#statsForm').addEventListener('submit', handleStatsForm);
    $('#refreshAdminBtn').addEventListener('click', () => {
      checkHealth();
      loadAdminUsers();
      loadAdminEvents();
      loadAdminCategories();
      loadAdminCompilations();
      loadAdminComments(document.querySelector('[data-comment-status].active')?.dataset.commentStatus || 'PENDING');
    });
    $('#newCategoryForm').addEventListener('submit', createCategory);
    $('#newCompilationForm').addEventListener('submit', createCompilation);
    document.querySelectorAll('[data-comment-status]').forEach((btn) => {
      btn.addEventListener('click', () => loadAdminComments(btn.dataset.commentStatus));
    });

    await Promise.allSettled([
      checkHealth(),
      loadAdminUsers(),
      loadAdminEvents(),
      loadAdminCategories(),
      loadAdminCompilations(),
      loadAdminComments('PENDING')
    ]);
  }

  init();
})();
