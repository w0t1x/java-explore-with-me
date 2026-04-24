(() => {
  const { $, apiFetch, showEmpty, setText, attachAuthHandlers, attachModalHandlers, guardPage, renderAccessDenied, loadSession, loadFavorites, saveFavorites, createEventCard, createFavoriteCard, openEventModal } = App;

  function renderFavoritesSection() {
    const target = $('#userFavoritesList');
    const list = loadFavorites();
    setText('#myFavoritesCount', String(list.length));
    if (!list.length) return showEmpty(target, 'Избранные события ещё не добавлены.');
    target.innerHTML = '';
    list.forEach((item) => target.appendChild(createFavoriteCard(item, {
      onOpen: (id) => openEventModal(id),
      onRemove: (id) => {
        saveFavorites(loadFavorites().filter((x) => String(x.id) !== String(id)));
        renderFavoritesSection();
      }
    })));
  }

  async function loadMyEvents(userId) {
    try {
      const events = await apiFetch(`/api/main/users/${userId}/events?from=0&size=12`);
      setText('#myEventsMeta', `Событий: ${events.length}`);
      setText('#myEventsCount', String(events.length));
      const target = $('#myEventsList');
      if (!events.length) return showEmpty(target, 'У этого пользователя пока нет созданных событий.');
      target.innerHTML = '';
      events.forEach((event) => target.appendChild(createEventCard(event, { onOpen: (id) => openEventModal(id) })));
    } catch (error) {
      showEmpty($('#myEventsList'), `Не удалось загрузить события пользователя: ${error.message}`);
      setText('#myEventsMeta', 'Ошибка загрузки');
    }
  }

  async function loadMyRequests(userId) {
    try {
      const requests = await apiFetch(`/api/main/users/${userId}/requests`);
      setText('#myRequestsMeta', `Заявок: ${requests.length}`);
      setText('#myRequestsCount', String(requests.length));
      const target = $('#myRequestsList');
      if (!requests.length) return showEmpty(target, 'У пользователя пока нет заявок на участие.');
      target.innerHTML = '';
      requests.forEach((item) => {
        const card = document.createElement('div');
        card.className = 'list-card';
        card.innerHTML = `
          <div class="list-topline"><span>Заявка #${item.id ?? '—'}</span><span>${item.status || 'UNKNOWN'}</span></div>
          <h3 style="margin:12px 0 8px">Событие ID: ${item.event || item.eventId || '—'}</h3>
          <div class="small-text">Создана: ${App.formatDate(item.created || item.createdOn)}</div>`;
        target.appendChild(card);
      });
    } catch (error) {
      showEmpty($('#myRequestsList'), `Не удалось загрузить заявки: ${error.message}`);
      setText('#myRequestsMeta', 'Ошибка загрузки');
    }
  }

  async function initPage() {
    attachAuthHandlers();
    attachModalHandlers();
    const root = $('#userApp');
    if (!guardPage('user')) {
      renderAccessDenied(root, 'Личный кабинет доступен после входа', 'Чтобы открыть страницу зарегистрированного пользователя, выполните вход как пользователь или администратор.');
      return;
    }
    const session = loadSession();
    setText('#userNameValue', session.name || 'Пользователь');
    setText('#userIdValue', session.userId || '1');
    $('#refreshCabinetBtn')?.addEventListener('click', () => { loadMyEvents(session.userId || '1'); loadMyRequests(session.userId || '1'); renderFavoritesSection(); });
    window.addEventListener('favorites:changed', renderFavoritesSection);
    renderFavoritesSection();
    await Promise.allSettled([loadMyEvents(session.userId || '1'), loadMyRequests(session.userId || '1')]);
  }

  initPage();
})();
