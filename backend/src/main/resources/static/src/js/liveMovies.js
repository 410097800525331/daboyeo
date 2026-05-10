/**
 * liveMovies.js - URL 파라미터와 실시간 상영 데이터를 처리한다.
 */

const movieGrid = document.getElementById('movie-grid');
const DEFAULT_API_BASE_ORIGIN = 'http://localhost:5500';
const FRONTEND_DEV_PORTS = new Set(['4173', '5173', '5510']);
const API_BASE_URL = resolveApiBaseUrl();

function resolveApiBaseUrl() {
  const override = typeof window !== 'undefined' ? window.DABOYEO_API_BASE_URL : '';
  if (!override && window.location.protocol.startsWith('http') && !FRONTEND_DEV_PORTS.has(window.location.port)) {
    return `${window.location.origin}/api`;
  }
  const baseOrigin = normalizeApiOrigin(override || DEFAULT_API_BASE_ORIGIN);
  return `${baseOrigin}/api`;
}

function normalizeApiOrigin(value) {
  return String(value || DEFAULT_API_BASE_ORIGIN).replace(/\/+$/, '');
}

function createElement(tagName, className, textContent) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  if (textContent !== undefined && textContent !== null) {
    element.textContent = String(textContent);
  }
  return element;
}

function appendText(parent, text) {
  parent.appendChild(document.createTextNode(text));
}

function normalizeText(value, fallback = '') {
  const text = value === undefined || value === null ? '' : String(value);
  return text.trim() || fallback;
}

function isWithinSelectedTimeRange(itemTime) {
  const start = normalizeText(currentSearchConfig.timeStart, '06:00');
  const end = normalizeText(currentSearchConfig.timeEnd, '23:59');
  const target = normalizeText(itemTime);

  if (!target) {
    return false;
  }

  if (end < start) {
    return target >= start || target <= end;
  }

  return target >= start && target <= end;
}

function cssToken(value) {
  return normalizeText(value, 'ALL').replace(/[^0-9A-Z]/gi, '').toUpperCase() || 'ALL';
}

function providerColor(provider) {
  if (provider === 'CGV') return '#E71A0F';
  if (provider === 'LOTTE') return '#FF8C00';
  return '#361771';
}

function showMessage(container, message, inlineStyle = '') {
  container.replaceChildren();
  const emptyBox = createElement('div', 'loading-container');
  if (inlineStyle) emptyBox.setAttribute('style', inlineStyle);

  const icon = createElement('i', 'fas fa-magnifying-glass-chart');
  icon.style.fontSize = '3rem';
  icon.style.color = 'var(--purple50)';
  icon.style.marginBottom = '20px';
  icon.style.opacity = '0.5';

  const text = createElement('p', 'loading-title', message);
  text.style.fontSize = '1.2rem';
  text.style.opacity = '0.8';

  emptyBox.append(icon, text);
  container.appendChild(emptyBox);
}

const MOCK_DEFAULTS = {
  lat: 37.4979,
  lng: 127.0276,
  regionName: '서울 강남구',
  date: new Date().toISOString().split('T')[0],
  timeStart: '06:00',
  timeEnd: '23:59',
};

let currentSearchConfig = { ...MOCK_DEFAULTS };
let regionResolutionPromise = null;

let allRawSchedules = [];
let modalRawSchedules = [];
let currentSelectedMovie = null;
let currentSelectedMovieKey = null;
let currentTab = 'ALL';
let latestSearchMeta = null;
let pendingRetryTimeoutId = null;

function initSearchParams() {
  const params = new URLSearchParams(window.location.search);

  // 1. Try to load from sessionStorage (saved by script.js)
  try {
    const rawContext = sessionStorage.getItem('daboyeoSearchContext');
    if (rawContext) {
      const context = JSON.parse(rawContext);
      if (context.date) currentSearchConfig.date = context.date;
      if (context.region) currentSearchConfig.regionName = context.region;

      // Map timeRange to actual hours
      if (context.timeRange === 'morning') {
        currentSearchConfig.timeStart = '06:00';
        currentSearchConfig.timeEnd = '10:59';
      } else if (context.timeRange === 'brunch') {
        currentSearchConfig.timeStart = '11:00';
        currentSearchConfig.timeEnd = '16:59';
      } else if (context.timeRange === 'night') {
        currentSearchConfig.timeStart = '17:00';
        currentSearchConfig.timeEnd = '06:00';
      }
    }
  } catch (e) {
    console.error('Failed to parse search context', e);
  }

  // 2. Override with URL params if they exist
  if (params.has('lat')) currentSearchConfig.lat = parseFloat(params.get('lat'));
  if (params.has('lng')) currentSearchConfig.lng = parseFloat(params.get('lng'));
  if (params.has('region')) currentSearchConfig.regionName = params.get('region');
  if (params.has('date')) currentSearchConfig.date = params.get('date');
  if (params.has('timeStart')) currentSearchConfig.timeStart = params.get('timeStart');
  if (params.has('timeEnd')) currentSearchConfig.timeEnd = params.get('timeEnd');

  updateSearchInfoUI();
}

function updateSearchInfoUI() {
  const infoArea = document.getElementById('search-info-text');
  if (!infoArea) return;

  infoArea.innerHTML = '';

  const region = createElement('strong', 'highlight-text', currentSearchConfig.regionName || '전체');
  const date = createElement('strong', 'highlight-text', currentSearchConfig.date);

  infoArea.appendChild(region);
  infoArea.appendChild(document.createTextNode(' 주변, '));
  infoArea.appendChild(date);

  const timeText = ` 기준 (시간: ${currentSearchConfig.timeStart} ~ ${currentSearchConfig.timeEnd})`;
  infoArea.appendChild(document.createTextNode(timeText));
}

function hasExplicitCoordinates() {
  const lat = Number(currentSearchConfig.lat);
  const lng = Number(currentSearchConfig.lng);
  // Only return true if coordinates are NOT the default Seoul City Hall ones
  return lat !== 0 && lng !== 0 && lat !== MOCK_DEFAULTS.lat && lng !== MOCK_DEFAULTS.lng;
}

function shouldResolveRegionCoordinates() {
  const region = normalizeText(currentSearchConfig.regionName);
  return !hasExplicitCoordinates() && region !== '' && region !== '전체';
}

function waitForKakaoServices(timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const startedAt = Date.now();

    function check() {
      if (window.kakao?.maps?.services) {
        resolve(window.kakao.maps.services);
        return;
      }
      if (Date.now() - startedAt >= timeoutMs) {
        reject(new Error('Kakao services SDK not ready.'));
        return;
      }
      window.setTimeout(check, 100);
    }

    check();
  });
}

async function resolveCoordinatesFromRegion(regionText) {
  const services = await waitForKakaoServices();
  const query = normalizeText(regionText);
  if (!query) {
    return null;
  }

  const geocoder = new services.Geocoder();
  const addressResult = await new Promise((resolve) => {
    geocoder.addressSearch(query, (result, status) => {
      if (status === window.kakao.maps.services.Status.OK && result.length > 0) {
        resolve({
          lat: parseFloat(result[0].y),
          lng: parseFloat(result[0].x),
          label: result[0].address_name,
        });
        return;
      }
      resolve(null);
    });
  });
  if (addressResult) {
    return addressResult;
  }

  const places = new services.Places();
  const keywordResult = await new Promise((resolve) => {
    places.keywordSearch(query, (data, status) => {
      if (status === window.kakao.maps.services.Status.OK && data.length > 0) {
        resolve({
          lat: parseFloat(data[0].y),
          lng: parseFloat(data[0].x),
          label: data[0].place_name,
        });
        return;
      }
      resolve(null);
    });
  });

  return keywordResult;
}

async function ensureSearchCoordinates() {
  if (!shouldResolveRegionCoordinates()) {
    return;
  }

  if (!regionResolutionPromise) {
    regionResolutionPromise = resolveCoordinatesFromRegion(currentSearchConfig.regionName)
      .then((resolved) => {
        if (!resolved || !Number.isFinite(resolved.lat) || !Number.isFinite(resolved.lng)) {
          return;
        }

        currentSearchConfig.lat = resolved.lat;
        currentSearchConfig.lng = resolved.lng;
        updateSearchInfoUI();
      })
      .catch((error) => {
        console.error('Region coordinate resolution failed:', error);
      })
      .finally(() => {
        regionResolutionPromise = null;
      });
  }

  await regionResolutionPromise;
}

function clearPendingRetry() {
  if (pendingRetryTimeoutId) {
    window.clearTimeout(pendingRetryTimeoutId);
    pendingRetryTimeoutId = null;
  }
}

function showPendingMessage(container, message) {
  container.replaceChildren();
  const box = createElement('div', 'loading-container');

  const iconWrap = createElement('div', 'loader-visual');
  iconWrap.innerHTML = `
    <div class="loader-circle"></div>
    <div class="loader-pulse"></div>
    <i class="fas fa-database loader-icon"></i>
  `;

  const title = createElement('p', 'loading-title', '주변 시네마 데이터를 아직 모으는 중이야.');
  const subtitle = createElement('p', 'loading-subtitle', message || '잠깐만 기다리면 자동으로 다시 확인할게.');
  subtitle.style.maxWidth = '34rem';
  subtitle.style.margin = '0 auto';
  subtitle.style.textAlign = 'center';

  const retryText = createElement('p', 'loading-subtitle', '3초 뒤 자동으로 다시 불러와.');
  retryText.style.opacity = '0.75';
  retryText.style.marginTop = '14px';

  box.append(iconWrap, title, subtitle, retryText);
  container.appendChild(box);
}

async function loadLiveMovies(options = {}) {
  if (!movieGrid) return;
  const { skipLoader = false } = options;
  clearPendingRetry();

  if (!skipLoader) {
    movieGrid.innerHTML = `
    <div class="loading-container">
      <div class="loader-visual">
        <div class="loader-circle"></div>
        <div class="loader-pulse"></div>
        <i class="fas fa-satellite-dish loader-icon"></i>
      </div>
      <div class="loading-text-group">
        <div class="loading-title">지금 <span>전국 시네마 데이터</span>를</div>
        <div class="loading-title">실시간으로 연결하고 있어요</div>
        <p class="loading-subtitle">
          조금만 기다려주세요! 더미 데이터가 아닌, <br>
          전국의 실제 상영 시간을 꼼꼼하게 수집하고 있습니다.
        </p>
      </div>
      <div class="data-stream-icons">
        <i class="fas fa-film"></i>
        <i class="fas fa-ticket"></i>
        <i class="fas fa-map-location-dot"></i>
      </div>
    </div>
    `;
  }

  try {
    await ensureSearchCoordinates();

    const { lat, lng, date, timeStart, timeEnd } = currentSearchConfig;
    const searchParams = new URLSearchParams({
      lat: String(lat),
      lng: String(lng),
      date,
      timeStart,
      timeEnd,
    });
    const response = await fetch(`${API_BASE_URL}/live/nearby?${searchParams.toString()}`);
    const data = await response.json();
    latestSearchMeta = data?.search || null;

    if (!data || !Array.isArray(data.results)) {
      showMessage(movieGrid, '데이터 형식이 올바르지 않아.');
      return;
    }

    if (data.search?.pendingRefresh) {
      pendingRetryTimeoutId = window.setTimeout(() => {
        void loadLiveMovies({ skipLoader: true });
      }, 3000);
      if (data.results.length === 0) {
        showPendingMessage(movieGrid, data.search.warning);
        return;
      }
    }

    allRawSchedules = data.results.map((item) => ({
      ...item,
      normalized: {
        title: item.movie_name || item.movie_nm || '제목 없음',
        provider: item.provider === 'LOTTE_CINEMA' ? 'LOTTE' : item.provider === 'MEGABOX' ? 'MEGA' : item.provider || 'ETC',
        format: item.format_name || item.screen_name || item.screen_division_name || '2D',
        theater: item.theater_name || item.cinema_name || '미정',
        rating: item.age_rating || item.age_rating_name || 'ALL',
        total_seats: item.total_seat_count || 100,
        available_seats: item.available_seat_count || item.remaining_seat_count || 0,
        start_time: item.start_time || '00:00',
      },
    }));

    applyFilters();
  } catch (error) {
    console.error('API Error:', error);
    showMessage(movieGrid, '백엔드 서버에 연결하지 못했어.');
  }
}

function applyFilters() {
  const getActiveValues = (id) => {
    const container = document.getElementById(id);
    if (!container) return ['all'];
    const chips = container.querySelectorAll('.filter-chip.active');
    return Array.from(chips).map((chip) => chip.getAttribute('data-value'));
  };

  const providerFilters = getActiveValues('check-provider');
  const theaterFilters = getActiveValues('check-theater');
  const seatTypeFilters = getActiveValues('check-seat-type');
  const seatFilters = getActiveValues('check-seats');
  const searchFilterValue = document.getElementById('filter-search')
    ? document.getElementById('filter-search').value.trim().toLowerCase()
    : '';

  const filtered = allRawSchedules.filter((item) => {
    // 0. Filter out past showtimes for today
    const now = new Date();
    const todayStr = now.toISOString().split('T')[0];
    if (currentSearchConfig.date === todayStr) {
      const currentH = now.getHours();
      const currentM = now.getMinutes();
      const currentTimeStr = `${String(currentH).padStart(2, '0')}:${String(currentM).padStart(2, '0')}`;
      if (item.normalized.start_time < currentTimeStr) return false;
    }
    const norm = item.normalized;
    const matchProvider = providerFilters.includes('all') || providerFilters.includes(norm.provider);
    const matchTheater = theaterFilters.includes('all') || theaterFilters.some((filterValue) => norm.format.toUpperCase().includes(filterValue.toUpperCase()));
    const matchSeatType = seatTypeFilters.includes('all') || seatTypeFilters.some((filterValue) => norm.format.toUpperCase().includes(filterValue.toUpperCase()));
    const matchSearch = searchFilterValue === ''
      || norm.title.toLowerCase().includes(searchFilterValue)
      || norm.theater.toLowerCase().includes(searchFilterValue);

    const matchTime = isWithinSelectedTimeRange(norm.start_time);

    const seatRatio = norm.total_seats > 0 ? (norm.available_seats / norm.total_seats) * 100 : 0;
    let matchSeats = seatFilters.includes('all');
    if (seatFilters.includes('spacious')) matchSeats = matchSeats || seatRatio >= 50;
    if (seatFilters.includes('comfortable')) matchSeats = matchSeats || seatRatio >= 30;
    if (seatFilters.includes('closing')) matchSeats = matchSeats || (seatRatio < 10 && norm.available_seats > 0);
    if (seatFilters.includes('group')) matchSeats = matchSeats || norm.available_seats >= 20;

    return matchProvider && matchTheater && matchSeatType && matchSeats && matchSearch && matchTime;
  });

  const aggregated = filtered.reduce((acc, current) => {
    const title = current.normalized.title;
    if (!acc[title]) {
      acc[title] = {
        movieKey: current.movie_key || current.movie_name,
        title,
        providers: new Set([current.normalized.provider]),
        rating: current.normalized.rating,
        max_available: current.normalized.available_seats,
        total_matches: 1,
      };
    } else {
      acc[title].providers.add(current.normalized.provider);
      acc[title].max_available = Math.max(acc[title].max_available, current.normalized.available_seats);
      acc[title].total_matches += 1;
    }
    return acc;
  }, {});

  renderMovieCards(Object.values(aggregated));
}

function renderMovieCards(movies) {
  if (!movieGrid) return;
  movieGrid.innerHTML = '';

  const resultCount = document.getElementById('result-count');
  if (resultCount) resultCount.innerText = movies.length;

  if (movies.length === 0) {
    const emptyMessage = latestSearchMeta?.warning && !latestSearchMeta.pendingRefresh
      ? latestSearchMeta.warning
      : '조건에 맞는 상영 데이터가 없어.';
    showMessage(movieGrid, emptyMessage, 'padding: 5rem 0; opacity: 0.5;');
    return;
  }

  movies.forEach((movie) => {
    const card = document.createElement('div');
    card.className = 'movie-card';
    card.addEventListener('click', () => window.openModal(movie));

    const posterContainer = createElement('div', 'poster-container');
    const poster = document.createElement('img');
    poster.src = getPosterUrl(movie.title);
    poster.alt = normalizeText(movie.title, '영화 포스터');

    const rating = normalizeText(movie.rating, 'ALL');
    const ratingBadge = createElement('div', `rating-badge rating-${cssToken(rating)}`, rating);
    posterContainer.append(poster, ratingBadge);

    const movieInfo = createElement('div', 'movie-info');
    movieInfo.appendChild(createElement('h3', null, normalizeText(movie.title, '제목 없음')));

    const providerList = createElement('div', 'provider-list');
    Array.from(movie.providers).forEach((provider) => {
      const providerText = normalizeText(provider, 'ETC');
      const providerTag = createElement('span', 'provider-tag', providerText);
      providerTag.style.background = providerColor(providerText);
      providerList.appendChild(providerTag);
    });
    movieInfo.appendChild(providerList);

    const seatInfo = createElement('div', 'seat-info-summary');
    const chairIcon = createElement('i', 'fas fa-chair');
    const seatCount = createElement('b', null, movie.max_available);
    seatInfo.appendChild(chairIcon);
    appendText(seatInfo, ' 최대 ');
    seatInfo.appendChild(seatCount);
    appendText(seatInfo, '석 가능');
    movieInfo.appendChild(seatInfo);

    const timeButton = createElement('button', 'btn-check-time', '상세 시간표 보기');
    timeButton.type = 'button';
    movieInfo.appendChild(timeButton);

    card.append(posterContainer, movieInfo);
    movieGrid.appendChild(card);
  });
}

window.openModal = async function openModal(movie) {
  currentSelectedMovie = typeof movie === 'string' ? movie : movie.title;
  currentSelectedMovieKey = typeof movie === 'string' ? movie : movie.movieKey;
  const modal = document.getElementById('schedule-modal');
  if (!modal) return;

  const titleEl = document.getElementById('modal-movie-title');
  const posterEl = document.getElementById('modal-movie-poster');

  if (titleEl) titleEl.innerText = currentSelectedMovie;
  if (posterEl) posterEl.src = getPosterUrl(currentSelectedMovie);

  modal.classList.add('active');
  document.body.style.overflow = 'hidden';
  await fetchMovieSchedulesForModal();
  window.switchTab('ALL');
};

window.closeModal = function closeModal() {
  const modal = document.getElementById('schedule-modal');
  if (modal) modal.classList.remove('active');
  document.body.style.overflow = 'auto';
  modalRawSchedules = [];
};

window.switchTab = function switchTab(provider) {
  currentTab = provider;
  document.querySelectorAll('.tab-btn').forEach((button) => {
    const isMatched = (provider === 'ALL' && button.innerText.includes('전체'))
      || button.innerText.toUpperCase().includes(provider);
    button.classList.toggle('active', isMatched);
  });
  renderScheduleList();
};

function renderScheduleList() {
  const listEl = document.getElementById('modal-schedule-list');
  if (!listEl) return;
  listEl.innerHTML = '';

  const sourceSchedules = modalRawSchedules.length > 0 ? modalRawSchedules : allRawSchedules;
  const targetSchedules = sourceSchedules.filter((item) => {
    const norm = item.normalized;
    const matchMovie = norm.title === currentSelectedMovie;
    const matchProvider = currentTab === 'ALL' || norm.provider === currentTab;
    const matchTime = isWithinSelectedTimeRange(norm.start_time);

    return matchMovie && matchProvider && matchTime;
  });

  const theaterGroups = targetSchedules.reduce((acc, curr) => {
    const theater = curr.normalized.theater;
    if (!acc[theater]) acc[theater] = [];
    acc[theater].push(curr.normalized);
    return acc;
  }, {});

  if (Object.keys(theaterGroups).length === 0) {
    showMessage(listEl, '해당 조건의 상영 정보가 없어.', 'padding: 3rem 0;');
    return;
  }

  Object.entries(theaterGroups).forEach(([theater, times]) => {
    const groupEl = document.createElement('div');
    groupEl.className = 'schedule-item';
    const provider = times[0].provider;

    const theaterInfo = createElement('div', 'theater-info');
    theaterInfo.appendChild(createElement('span', 'theater-name', theater));
    const theaterBrand = createElement('span', 'theater-brand', normalizeText(provider, 'ETC'));
    theaterBrand.style.background = providerColor(provider);
    theaterInfo.appendChild(theaterBrand);

    const timeSlots = createElement('div', 'time-slots');
    times.forEach((timeItem) => {
      const isClosing = timeItem.available_seats < 10 && timeItem.available_seats > 0;
      const timeCard = createElement('div', 'time-card');
      timeCard.addEventListener('click', () => {
        alert(`${normalizeText(timeItem.theater, '극장')} ${normalizeText(timeItem.start_time, '00:00')} 예매 페이지로 이동할게.`);
      });
      timeCard.appendChild(createElement('span', 'start-time', normalizeText(timeItem.start_time, '00:00')));
      timeCard.appendChild(createElement('span', 'hall-info', normalizeText(timeItem.format, '2D')));

      const seatsClass = timeItem.available_seats === 0 ? 'seats sold-out' : 'seats';
      const seatsText = timeItem.available_seats === 0
        ? '매진'
        : (isClosing ? `임박 ${timeItem.available_seats}석` : `${timeItem.available_seats}석`);
      timeCard.appendChild(createElement('span', seatsClass, seatsText));
      timeSlots.appendChild(timeCard);
    });

    groupEl.append(theaterInfo, timeSlots);
    listEl.appendChild(groupEl);
  });
}

async function fetchMovieSchedulesForModal() {
  if (!currentSelectedMovieKey) return;

  try {
    const { lat, lng, date, timeStart, timeEnd } = currentSearchConfig;
    const searchParams = new URLSearchParams({
      lat: String(lat),
      lng: String(lng),
      date,
      timeStart,
      timeEnd,
    });
    const response = await fetch(`${API_BASE_URL}/live/movies/${encodeURIComponent(currentSelectedMovieKey)}/schedules?${searchParams.toString()}`);
    if (!response.ok) {
      return;
    }

    const data = await response.json();
    if (!data || !Array.isArray(data.theaters)) {
      return;
    }

    const nextSchedules = [];
    data.theaters.forEach((group) => {
      (group.schedules || []).forEach((schedule) => {
        nextSchedules.push({
          movie_key: data.movie?.movie_key || currentSelectedMovieKey,
          movie_name: data.movie?.movie_name || currentSelectedMovie,
          provider: group.provider,
          provider_code: group.provider_code,
          theater_id: group.theater_id,
          theater_name: group.theater_name,
          format_name: schedule.format_name,
          age_rating: data.movie?.age_rating || 'ALL',
          start_time: schedule.start_time,
          end_time: schedule.end_time,
          total_seat_count: schedule.total_seat_count,
          available_seat_count: schedule.available_seat_count,
          booking_url: schedule.booking_url,
          normalized: {
            title: data.movie?.movie_name || currentSelectedMovie,
            provider: group.provider || 'ETC',
            format: schedule.format_name || '2D',
            theater: group.theater_name || '미정',
            rating: data.movie?.age_rating || 'ALL',
            total_seats: schedule.total_seat_count || 100,
            available_seats: schedule.available_seat_count || 0,
            start_time: schedule.start_time || '00:00',
            booking_url: schedule.booking_url || '',
          },
        });
      });
    });

    if (nextSchedules.length > 0) {
      modalRawSchedules = nextSchedules;
    }
  } catch (error) {
    console.error('Modal API Error:', error);
  }
}

window.addEventListener('click', (event) => {
  const modal = document.getElementById('schedule-modal');
  if (event.target === modal) window.closeModal();
});

function getPosterUrl() {
  return 'https://search.pstatic.net/common/?src=http%3A%2F%2Fattachment.namu.wiki%2Fmovie_poster.jpg&type=f308_432';
}

window.toggleChip = function toggleChip(element) {
  const parent = element.parentElement;
  const chips = parent.querySelectorAll('.filter-chip');
  const isAll = element.getAttribute('data-value') === 'all';

  if (isAll) {
    chips.forEach((chip) => chip.classList.remove('active'));
    element.classList.add('active');
  } else {
    element.classList.toggle('active');
    const allButton = parent.querySelector('[data-value="all"]');
    if (allButton) allButton.classList.remove('active');
    const actives = Array.from(chips).filter((chip) => chip.classList.contains('active'));
    if (actives.length === 0 && allButton) allButton.classList.add('active');
  }

  applyFilters();
};

document.addEventListener('DOMContentLoaded', () => {
  initSearchParams();
  void loadLiveMovies();
});
