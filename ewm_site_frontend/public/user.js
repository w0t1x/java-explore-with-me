
(() => {
  const { $, apiFetch, currentUser, guardPage, renderAccessDenied, attachAuthHandlers, attachModalHandlers, createEventCard, createFavoriteCard, openEventModal, showEmpty, setText, loadFavorites, saveFavorites, normalizeDateTimeLocal, DEFAULT_LOCATION, loadUserComments, updateComment, deleteComment } = App;
  let selectedEventIdForRequests = null;

  async function loadCategoriesForForm() {
    const select = $('#eventCategory');
    try {
      const categories = await apiFetch('/api/main/categories?from=0&size=50');
      select.innerHTML = categories.map((item) => `<option value="${item.id}">${item.name}</option>`).join('');
    } catch (error) {
      select.innerHTML = '<option value="">Нет категорий</option>';
    }
  }

  function renderFavoritesSection() {
    const target = $('#userFavoritesList');
    const list = loadFavorites();
    setText('#myFavoritesCount', String(list.length));
    if (!list.length) return showEmpty(target, 'Избранные события ещё не добавлены.');
    target.innerHTML = '';
    list.forEach((item) => target.appendChild(createFavoriteCard(item, {
      onOpen: (id) => openEventModal(id, { mode: 'user' }),
      onRemove: (id) => {
        saveFavorites(loadFavorites().filter((x) => String(x.id) !== String(id)));
        renderFavoritesSection();
      }
    })));
  }

  async function loadMyEvents() {
    const user = currentUser();
    if (!user?.backendUserId) return;
    try {
      const events = await apiFetch(`/api/main/users/${user.backendUserId}/events?from=0&size=20`);
      setText('#myEventsMeta', `Событий: ${events.length}`);
      setText('#myEventsCount', String(events.length));
      const target = $('#myEventsList');
      if (!events.length) {
        showEmpty(target, 'У вас пока нет собственных событий.');
        showEmpty($('#ownerRequestsList'), 'Создайте событие, чтобы получать заявки на участие.');
        setText('#ownerRequestsMeta', 'Нет событий для обработки заявок');
        return;
      }
      target.innerHTML = '';
      events.forEach((event) => {
        const extraActions = `<button class="ghost-btn small" data-requests="${event.id}">Заявки</button>`;
        const card = createEventCard(event, {
          onOpen: (id) => openEventModal(id, { mode: 'user' }),
          allowFavorite: false,
          extraActions
        });
        card.querySelector(`[data-requests="${event.id}"]`)?.addEventListener('click', () => {
          selectedEventIdForRequests = event.id;
          setText('#ownerRequestsMeta', `Загрузка заявок для события: ${event.title}`);
          loadOwnerEventRequests(event.id, event.title);
          document.getElementById('ownerRequests').scrollIntoView({ behavior: 'smooth', block: 'start' });
        });
        target.appendChild(card);
      });
      if (!selectedEventIdForRequests && events[0]?.id) {
        selectedEventIdForRequests = events[0].id;
        loadOwnerEventRequests(events[0].id, events[0].title);
      }
    } catch (error) {
      showEmpty($('#myEventsList'), `Не удалось загрузить ваши события: ${error.message}`);
      setText('#myEventsMeta', 'Ошибка загрузки');
    }
  }

  async function loadMyRequests() {
    const user = currentUser();
    if (!user?.backendUserId) return;
    try {
      const requests = await apiFetch(`/api/main/users/${user.backendUserId}/requests`);
      setText('#myRequestsMeta', `Заявок: ${requests.length}`);
      setText('#myRequestsCount', String(requests.length));
      const target = $('#myRequestsList');
      if (!requests.length) return showEmpty(target, 'У вас пока нет заявок на участие.');
      target.innerHTML = '';
      requests.forEach((item) => {
        const isPending = item.status === 'PENDING';
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Заявка #${item.id ?? '—'}</span><span>${item.status || 'UNKNOWN'}</span></div>
          <h3 style="margin:12px 0 8px">Событие ID: ${item.event || item.eventId || '—'}</h3>
          <div class="small-text">Создана: ${App.formatDate(item.created || item.createdOn)}</div>
          <div class="event-actions" style="padding:16px 0 0">
            ${isPending ? '<button class="ghost-btn small" data-cancel>Отменить заявку</button>' : '<span class="form-note">Заявка уже обработана и не требует действий.</span>'}
          </div>`;
        if (isPending) {
          card.querySelector('[data-cancel]').addEventListener('click', async () => {
            try {
              await apiFetch(`/api/main/users/${user.backendUserId}/requests/${item.id}/cancel`, { method: 'PATCH' });
              loadMyRequests();
            } catch (error) {
              alert(`Не удалось отменить заявку: ${error.message}`);
            }
          });
        }
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#myRequestsList'), `Не удалось загрузить заявки: ${error.message}`);
      setText('#myRequestsMeta', 'Ошибка загрузки');
    }
  }

  async function loadOwnerEventRequests(eventId, title = '') {
    const user = currentUser();
    if (!user?.backendUserId) return;
    try {
      const requests = await apiFetch(`/api/main/users/${user.backendUserId}/events/${eventId}/requests`);
      setText('#ownerRequestsMeta', title ? `Заявки к событию: ${title}` : `Заявки к событию #${eventId}`);
      const target = $('#ownerRequestsList');
      if (!requests.length) return showEmpty(target, 'По этому событию пока нет входящих заявок.');
      target.innerHTML = '';
      requests.forEach((item) => {
        const card = document.createElement('div');
        card.className = 'list-card';
        const canModerate = item.status === 'PENDING';
        card.innerHTML = `
          <div class="list-topline"><span>Заявка #${item.id ?? '—'}</span><span>${item.status || 'UNKNOWN'}</span></div>
          <h3 style="margin:12px 0 8px">Участник: ${item.requester || item.requesterId || '—'}</h3>
          <div class="small-text">Создана: ${App.formatDate(item.created || item.createdOn)}</div>
          <div class="event-actions" style="padding:16px 0 0">
            ${canModerate ? '<button class="primary-btn small" data-confirm>Подтвердить</button><button class="ghost-btn small" data-reject>Отклонить</button>' : '<span class="form-note">Заявка уже обработана.</span>'}
          </div>`;
        if (canModerate) {
          card.querySelector('[data-confirm]')?.addEventListener('click', () => updateOwnerRequestStatus(eventId, item.id, 'CONFIRMED'));
          card.querySelector('[data-reject]')?.addEventListener('click', () => updateOwnerRequestStatus(eventId, item.id, 'REJECTED'));
        }
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#ownerRequestsList'), `Не удалось загрузить входящие заявки: ${error.message}`);
    }
  }

  async function updateOwnerRequestStatus(eventId, requestId, status) {
    const user = currentUser();
    if (!user?.backendUserId) return;
    try {
      const result = await apiFetch(`/api/main/users/${user.backendUserId}/events/${eventId}/requests`, {
        method: 'PATCH',
        body: JSON.stringify({ requestIds: [Number(requestId)], status })
      });
      const affected = status === 'CONFIRMED' ? (result?.confirmedRequests?.length || 0) : (result?.rejectedRequests?.length || 0);
      alert(status === 'CONFIRMED' ? `Подтверждено заявок: ${affected}` : `Отклонено заявок: ${affected}`);
      loadOwnerEventRequests(eventId);
      loadMyRequests();
    } catch (error) {
      alert(`Не удалось изменить статус заявки: ${error.message}`);
    }
  }

  function validateEventForm(payload) {
    if (!payload.title || payload.title.length < 3 || payload.title.length > 120) {
      return 'Название события должно содержать от 3 до 120 символов.';
    }
    if (!payload.annotation || payload.annotation.length < 20 || payload.annotation.length > 2000) {
      return 'Краткое описание должно содержать от 20 до 2000 символов.';
    }
    if (!payload.description || payload.description.length < 20 || payload.description.length > 7000) {
      return 'Полное описание должно содержать от 20 до 7000 символов.';
    }
    if (!payload.eventDate) {
      return 'Укажите дату события.';
    }
    if (!payload.category) {
      return 'Выберите категорию.';
    }
    return '';
  }

  async function createEvent(event) {
    event.preventDefault();
    const user = currentUser();
    if (!user?.backendUserId) return;
    const payload = {
      title: $('#eventTitle').value.trim(),
      annotation: $('#eventAnnotation').value.trim(),
      description: $('#eventDescription').value.trim(),
      category: Number($('#eventCategory').value),
      eventDate: normalizeDateTimeLocal($('#eventDate').value),
      paid: $('#eventPaid').value === 'true',
      participantLimit: Number($('#eventLimit').value || 0),
      requestModeration: $('#eventModeration').value === 'true',
      location: { ...DEFAULT_LOCATION }
    };
    const validationMessage = validateEventForm(payload);
    if (validationMessage) return alert(validationMessage);
    try {
      const created = await apiFetch(`/api/main/users/${user.backendUserId}/events`, { method: 'POST', body: JSON.stringify(payload) });
      $('#createEventForm').reset();
      $('#eventLimit').value = '0';
      $('#eventModeration').value = 'true';
      alert('Событие создано и отправлено на модерацию.');
      selectedEventIdForRequests = created?.id || null;
      loadMyEvents();
    } catch (error) {
      alert(`Не удалось создать событие: ${error.message}`);
    }
  }

  async function loadMyCommentsList() {
    const user = currentUser();
    if (!user?.backendUserId) return;
    try {
      const comments = await loadUserComments(user.backendUserId);
      setText('#myCommentsMeta', `Комментариев: ${comments.length}`);
      const target = $('#myCommentsList');
      if (!comments.length) return showEmpty(target, 'Вы ещё не оставляли комментарии.');
      target.innerHTML = '';
      comments.forEach((item) => {
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Комментарий #${item.id ?? '—'}</span><span>${item.status || 'PENDING'}</span></div>
          <h3 style="margin:12px 0 8px">Событие ID: ${item.eventId ?? '—'}</h3>
          <div class="small-text" style="white-space:pre-wrap">${item.text || ''}</div>
          <div class="small-text" style="margin-top:8px">Создан: ${App.formatDate(item.createdOn)}</div>
          ${item.rejectionReason ? `<div class="small-text" style="margin-top:8px">Причина отклонения: ${item.rejectionReason}</div>` : ''}
          <div class="event-actions" style="padding:16px 0 0">
            <button class="ghost-btn small" data-edit>Изменить</button>
            <button class="ghost-btn small" data-delete>Удалить</button>
          </div>`;
        card.querySelector('[data-edit]').addEventListener('click', async () => {
          const text = prompt('Новый текст комментария:', item.text || '');
          if (!text || !text.trim()) return;
          try {
            await updateComment(user.backendUserId, item.id, text.trim());
            loadMyCommentsList();
          } catch (error) {
            alert(`Не удалось изменить комментарий: ${error.message}`);
          }
        });
        card.querySelector('[data-delete]').addEventListener('click', async () => {
          if (!confirm('Удалить комментарий?')) return;
          try {
            await deleteComment(user.backendUserId, item.id);
            loadMyCommentsList();
          } catch (error) {
            alert(`Не удалось удалить комментарий: ${error.message}`);
          }
        });
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#myCommentsList'), `Не удалось загрузить комментарии: ${error.message}`);
      setText('#myCommentsMeta', 'Ошибка загрузки');
    }
  }

  async function init() {
    attachAuthHandlers();
    attachModalHandlers();
    const root = $('#userRoot');
    if (!guardPage('user')) {
      renderAccessDenied(root, {
        title: 'Войдите, чтобы открыть личный кабинет',
        text: 'Гостю недоступны создание событий, заявки на участие, входящие запросы, комментарии и избранное. Выполните вход или зарегистрируйтесь.'
      });
      return;
    }

    const user = currentUser();
    $('#profileTitle').textContent = user?.name || 'Пользователь';
    setText('#userNameValue', user?.name || '—');
    setText('#userEmailValue', user?.email || '—');

    $('#refreshProfileBtn').addEventListener('click', () => {
      loadMyEvents();
      loadMyRequests();
      loadMyCommentsList();
      renderFavoritesSection();
      if (selectedEventIdForRequests) loadOwnerEventRequests(selectedEventIdForRequests);
    });
    $('#createEventForm').addEventListener('submit', createEvent);

    window.addEventListener('favorites:changed', renderFavoritesSection);
    window.addEventListener('requests:changed', loadMyRequests);
    window.addEventListener('comments:changed', loadMyCommentsList);

    renderFavoritesSection();
    await Promise.allSettled([loadCategoriesForForm(), loadMyEvents(), loadMyRequests(), loadMyCommentsList()]);
  }

  init();
})();
