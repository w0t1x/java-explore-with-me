
(() => {
  const { $, apiFetch, currentSession, isAuthenticated, showEmpty, setText, attachAuthHandlers, attachModalHandlers, createEventCard, createFavoriteCard, openEventModal, loadFavorites, saveFavorites } = App;
  const state = { categories: [], events: [], compilations: [], selectedCategoryId: '' };

  function buildEventsQuery() {
    const formData = new FormData($('#eventsForm'));
    const params = new URLSearchParams();
    ['text', 'paid', 'onlyAvailable', 'sort', 'categories', 'from', 'size'].forEach((key) => {
      const value = (formData.get(key) || '').toString().trim();
      if (value) params.append(key, value);
    });
    if (!params.has('from')) params.append('from', '0');
    if (!params.has('size')) params.append('size', '12');
    return params.toString();
  }

  function renderRoleState() {
    const auth = isAuthenticated();
    $('#favoritesSection')?.classList.toggle('hidden', !auth);
    $('#guestAccess')?.classList.toggle('hidden', auth);
    if (auth) renderFavorites();
  }

  function handleOpenEvent(id) {
    const mode = currentSession().role === 'admin' ? 'admin' : currentSession().role === 'user' ? 'user' : 'guest';
    openEventModal(id, {
      mode,
      onShowStats: (stat, event) => {
        alert(stat ? `Событие: ${event.title}\nURI: ${stat.uri}\nПросмотров: ${stat.hits}` : `Для события ${event.title} статистика пока не найдена.`);
      }
    });
  }

  function renderEvents() {
    const target = $('#eventsList');
    if (!state.events.length) {
      setText('#eventsMeta', 'Найдено 0 событий');
      return showEmpty(target, 'События не найдены. Попробуйте изменить фильтры.');
    }
    const canFavorite = currentSession().role !== 'guest';
    const canRequest = currentSession().role === 'user';
    target.innerHTML = '';
    state.events.forEach((event) => target.appendChild(createEventCard(event, {
      onOpen: handleOpenEvent,
      allowFavorite: canFavorite,
      allowRequest: canRequest,
      onRequest: () => openEventModal(event.id, { mode: 'user' })
    })));
    setText('#eventsMeta', `Найдено событий: ${state.events.length}`);
    setText('#eventsCounter', `События: ${state.events.length}`);
  }

  function renderCategoryChips() {
    const target = $('#categoriesChips');
    if (!state.categories.length) return showEmpty(target, 'Категории не найдены.');
    target.innerHTML = '';
    state.categories.forEach((category) => {
      const btn = document.createElement('button');
      btn.className = `chip ${String(category.id) === String(state.selectedCategoryId) ? 'active' : ''}`;
      btn.textContent = category.name;
      btn.addEventListener('click', () => {
        state.selectedCategoryId = String(category.id);
        $('#eventsForm [name="categories"]').value = state.selectedCategoryId;
        renderCategoryChips();
        loadEvents();
      });
      target.appendChild(btn);
    });
  }

  function renderCompilations() {
    const target = $('#compilationsList');
    if (!state.compilations.length) return showEmpty(target, 'Подборки пока не заполнены.');
    target.innerHTML = '';
    state.compilations.forEach((item) => {
      const card = document.createElement('article');
      card.className = 'compilation-card';
      const events = Array.isArray(item.events) ? item.events : [];
      card.innerHTML = `
        <div class="compilation-cover"></div>
        <div class="compilation-body">
          <div class="compilation-topline"><span>${item.pinned ? 'Закреплённая подборка' : 'Подборка'}</span><span>Событий: ${events.length}</span></div>
          <h3 class="compilation-title">${item.title || 'Без названия'}</h3>
          <p class="compilation-text">Подборка помогает быстро открыть близкие по теме мероприятия.</p>
          <div class="card-meta">${events.slice(0, 4).map((event) => event.title || `Событие #${event.id}`).join('<br>') || 'Список событий появится после наполнения базы.'}</div>
        </div>`;
      target.appendChild(card);
    });
    setText('#compCounter', `Подборки: ${state.compilations.length}`);
  }

  function renderFavorites() {
    const target = $('#favoritesList');
    const list = loadFavorites();
    if (!list.length) return showEmpty(target, 'Избранные события появятся здесь после добавления пользователем.');
    target.innerHTML = '';
    list.forEach((item) => target.appendChild(createFavoriteCard(item, {
      onOpen: handleOpenEvent,
      onRemove: (id) => saveFavorites(loadFavorites().filter((x) => String(x.id) !== String(id)))
    })));
  }

  async function loadCategories() {
    try {
      state.categories = await apiFetch('/api/main/categories?from=0&size=30');
      renderCategoryChips();
      setText('#categoriesCounter', `Категории: ${state.categories.length}`);
    } catch (error) {
      showEmpty($('#categoriesChips'), `Не удалось загрузить категории: ${error.message}`);
    }
  }

  async function loadEvents() {
    try {
      state.events = await apiFetch(`/api/main/events?${buildEventsQuery()}`);
      renderEvents();
    } catch (error) {
      showEmpty($('#eventsList'), `Не удалось загрузить события: ${error.message}`);
      setText('#eventsMeta', 'Ошибка загрузки');
    }
  }

  async function loadCompilations() {
    try {
      state.compilations = await apiFetch('/api/main/compilations?from=0&size=12');
      renderCompilations();
    } catch (error) {
      showEmpty($('#compilationsList'), `Не удалось загрузить подборки: ${error.message}`);
    }
  }

  function attachPageHandlers() {
    $('#eventsForm').addEventListener('submit', (e) => { e.preventDefault(); loadEvents(); });
    $('#resetEventsBtn').addEventListener('click', () => {
      $('#eventsForm').reset();
      $('#eventsForm [name="from"]').value = '0';
      $('#eventsForm [name="size"]').value = '12';
      $('#eventsForm [name="categories"]').value = '';
      state.selectedCategoryId = '';
      renderCategoryChips();
      loadEvents();
    });
    $('#clearCategoryBtn').addEventListener('click', () => {
      state.selectedCategoryId = '';
      $('#eventsForm [name="categories"]').value = '';
      renderCategoryChips();
      loadEvents();
    });
    $('#clearFavoritesBtn')?.addEventListener('click', () => saveFavorites([]));
    $('#reloadCompilationsBtn')?.addEventListener('click', loadCompilations);
    window.addEventListener('favorites:changed', () => {
      if (isAuthenticated()) renderFavorites();
      renderEvents();
    });
    window.addEventListener('session:changed', renderRoleState);
  }

  function initRoleContent() {
    renderRoleState();
    const session = currentSession();
    if (session.role !== 'guest') {
      $('#heroLoginBtn').textContent = session.role === 'admin' ? 'Открыть админ-панель' : 'Открыть профиль';
      $('#heroLoginBtn').onclick = () => { location.href = session.role === 'admin' ? '/admin' : '/profile'; };
    }
  }

  async function init() {
    attachAuthHandlers();
    attachModalHandlers();
    attachPageHandlers();
    initRoleContent();
    await Promise.allSettled([loadCategories(), loadEvents(), loadCompilations()]);
    if (isAuthenticated()) renderFavorites();
  }

  init();
})();
