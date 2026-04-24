(() => {
  const { $, apiFetch, showEmpty, setText, attachAuthHandlers, attachModalHandlers, loadFavorites, saveFavorites, createEventCard, createFavoriteCard, openEventModal, loadSession } = App;
  const state = { categories: [], events: [], compilations: [], selectedCategoryId: '' };

  function buildEventsQuery() {
    const formData = new FormData($('#eventsForm'));
    const params = new URLSearchParams();
    const keys = ['text', 'paid', 'onlyAvailable', 'sort', 'categories', 'from', 'size'];
    keys.forEach((key) => {
      const value = (formData.get(key) || '').toString().trim();
      if (value) params.append(key, value);
    });
    if (!params.has('from')) params.append('from', '0');
    if (!params.has('size')) params.append('size', '12');
    return params.toString();
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

  function renderEvents() {
    const target = $('#eventsList');
    if (!state.events.length) {
      setText('#eventsMeta', 'Найдено 0 событий');
      return showEmpty(target, 'События не найдены. Попробуйте изменить фильтр или выполнить поиск заново.');
    }
    target.innerHTML = '';
    state.events.forEach((event) => target.appendChild(createEventCard(event, { onOpen: handleOpenEvent })));
    setText('#eventsMeta', `Найдено событий: ${state.events.length}`);
    setText('#eventsCounter', `События: ${state.events.length}`);
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
          <p class="compilation-text">Подборка помогает быстро открыть близкие по тематике события.</p>
          <div class="card-meta">${events.slice(0,4).map((event) => event.title || `Событие #${event.id}`).join('<br>') || 'Список появится после наполнения базы.'}</div>
        </div>`;
      target.appendChild(card);
    });
  }

  function renderFavorites() {
    const target = $('#favoritesList');
    const list = loadFavorites();
    setText('#favoritesCounter', `Избранное: ${list.length}`);
    if (!list.length) return showEmpty(target, 'Здесь будут события, которые пользователь добавил в избранное.');
    target.innerHTML = '';
    list.forEach((item) => target.appendChild(createFavoriteCard(item, {
      onOpen: handleOpenEvent,
      onRemove: (id) => {
        saveFavorites(loadFavorites().filter((x) => String(x.id) !== String(id)));
        renderFavorites();
        renderEvents();
      }
    })));
  }

  function showStatsInAlert(stat, event) {
    if (stat) alert(`Событие: ${event.title}\nURI: ${stat.uri}\nПросмотров: ${stat.hits}`);
    else alert(`Для события "${event.title}" статистика по URI ${App.eventUri(event.id)} пока не найдена.`);
  }

  function handleOpenEvent(id) { openEventModal(id, { onShowStats: showStatsInAlert }); }

  async function loadCategories() {
    try {
      state.categories = await apiFetch('/api/main/categories?from=0&size=30');
      renderCategoryChips();
      setText('#categoriesCounter', `Категории: ${state.categories.length}`);
    } catch (error) {
      showEmpty($('#categoriesChips'), `Не удалось загрузить категории: ${error.message}`);
    }
  }
  async function loadCompilations() {
    try {
      state.compilations = await apiFetch('/api/main/compilations?from=0&size=6');
      renderCompilations();
    } catch (error) {
      showEmpty($('#compilationsList'), `Не удалось загрузить подборки: ${error.message}`);
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

  function resetFilters() {
    $('#eventsForm').reset();
    $('#eventsForm [name="from"]').value = '0';
    $('#eventsForm [name="size"]').value = '12';
    $('#eventsForm [name="categories"]').value = '';
    state.selectedCategoryId = '';
    renderCategoryChips();
    loadEvents();
  }

  function initRoleContent() {
    const session = loadSession();
    if (session.role !== 'guest') {
      $('#heroLoginBtn').textContent = 'Открыть личный кабинет';
      $('#heroLoginBtn').onclick = () => { location.href = session.role === 'admin' ? '/admin.html' : '/user.html'; };
    }
  }

  function attachPageHandlers() {
    $('#eventsForm').addEventListener('submit', (e) => { e.preventDefault(); loadEvents(); });
    $('#resetEventsBtn').addEventListener('click', resetFilters);
    $('#clearCategoryBtn').addEventListener('click', () => { state.selectedCategoryId = ''; $('#eventsForm [name="categories"]').value = ''; renderCategoryChips(); loadEvents(); });
    $('#clearFavoritesBtn').addEventListener('click', () => { saveFavorites([]); renderFavorites(); renderEvents(); });
    window.addEventListener('favorites:changed', () => { renderFavorites(); renderEvents(); });
  }

  async function init() {
    attachAuthHandlers();
    attachModalHandlers();
    attachPageHandlers();
    initRoleContent();
    renderFavorites();
    await Promise.allSettled([loadCategories(), loadCompilations(), loadEvents()]);
  }

  init();
})();
