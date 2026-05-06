import {
  createRecommendationSession,
  deleteRecommendationSession,
  getPosterSeed,
  requestRecommendations,
  sendRecommendationFeedback,
} from "../api/client.js?v=20260506-codex-only";

const STORAGE_KEY = "daboyeoAnonymousId";
const SEARCH_CONTEXT_KEY = "daboyeoSearchContext";
const MAIN_PAGE_URL = "../../index.html";
const SEAT_RECOMMEND_URL = "./seatRecommendMbti.html?flow=ai-result";
const POSTER_LIMIT = 18;
const POSTER_BATCH_SIZE = 6;
const MIN_LIKED_POSTERS = 3;
const LIKE_LIMIT = 5;
const GENRE_LIMIT = 1;
const AUTO_ADVANCE_MS = 320;
const DEFAULT_AI_PROVIDER = "codex";
const PROVIDER_BOOKING_URLS = {
  cgv: "https://m.cgv.co.kr/WebApp/Reservation/seat.aspx",
  lotte: "https://www.lottecinema.co.kr/NLCHS/Ticketing",
  lottecinema: "https://www.lottecinema.co.kr/NLCHS/Ticketing",
  megabox: "https://www.megabox.co.kr/booking",
  mega: "https://www.megabox.co.kr/booking",
};

const audienceOptions = [
  { value: "alone", label: "혼자", hint: "혼자서도 몰입할 수 있는 영화로 볼래." },
  { value: "friends", label: "친구", hint: "대화도 되고 텐션도 사는 영화로 볼래" },
  { value: "date", label: "연인", hint: "분위기 살리고 함께 즐길 수 있는 영화로 볼래." },
  { value: "family", label: "가족", hint: "온 가족이 함께 보기 좋은 영화로 볼래." },
  { value: "child", label: "아이와 함께", hint: "아이와 함께 즐겁게 볼 수 있는 영화로 볼래." },
];

const moodOptions = [
  { value: "light", label: "가볍게 즐기기", hint: "부담 없이 편하게 볼 수 있는 작품 위주로." },
  { value: "immersive", label: "깊게 몰입하기", hint: "서사와 여운이 있는 작품 위주로." },
  { value: "exciting", label: "신나게 즐기기", hint: "속도감 있고 재미로 몰아치는 작품 위주로." },
  { value: "calm", label: "잔잔하게 보기", hint: "편안하게 흘러가는 감성적인 작품 위주로." },
  { value: "tense", label: "긴장감 있게 보기", hint: "손에 땀을 쥐게 하는 긴장감 있는 작품 위주로." },
];

const avoidOptions = [
  { value: "too_long", label: "긴 상영시간" },
  { value: "complex", label: "복잡한 이야기" },
  { value: "romance", label: "로맨스 중심 전개" },
  { value: "violence", label: "잔인한 장면" },
  { value: "sad_ending", label: "슬픈 결말" },
  { value: "reality", label: "현실감 없는 설정" },
  { value: "loud", label: "시끄러운 연출" },
  { value: "none", label: "해당 없음" },
];

const genreOptions = [
  { value: "action", label: "액션" },
  { value: "animation", label: "애니" },
  { value: "horror", label: "공포" },
  { value: "thriller", label: "스릴러" },
  { value: "sf", label: "SF" },
  { value: "adventure", label: "어드벤처" },
  { value: "comedy", label: "코미디" },
  { value: "romance", label: "로맨스" },
  { value: "drama", label: "드라마" },
  { value: "fantasy", label: "판타지" },
  { value: "crime", label: "범죄" },
  { value: "history", label: "역사" },
  { value: "music", label: "음악" },
  { value: "family", label: "가족" },
];

const modeOptions = [
  {
    value: "fast",
    label: "빠른 추천",
    model: "GPT (Codex) · fast",
    description: "짧은 이유와 함께, 바로 볼 수 있는\n영화를 빠르게 추천해 드립니다.",
    tags: ["GPT", "빠름", "간단한 이유", "상위 후보"],
  },
  {
    value: "precise",
    label: "정밀 추천",
    model: "GPT (Codex) · precise",
    description: "후보를 꼼꼼히 비교하고, 포스터 취향까지\n반영해 더 정확하게 추천해 드립니다.",
    tags: ["GPT", "정밀 분석", "포스터 취향", "후보 비교"],
    recommended: true,
  },
];

const providerInfo = {
  value: "codex",
  label: "GPT",
  title: "GPT (Codex)",
};

const modeProfiles = {
  fast: {
    model: "GPT (Codex) · fast",
    description: "후보 수를 줄여 가볍게 비교하고\n바로 볼 만한 영화만 먼저 추려드릴게요.",
    tags: ["GPT", "Codex", "가벼운 분석", "소수 후보"],
  },
  precise: {
    model: "GPT (Codex) · precise",
    description: "GPT로 후보와 취향 근거를 더 꼼꼼하게 비교해서\n어색한 후보를 줄여볼게요.",
    tags: ["GPT", "Codex", "정밀 분석", "후보 비교"],
  },
};

const progressText = {
  audience: "1 / 6 상황",
  mood: "2 / 6 컨디션",
  avoid: "3 / 6 상황",
  genres: "4 / 6 장르",
  posters: "5 / 6 포스터",
  mode: "6 / 6 방식",
  loading: "분석 중",
  results: "추천 완료",
  empty: "후보 없음",
  error: "오류",
  sessionError: "연결 필요",
};

const stepBackMap = {
  mood: "audience",
  avoid: "mood",
  genres: "avoid",
  posters: "genres",
  mode: "posters",
};

const timeRangeLabels = {
  morning: "조조",
  brunch: "브런치",
  night: "야간",
};

const screen = document.getElementById("aiScreen");
const backButton = document.getElementById("aiBackButton");
const progress = document.getElementById("aiProgress");
const toast = document.getElementById("aiToast");
const topbarLeft = document.querySelector(".ai-topbar-left");

const state = {
  step: "audience",
  sessionStatus: "pending",
  anonymousId: localStorage.getItem(STORAGE_KEY),
  aiProvider: DEFAULT_AI_PROVIDER,
  survey: {
    audience: null,
    mood: null,
    avoid: [],
    preferredGenres: [],
  },
  searchContext: null,
  posters: {
    status: "idle",
    items: [],
    error: null,
    activeBatchIndex: 0,
    genresKey: "",
  },
  posterChoices: {
    likedSeedMovieIds: [],
    dislikedSeedMovieIds: [],
  },
  run: {
    status: "idle",
    mode: null,
    aiProvider: null,
    response: null,
    error: null,
  },
  feedback: new Map(),
  stepTimer: null,
  toastTimer: null,
};

state.searchContext = readSearchContext();

function modeOption(value) {
  return modeOptions.find((option) => option.value === value) || modeOptions[0];
}

function modeProfile(modeValue) {
  const option = modeOption(modeValue);
  return {
    ...option,
    ...(modeProfiles[option.value] || {}),
  };
}

function goToMainPage() {
  window.location.href = MAIN_PAGE_URL;
}

function goToPreviousPage() {
  if (window.history.length > 1) {
    window.history.back();
    return;
  }

  goToMainPage();
}

function resetBackButtonToTopbar() {
  backButton.hidden = false;
  backButton.style.position = "";
  backButton.style.top = "";
  backButton.style.left = "";
  backButton.style.marginBottom = "";

  if (topbarLeft && backButton.parentElement !== topbarLeft) {
    topbarLeft.insertBefore(backButton, topbarLeft.firstChild);
  }
}

function shuffleItems(items) {
  const copy = [...items];

  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    const current = copy[i];
    copy[i] = copy[j];
    copy[j] = current;
  }

  return copy;
}

function createElement(tagName, className, text) {
  const element = document.createElement(tagName);

  if (Array.isArray(className)) {
    element.classList.add(...className.filter(Boolean));
  } else if (className) {
    element.className = className;
  }

  if (text !== undefined && text !== null) {
    element.textContent = text;
  }

  return element;
}

function clearChildren(element) {
  while (element.firstChild) {
    element.removeChild(element.firstChild);
  }
}

function normalizeSearchContext(raw) {
  if (!raw || typeof raw !== "object") {
    return null;
  }

  const personCount = Number(raw.personCount);
  const normalized = {
    region: typeof raw.region === "string" ? raw.region.trim() : "",
    date: typeof raw.date === "string" ? raw.date.trim() : "",
    timeRange: typeof raw.timeRange === "string" ? raw.timeRange.trim().toLowerCase() : "",
    personCount: Number.isFinite(personCount) && personCount > 0 ? Math.floor(personCount) : null,
  };

  return hasSearchContext(normalized) ? normalized : null;
}

function hasSearchContext(context) {
  return Boolean(
    context
      && (context.region
        || context.date
        || context.timeRange
        || context.personCount),
  );
}

function readSearchContext() {
  try {
    const raw = sessionStorage.getItem(SEARCH_CONTEXT_KEY);
    return raw ? normalizeSearchContext(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

function searchContextRegion(context) {
  const region = context?.region?.trim() || "";
  return region && region !== "전체" ? region : "";
}

function searchContextParts(context) {
  if (!hasSearchContext(context)) {
    return [];
  }

  const parts = [];
  const region = searchContextRegion(context);
  if (region) {
    parts.push(region);
  }
  if (context.date) {
    parts.push(context.date);
  }
  if (context.timeRange && timeRangeLabels[context.timeRange]) {
    parts.push(timeRangeLabels[context.timeRange]);
  }
  if (context.personCount) {
    parts.push(`${context.personCount}명`);
  }
  return parts;
}

function searchContextText(context) {
  const parts = searchContextParts(context);
  return parts.length > 0 ? parts.join(" · ") : "";
}

function buildSearchFiltersPayload() {
  if (!hasSearchContext(state.searchContext)) {
    return null;
  }

  return {
    region: searchContextRegion(state.searchContext),
    date: state.searchContext.date || null,
    timeRange: state.searchContext.timeRange || null,
    personCount: state.searchContext.personCount || null,
  };
}

function optionLabel(options, value) {
  return options.find((option) => option.value === value)?.label || "아직 선택 안 함";
}

function optionLabels(options, values) {
  return (Array.isArray(values) ? values : [])
    .map((value) => optionLabel(options, value))
    .filter(Boolean)
    .join(", ");
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("is-visible");

  if (state.toastTimer) {
    window.clearTimeout(state.toastTimer);
  }

  state.toastTimer = window.setTimeout(() => {
    toast.classList.remove("is-visible");
  }, 2400);
}

function scheduleStep(nextStep, delay = AUTO_ADVANCE_MS) {
  if (state.stepTimer) {
    window.clearTimeout(state.stepTimer);
  }

  state.stepTimer = window.setTimeout(() => {
    setStep(nextStep);
  }, delay);
}

function clearStateFromStep(stepToClear) {
  const steps = ["audience", "mood", "avoid", "genres", "posters", "mode"];
  const startIndex = steps.indexOf(stepToClear);
  if (startIndex === -1) return;

  for (let i = startIndex; i < steps.length; i++) {
    switch (steps[i]) {
      case "audience":
        state.survey.audience = null;
        break;
      case "mood":
        state.survey.mood = null;
        break;
      case "avoid":
        state.survey.avoid = [];
        break;
      case "genres":
        state.survey.preferredGenres = [];
        break;
      case "posters":
        state.posterChoices.likedSeedMovieIds = [];
        state.posters.activeBatchIndex = 0;
        break;
    }
  }
}

function setStep(nextStep) {
  if (state.stepTimer) {
    window.clearTimeout(state.stepTimer);
    state.stepTimer = null;
  }

  const performStepChange = () => {
    state.step = nextStep;
    if (nextStep === "posters") {
      ensurePostersLoaded();
    }
    render();
    screen.classList.add("is-entering");
    setTimeout(() => screen.classList.remove("is-entering"), 400);
    screen.focus({ preventScroll: true });
  };

  if (state.step && screen.firstChild) {
    screen.classList.add("is-exiting");
    setTimeout(() => {
      screen.classList.remove("is-exiting");
      performStepChange();
    }, 350);
  } else {
    performStepChange();
  }
}

function resetInputs(keepSession = true) {
  state.step = "audience";
  state.survey = { audience: null, mood: null, avoid: [], preferredGenres: [] };
  state.posterChoices = { likedSeedMovieIds: [], dislikedSeedMovieIds: [] };
  state.posters.activeBatchIndex = 0;
  state.run = { status: "idle", mode: null, aiProvider: null, response: null, error: null };
  state.feedback = new Map();

  if (!keepSession) {
    state.anonymousId = null;
    state.sessionStatus = "pending";
  }
}

async function ensureSession() {
  state.sessionStatus = "pending";

  try {
    const session = await createRecommendationSession(state.anonymousId);
    if (!session?.anonymousId) {
      throw new Error("익명 세션을 만들지 못했어.");
    }

    state.anonymousId = session.anonymousId;
    localStorage.setItem(STORAGE_KEY, session.anonymousId);
    state.sessionStatus = "ready";

    if (state.step === "sessionError") {
      state.step = "audience";
    }
  } catch (error) {
    state.sessionStatus = "error";
    state.anonymousId = null;
    state.run.error = error;
    state.step = "sessionError";
    showToast(error.message || "백엔드 세션 연결에 실패했어.");
  }

  render();
}

function selectedGenreKey(genres = selectedGenres()) {
  return (Array.isArray(genres) ? genres : [])
    .map((genre) => normalizeGenreValue(genre))
    .filter(Boolean)
    .join("|");
}

async function ensurePostersLoaded(force = false) {
  const genres = selectedGenres();
  const genresKey = selectedGenreKey(genres);
  if (!force && ["loading", "ready"].includes(state.posters.status) && state.posters.genresKey === genresKey) {
    return;
  }

  state.posters = { ...state.posters, status: "loading", error: null, genresKey };
  render();

  try {
    const posters = await getPosterSeed(POSTER_LIMIT, genres);
    const items = Array.isArray(posters) ? posters : [];
    state.posters = {
      status: items.length > 0 ? "ready" : "empty",
      items,
      error: null,
      activeBatchIndex: 0,
      genresKey,
    };
  } catch (error) {
    state.posters = {
      status: "error",
      items: [],
      error,
      activeBatchIndex: 0,
      genresKey,
    };
    showToast(error.message || "포스터 API 연결에 실패했어.");
  }

  render();
}

function renderTitle(parts) {
  const title = createElement("h2", "ai-title");

  parts.forEach((part) => {
    const node = createElement("span", part.highlight ? "ai-highlight" : null);
    if (part.style) {
      node.style.cssText = part.style;
    }

    const textParts = part.text.split('\n');
    textParts.forEach((textPart, index) => {
      if (textPart) {
        node.appendChild(document.createTextNode(textPart));
      }
      if (index < textParts.length - 1) {
        node.appendChild(document.createElement('br'));
      }
    });

    title.appendChild(node);
  });

  return title;
}

function renderSplitLayout({ kicker, titleParts, description, extraLeft, content }) {
  const section = createElement("section", "ai-split-layout");

  const leftPane = createElement("div", "ai-split-left");
  resetBackButtonToTopbar();

  leftPane.appendChild(renderTitle(titleParts));

  if (description) {
    leftPane.appendChild(createElement("p", "ai-description", description));
  }
  if (extraLeft) {
    leftPane.appendChild(extraLeft);
  }

  const summary = renderSummary();
  if (summary) {
    leftPane.appendChild(summary);
  }

  const rightPane = createElement("div", "ai-split-right");
  rightPane.appendChild(content);

  section.appendChild(leftPane);
  section.appendChild(rightPane);

  return section;
}

function renderSummary() {
  const rows = [];
  const searchText = searchContextText(state.searchContext);

  if (searchText) {
    rows.push(["상영 조건", searchText]);
  }

  if (state.survey.audience) {
    rows.push(["함께 볼 사람", optionLabel(audienceOptions, state.survey.audience)]);
  }
  if (state.survey.mood) {
    rows.push(["오늘 컨디션", optionLabel(moodOptions, state.survey.mood)]);
  }
  if (state.survey.avoid.length > 0) {
    const avoidLabel = state.survey.avoid.map((value) => optionLabel(avoidOptions, value)).join(", ");
    rows.push(["보고 싶지 않은 요소", avoidLabel]);
  }
  if (state.survey.preferredGenres.length > 0) {
    rows.push(["선호 장르", optionLabels(genreOptions, state.survey.preferredGenres)]);
  }
  if (state.posterChoices.likedSeedMovieIds.length) {
    rows.push(["포스터 취향", `${state.posterChoices.likedSeedMovieIds.length} / ${MIN_LIKED_POSTERS}+`]);
  }

  if (rows.length === 0) return null;

  const summary = createElement("div", "ai-summary");
  rows.forEach(([label, value]) => {
    const row = createElement("div", "ai-summary-row");
    row.appendChild(createElement("strong", null, label));
    row.appendChild(document.createTextNode(` ${value}`));
    summary.appendChild(row);
  });
  return summary;
}

function renderOptionList(options, selectedValueOrArray, onSelect, isMulti = false) {
  const list = createElement("div", "ai-option-list");
  list.style.display = 'flex';
  list.style.flexDirection = 'column';
  list.style.alignItems = 'flex-end';
  list.style.gap = '14px';
  list.style.width = '100%';

  options.forEach((option) => {
    const isSelected = isMulti
      ? selectedValueOrArray.includes(option.value)
      : selectedValueOrArray === option.value;

    const button = createElement("button", ["ai-glass-btn", "can-hover", isSelected ? "is-selected" : null]);
    button.type = "button";

    button.addEventListener("click", () => {
      onSelect(option.value);
    });

    button.appendChild(createElement("span", "ai-glass-btn-title", option.label));
    list.appendChild(button);
  });

  return list;
}

function renderAudienceStep() {
  const content = renderOptionList(audienceOptions, state.survey.audience, (value) => {
    state.survey.audience = value;
    render();
    scheduleStep("mood");
  });

  return renderSplitLayout({
    kicker: "AI GUIDE 01",
    titleParts: [
      { text: "반가워요, 다보예요!\n", style: "font-size: 40px;" },
      { text: "누구랑 보실 건가요?" },
    ],
    description: "같이 볼 사람부터 골라볼까요? 지금 상황을 기준으로 추천 기준을 잡을게요.",
    content,
  });
}

function renderMoodStep() {
  const content = renderOptionList(moodOptions, state.survey.mood, (value) => {
    state.survey.mood = value;
    render();
    scheduleStep("avoid");
  });

  return renderSplitLayout({
    kicker: "AI GUIDE 02",
    titleParts: [
      { text: "오늘 하루,\n컨디션은 어때요?" },
    ],
    description: "지금 보고 싶은 감정의 온도를 고르면 추천 방향이 바로 좁혀져요.",
    content,
  });
}

function toggleAvoid(value) {
  const exists = state.survey.avoid.includes(value);
  state.survey.avoid = exists
    ? state.survey.avoid.filter((item) => item !== value)
    : [...state.survey.avoid, value];
  render();
}

function selectedGenres() {
  return (Array.isArray(state.survey.preferredGenres) ? state.survey.preferredGenres : [])
    .map(normalizeGenreValue)
    .filter(Boolean)
    .slice(0, GENRE_LIMIT);
}

function normalizeGenreValue(value) {
  return typeof value === "string" ? value.trim().toLowerCase() : "";
}

function movieGenreValues(movie) {
  return Array.isArray(movie?.genres)
    ? movie.genres.map(normalizeGenreValue).filter(Boolean)
    : [];
}

function movieMatchesSelectedGenre(movie) {
  const selected = new Set(selectedGenres());
  if (selected.size === 0) {
    return true;
  }
  return movieGenreValues(movie).some((genre) => selected.has(genre));
}

function genreMatchedPosterItems() {
  if (selectedGenres().length === 0) {
    return state.posters.items;
  }
  return state.posters.items.filter(movieMatchesSelectedGenre);
}

function posterDisplayItems() {
  const matched = genreMatchedPosterItems();
  if (selectedGenres().length === 0 || matched.length >= MIN_LIKED_POSTERS) {
    return matched;
  }

  const matchedIds = new Set(matched.map((movie) => String(movie.id)));
  const fallback = state.posters.items.filter((movie) => !matchedIds.has(String(movie.id)));
  return [...matched, ...fallback];
}

function posterGenreFallbackActive() {
  return selectedGenres().length > 0 && genreMatchedPosterItems().length < MIN_LIKED_POSTERS;
}

function toggleGenre(value) {
  const normalized = normalizeGenreValue(value);
  if (!genreOptions.some((option) => option.value === normalized)) {
    return;
  }

  const selected = selectedGenres();
  const exists = selected.includes(normalized);
  state.survey.preferredGenres = exists ? [] : [normalized];

  state.posterChoices.likedSeedMovieIds = [];
  state.posters.activeBatchIndex = 0;
  render();
}

function posterBatchCount() {
  return Math.max(1, Math.ceil(posterDisplayItems().length / POSTER_BATCH_SIZE));
}

function currentPosterBatch() {
  const batchIndex = Math.min(state.posters.activeBatchIndex, posterBatchCount() - 1);
  const start = batchIndex * POSTER_BATCH_SIZE;

  return posterDisplayItems().slice(start, start + POSTER_BATCH_SIZE);
}

function posterChoiceStatus(seedId) {
  const id = String(seedId);

  if (state.posterChoices.likedSeedMovieIds.includes(id)) {
    return "like";
  }

  return null;
}

function isPosterBatchComplete(batch = currentPosterBatch()) {
  return batch.length > 0 && batch.some((movie) => posterChoiceStatus(movie.id));
}

function canContinuePosterDiagnosis() {
  return state.posterChoices.likedSeedMovieIds.length >= MIN_LIKED_POSTERS;
}

function showPosterBatch(batchIndex) {
  if (state.stepTimer) {
    window.clearTimeout(state.stepTimer);
    state.stepTimer = null;
  }

  state.posters.activeBatchIndex = Math.min(Math.max(batchIndex, 0), posterBatchCount() - 1);
  render();
}

function renderAvoidStep() {
  const panel = createElement("div", "ai-avoid-panel");
  panel.style.width = '100%';

  const grid = createElement("div", "ai-avoid-grid");

  avoidOptions.forEach((option) => {
    const isSelected = state.survey.avoid.includes(option.value);
    const btn = createElement("button", ["ai-avoid-btn", isSelected ? "is-selected" : null], option.label);
    btn.type = "button";
    btn.addEventListener("click", () => {
      if (option.value === "none") {
        state.survey.avoid = ["none"];
        render();
        setStep("genres");
      } else {
        state.survey.avoid = state.survey.avoid.filter(v => v !== "none");
        toggleAvoid(option.value);
      }
    });
    grid.appendChild(btn);
  });

  panel.appendChild(grid);

  const ctaRow = createElement("div", "ai-cta-row");
  const completeBtn = createElement("button", "ai-primary-cta", "선택 완료");
  completeBtn.type = "button";
  completeBtn.addEventListener("click", () => {
    setStep("genres");
  });
  ctaRow.appendChild(completeBtn);

  const hint = createElement("p", "ai-avoid-cta-hint", "여러 개 선택하실 수 있습니다. 해당 없음을 선택하시면 바로 다음 단계로 넘어갑니다.");
  ctaRow.appendChild(hint);

  panel.appendChild(ctaRow);

  return renderSplitLayout({
    kicker: "AI GUIDE 03",
    titleParts: [
      { text: "보고 싶지 않은\n요소가 있으신가요?" }
    ],
    description: "선택하신 요소는 추천에 최대한 반영해 걸러드립니다.\n아이와 함께라면 더 안전한 기준으로 추천해 드릴게요.",
    content: panel,
  });
}

function renderGenreStep() {
  const panel = createElement("div", "ai-avoid-panel");
  panel.style.width = "100%";

  const grid = createElement("div", "ai-avoid-grid");
  genreOptions.forEach((option) => {
    const isSelected = selectedGenres().includes(option.value);
    const btn = createElement("button", ["ai-avoid-btn", isSelected ? "is-selected" : null], option.label);
    btn.type = "button";
    btn.setAttribute("aria-pressed", String(isSelected));
    btn.addEventListener("click", () => toggleGenre(option.value));
    grid.appendChild(btn);
  });
  panel.appendChild(grid);

  const ctaRow = createElement("div", "ai-cta-row");
  const isSatisfied = selectedGenres().length > 0;
  const completeBtn = createElement("button", ["ai-primary-cta", isSatisfied ? "is-active" : null], "포스터 고르기");
  completeBtn.type = "button";
  completeBtn.disabled = !isSatisfied;
  completeBtn.setAttribute("aria-disabled", String(!isSatisfied));
  completeBtn.addEventListener("click", () => {
    if (!isSatisfied) {
      showToast("먼저 장르를 하나 골라줘.");
      return;
    }
    setStep("posters");
  });
  ctaRow.appendChild(completeBtn);

  const hint = createElement("p", "ai-avoid-cta-hint", "장르는 하나만 선택할 수 있어. 선택한 장르 기준으로 포스터를 먼저 보여줄게.");
  ctaRow.appendChild(hint);
  panel.appendChild(ctaRow);

  return renderSplitLayout({
    kicker: "AI GUIDE 04",
    titleParts: [
      { text: "먼저 보고 싶은\n장르 하나를 골라주세요." },
    ],
    description: "포스터만 보고 고르기 전에 방향을 먼저 잡을게요. 장르 신호가 추천 점수와 GPT 분석에 같이 반영됩니다.",
    content: panel,
  });
}

function selectPoster(seedId) {
  if (state.stepTimer) {
    window.clearTimeout(state.stepTimer);
    state.stepTimer = null;
  }

  const id = String(seedId);
  const liked = state.posterChoices.likedSeedMovieIds;

  if (liked.includes(id)) {
    state.posterChoices.likedSeedMovieIds = liked.filter((item) => item !== id);
    render();
    return;
  }

  if (liked.length >= LIKE_LIMIT) {
    showToast(`끌리는 포스터는 ${LIKE_LIMIT}개까지야.`);
    return;
  }

  state.posterChoices.likedSeedMovieIds = [...liked, id];

  render();
}

function renderPosterCount(className, label, value, target) {
  const pill = createElement("div", ["ai-count-pill", className]);
  pill.appendChild(createElement("strong", null, label));
  pill.appendChild(createElement("span", null, `${value} / ${target}`));
  return pill;
}

function renderPosterPageIndicator() {
  const totalBatches = posterBatchCount();
  const currentPage = Math.min(state.posters.activeBatchIndex + 1, totalBatches);
  const indicator = createElement("div", "ai-poster-page-indicator", `${currentPage} / ${totalBatches}`);
  indicator.setAttribute("aria-label", `포스터 페이지 ${currentPage} / ${totalBatches}`);
  return indicator;
}

function renderPosterCard(movie) {
  const id = String(movie.id);
  const isLiked = state.posterChoices.likedSeedMovieIds.includes(id);
  const card = createElement("button", ["ai-poster-card", isLiked ? "is-selected" : null]);
  card.type = "button";
  card.addEventListener("click", () => selectPoster(id));

  const title = movie.title || "이름 없는 포스터";
  const fallback = createElement("div", "ai-poster-image-fallback");
  fallback.appendChild(createElement("span", null, title));
  fallback.appendChild(createElement("small", null, "포스터 준비 중"));
  card.appendChild(fallback);

  if (movie.posterUrl) {
    const image = createElement("img");
    image.alt = `${title} 포스터`;
    image.loading = "lazy";
    image.src = movie.posterUrl;
    image.addEventListener("load", () => card.classList.add("has-image"));
    image.addEventListener("error", () => {
      image.remove();
      card.classList.add("is-image-missing");
    });
    card.appendChild(image);
  } else {
    card.classList.add("is-image-missing");
  }

  const overlay = createElement("div", "ai-poster-overlay");
  overlay.appendChild(createElement("span", null, title));
  card.appendChild(overlay);

  return card;
}

function renderPosterStep() {
  if (state.posters.status === "loading" || state.posters.status === "idle") {
    return renderLoadingMessage("포스터를 가져오는 중", "잠깐만 기다려줘.");
  }
  if (state.posters.status === "error") {
    return renderErrorPanel("포스터를 가져오지 못했어", state.posters.error?.message,
      [{ label: "다시 시도", onClick: () => ensurePostersLoaded(true) }, { label: "처음부터", onClick: () => setStep("audience"), secondary: true }]);
  }
  if (state.posters.status === "empty") {
    return renderErrorPanel("포스터 seed가 비어 있어", "서버에 포스터 진단용 데이터가 아직 없어.",
      [{ label: "다시 시도", onClick: () => ensurePostersLoaded(true) }]);
  }

  const displayItems = posterDisplayItems();
  if (displayItems.length === 0) {
    return renderErrorPanel("보여줄 포스터가 없어", "선택한 장르에 맞는 포스터를 찾지 못했어. 장르를 다시 골라줘.",
      [{ label: "장르 다시 선택", onClick: () => setStep("genres") }, { label: "처음부터", onClick: () => setStep("audience"), secondary: true }]);
  }

  const activeBatch = currentPosterBatch();
  const grid = createElement("div", "ai-poster-grid");
  activeBatch.forEach((movie) => {
    grid.appendChild(renderPosterCard(movie));
  });

  const posterStage = createElement("div", "ai-poster-stage");
  posterStage.appendChild(grid);

  const prevBatchBtn = createElement("button", ["ai-batch-button", "ai-poster-nav-button", "ai-poster-prev-button"], "<");
  prevBatchBtn.type = "button";
  prevBatchBtn.setAttribute("aria-label", "Previous poster batch");
  prevBatchBtn.addEventListener("click", () => {
    const totalBatches = posterBatchCount();
    const previousIndex = (state.posters.activeBatchIndex - 1 + totalBatches) % totalBatches;
    showPosterBatch(previousIndex);
  });
  posterStage.appendChild(prevBatchBtn);

  const nextBatchBtn = createElement("button", ["ai-batch-button", "ai-poster-nav-button", "ai-poster-next-button"], ">");
  nextBatchBtn.type = "button";
  nextBatchBtn.setAttribute("aria-label", "Next poster batch");
  nextBatchBtn.addEventListener("click", () => {
    const nextIndex = (state.posters.activeBatchIndex + 1) % posterBatchCount();
    showPosterBatch(nextIndex);
  });
  posterStage.appendChild(nextBatchBtn);

  const ctaRow = createElement("div", ["ai-cta-row", "ai-poster-cta-row"]);
  const isSatisfied = canContinuePosterDiagnosis();
  const completeBtn = createElement("button", ["ai-primary-cta", isSatisfied ? "is-active" : null], "선택 완료");
  completeBtn.type = "button";
  completeBtn.disabled = !isSatisfied;
  completeBtn.setAttribute("aria-disabled", String(!isSatisfied));
  completeBtn.addEventListener("click", () => {
    if (isSatisfied) {
      setStep("mode");
    } else {
      showToast(`포스터는 최소 ${MIN_LIKED_POSTERS}개만 고르면 다음으로 갈 수 있어.`);
    }
  });
  ctaRow.appendChild(completeBtn);

  const matchedCount = genreMatchedPosterItems().length;
  const selectedGenreText = optionLabels(genreOptions, selectedGenres());
  const hintText = posterGenreFallbackActive()
    ? `선택 장르 포스터가 ${matchedCount}개라서, 비슷한 후보도 뒤에 섞었어.`
    : selectedGenreText
      ? `${selectedGenreText} 장르 포스터를 기준으로 보여주는 중이야.`
      : "전체 장르 포스터를 보여주는 중이야.";
  ctaRow.appendChild(createElement("p", "ai-avoid-cta-hint", `최소 ${MIN_LIKED_POSTERS}개, 최대 ${LIKE_LIMIT}개 선택. ${hintText}`));

  const content = createElement("div", "ai-poster-pane");
  content.style.position = "relative";
  content.style.width = "100%";

  const fakeKicker = createElement("p", "ai-kicker", "AI GUIDE 05");
  fakeKicker.style.visibility = "hidden";
  fakeKicker.setAttribute("aria-hidden", "true");

  content.appendChild(fakeKicker);
  content.appendChild(renderPosterPageIndicator());
  content.appendChild(posterStage);
  content.appendChild(ctaRow);

  const extraLeft = createElement("div", "ai-poster-count-left");
  const span = createElement("span", null, "선택");
  extraLeft.appendChild(span);
  extraLeft.appendChild(document.createTextNode(` ${state.posterChoices.likedSeedMovieIds.length} / ${MIN_LIKED_POSTERS}+`));
  if (selectedGenreText) {
    extraLeft.appendChild(document.createElement("br"));
    extraLeft.appendChild(createElement("small", null, `${selectedGenreText} 기준`));
  }

  return renderSplitLayout({
    kicker: "AI GUIDE 05",
    titleParts: [{ text: "선택한 장르 안에서,\n끌리는 포스터를 골라주세요.", style: "font-size: 30px;" }],
    description: "여기서 고른 포스터는 장르 선택보다 더 세밀한 취향 신호로 쓰입니다.",
    extraLeft,
    content,
  });
}

function renderModeCard(option) {
  const profile = modeProfile(option.value);
  const card = createElement("div", "ai-mode-card");

  if (option.recommended) {
    const tag = createElement("div", "ai-mode-recommend-tag", "추천");
    card.appendChild(tag);
  }

  const top = createElement("div");
  top.appendChild(createElement("h3", null, option.label));
  top.appendChild(createElement("div", "ai-mode-model", profile.model));

  top.appendChild(createElement("div", "ai-mode-divider"));

  const desc = createElement("p", null, profile.description);
  desc.innerHTML = profile.description.replace(/\n/g, '<br/>');
  top.appendChild(desc);

  const badges = createElement("div", "ai-mode-badges");
  profile.tags.forEach(t => {
    badges.appendChild(createElement("span", "ai-mode-badge", t));
  });
  top.appendChild(badges);

  card.appendChild(top);

  const btn = createElement("button", "ai-mode-action", "보러가기");
  btn.type = "button";
  btn.addEventListener("click", () => runRecommendation(option.value));
  card.appendChild(btn);

  return card;
}

function renderModeStep() {
  const content = createElement("div", "ai-mode-panel");
  const grid = createElement("div", "ai-mode-grid");
  modeOptions.forEach(opt => {
    grid.appendChild(renderModeCard(opt));
  });
  content.appendChild(grid);

  return renderSplitLayout({
    kicker: "AI GUIDE 06",
    titleParts: [{ text: "어떤 방식으로\n추천해 드릴까요?" }],
    description: "GPT가 포스터 취향과 상영 후보를 비교하고, 응답이 늦으면 코드 점수 기반으로 자연스럽게 보완합니다.",
    content,
  });
}

async function runRecommendation(mode) {
  if (state.sessionStatus !== "ready" || !state.anonymousId) {
    showToast("익명 세션을 먼저 확인할게.");
    await ensureSession();
  }

  if (!state.anonymousId) {
    return;
  }

  const aiProvider = state.aiProvider || DEFAULT_AI_PROVIDER;
  state.run = { status: "loading", mode, aiProvider, response: null, error: null };
  setStep("loading");

  const payload = {
    anonymousId: state.anonymousId,
    mode,
    aiProvider,
    survey: {
      audience: state.survey.audience,
      mood: state.survey.mood,
      avoid: state.survey.avoid,
      preferredGenres: selectedGenres(),
    },
    posterChoices: {
      likedSeedMovieIds: state.posterChoices.likedSeedMovieIds,
      dislikedSeedMovieIds: [],
    },
  };
  const searchFilters = buildSearchFiltersPayload();
  if (searchFilters) {
    payload.searchFilters = searchFilters;
  }

  try {
    const response = await requestRecommendations(payload);
    state.run = { status: "success", mode, aiProvider, response, error: null };

    const recommendations = Array.isArray(response?.recommendations) ? response.recommendations : [];
    setStep(response?.status === "no_candidates" || recommendations.length === 0 ? "empty" : "results");
  } catch (error) {
    state.run = { status: "error", mode, aiProvider, response: null, error };
    setStep("error");
  }
}

function renderLoadingMessage(title, description) {
  const card = createElement("section", "ai-loading-card");
  card.appendChild(createElement("div", "ai-loader"));
  card.appendChild(createElement("h2", null, title));
  card.appendChild(createElement("p", null, description));
  return card;
}

function renderLoadingStep() {
  const mode = modeProfile(state.run.mode);
  const title = mode ? `${mode.label} 분석 중` : "분석 중";
  const description = state.run.mode === "fast"
    ? "소수 후보만 빠르게 비교하고 있어. 맞는 후보가 없으면 그대로 알려줄게."
    : mode
      ? `${mode.model} 모델에 상위 후보만 보내고 있어. 결과가 없으면 가짜 추천 없이 알려줄게.`
      : "추천 후보를 계산하고 있어.";

  return renderLoadingMessage(title, description);
}

function renderErrorPanel(title, description, actions = []) {
  const panel = createElement("section", "ai-error-panel");
  panel.appendChild(createElement("h2", null, title));
  panel.appendChild(createElement("p", null, description));

  if (actions.length > 0) {
    const actionRow = createElement("div", "ai-panel-actions");
    actions.forEach((action) => {
      const button = createElement("button", action.secondary ? "ai-secondary-button" : "ai-complete-button", action.label);
      button.type = "button";
      button.addEventListener("click", action.onClick);
      actionRow.appendChild(button);
    });
    panel.appendChild(actionRow);
  }

  return panel;
}

function renderEmptyStep() {
  const message = state.run.response?.message || "현재 상영 DB에서 추천할 후보를 찾지 못했어. 수집 데이터가 들어오면 바로 추천할 수 있어.";

  return renderErrorPanel(
    "추천 후보가 아직 없어",
    message,
    [
      { label: "다시 추천", onClick: () => setStep("mode") },
      { label: "처음부터", onClick: () => setStep("audience"), secondary: true },
      { label: "메인으로", onClick: goToMainPage, secondary: true },
    ],
  );
}

function renderRecommendationErrorStep() {
  return renderErrorPanel(
    "추천 요청이 실패했어",
    state.run.error?.message || "백엔드 API 또는 선택한 AI 라우팅 연결을 확인해줘.",
    [
      { label: "다시 시도", onClick: () => runRecommendation(state.run.mode || "fast") },
      { label: "방식 다시 고르기", onClick: () => setStep("mode"), secondary: true },
      { label: "메인으로", onClick: goToMainPage, secondary: true },
    ],
  );
}

function renderSessionErrorStep() {
  return renderErrorPanel(
    "익명 세션을 만들지 못했어",
    state.run.error?.message || "Spring API가 localhost:5500에서 실행 중인지 확인해줘.",
    [
      { label: "다시 연결", onClick: ensureSession },
      { label: "홈으로", onClick: goToMainPage, secondary: true },
    ],
  );
}

function safeUrl(value) {
  if (!value || typeof value !== "string") {
    return null;
  }

  try {
    const url = new URL(value, window.location.href);
    return ["http:", "https:"].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function formatPrice(amount, currencyCode) {
  if (amount === null || amount === undefined || amount === "") {
    return "가격 정보 없음";
  }

  const numberAmount = Number(amount);
  if (Number.isNaN(numberAmount)) {
    return String(amount);
  }

  return new Intl.NumberFormat("ko-KR", {
    style: "currency",
    currency: currencyCode || "KRW",
    maximumFractionDigits: 0,
  }).format(numberAmount);
}

function providerKey(providerCode) {
  return String(providerCode || "").trim().toLowerCase().replace(/[^a-z0-9]/g, "");
}

function providerDisplayName(providerCode) {
  const key = providerKey(providerCode);
  if (key.includes("lotte")) return "롯데시네마";
  if (key.includes("mega")) return "메가박스";
  if (key.includes("cgv")) return "CGV";
  return providerCode || "정보 없음";
}

function providerBookingUrl(providerCode) {
  const key = providerKey(providerCode);
  if (key.includes("lotte")) return PROVIDER_BOOKING_URLS.lotte;
  if (key.includes("mega")) return PROVIDER_BOOKING_URLS.megabox;
  if (key.includes("cgv")) return PROVIDER_BOOKING_URLS.cgv;
  return null;
}

function priceInfo(item) {
  const hasPrice = item.minPriceAmount !== null && item.minPriceAmount !== undefined && item.minPriceAmount !== "";
  return {
    label: hasPrice ? "추천 항목 가격" : "가격 대신 확인",
    value: hasPrice
      ? formatPrice(item.minPriceAmount, item.currencyCode)
      : [item.screenName, formatShowtime(item)].filter(Boolean).join(" · ") || "상영 시간과 지점 먼저 확인",
  };
}

function formatShowtime(item) {
  const parts = [item.showDate, item.startsAt].filter(Boolean);
  return parts.length > 0 ? parts.join(" ") : "시간 정보 없음";
}

function feedbackKey(item) {
  return `${item.movieId || "movie"}:${item.showtimeId || "showtime"}`;
}

async function sendFeedback(item, action, { quiet = false } = {}) {
  if (!state.run.response?.runId || !state.anonymousId) {
    if (!quiet) {
      showToast("추천 실행 정보가 없어 피드백을 저장하지 못했어.");
    }
    return;
  }

  try {
    await sendRecommendationFeedback(state.run.response.runId, {
      anonymousId: state.anonymousId,
      movieId: item.movieId,
      showtimeId: item.showtimeId,
      action,
    });

    if (action === "like" || action === "dislike") {
      state.feedback.set(feedbackKey(item), action);
      render();
    }

    if (!quiet) {
      showToast("다음 추천에 반영할게.");
    }
  } catch (error) {
    if (!quiet) {
      showToast(error.message || "피드백 저장에 실패했어.");
    }
  }
}

function openBooking(item) {
  const bookingUrl = safeUrl(item.bookingUrl) || safeUrl(providerBookingUrl(item.providerCode));

  if (!bookingUrl) {
    showToast("이 극장의 예매 페이지를 찾지 못했어.");
    return;
  }

  sendFeedback(item, "booking_view", { quiet: true });
  window.open(bookingUrl, "_blank", "noopener,noreferrer");
}

function openSeatMap() {
  window.location.href = SEAT_RECOMMEND_URL;
}

function renderShowtimeItem(label, value) {
  const item = createElement("div", "ai-showtime-item");
  item.appendChild(createElement("strong", null, label));
  item.appendChild(createElement("span", null, value || "정보 없음"));
  return item;
}

function renderResultCard(item, index, context = {}) {
  const card = createElement("article", "ai-result-card");
  const isAiBacked = context.isAiBacked !== false;
  const isGptResult = isAiBacked && context.providerValue === "codex";
  const isFallbackResult = !isAiBacked;
  const isPreciseResult = context.mode === "precise";
  card.classList.toggle("is-gpt-result", isGptResult);
  card.classList.toggle("is-fallback-result", isFallbackResult);
  card.classList.toggle("is-precise-result", isPreciseResult);
  const inner = createElement("div", "ai-result-card-inner");
  const poster = createElement("div", "ai-result-poster");
  const imageUrl = safeUrl(item.posterUrl);

  if (imageUrl) {
    const image = createElement("img");
    image.src = imageUrl;
    image.alt = `${item.title || "영화"} 포스터`;
    image.loading = "lazy";
    poster.appendChild(image);
  } else {
    poster.appendChild(createElement("div", "ai-poster-fallback", item.title || "포스터 없음"));
  }

  poster.appendChild(createElement("div", "ai-rank", String(index + 1)));

  const body = createElement("div", "ai-result-body");
  const heading = createElement("div", "ai-result-heading");
  heading.appendChild(createElement("h2", "ai-movie-title", item.title || "제목 미상"));
  heading.appendChild(createElement("div", "ai-score", `${Math.round(Number(item.score) || 0)}점`));
  body.appendChild(heading);

  if (item.reason) {
    body.appendChild(createElement("p", isGptResult ? "ai-result-reason" : "ai-result-tags", item.reason));
  }

  if (item.analysisPoint) {
    const analysisPoint = createElement("p", isGptResult ? "ai-result-analysis" : "ai-result-tags");
    const labelText = isGptResult ? "GPT 분석" : isFallbackResult ? "Fallback 근거" : "분석 포인트";
    const label = createElement("strong", "ai-analysis-label", labelText);
    analysisPoint.appendChild(label);
    analysisPoint.appendChild(document.createTextNode(` ${item.analysisPoint}`));
    body.appendChild(analysisPoint);
  }

  if (item.caution) {
    const caution = createElement("p", "ai-result-caution");
    caution.appendChild(createElement("strong", null, "주의 포인트"));
    caution.appendChild(document.createTextNode(` ${item.caution}`));
    body.appendChild(caution);
  }

  if (item.valuePoint) {
    body.appendChild(createElement("p", ["ai-result-tags", "ai-result-tags-white"], item.valuePoint));
  }

  const price = priceInfo(item);
  const showtimeGrid = createElement("div", "ai-showtime-grid");
  showtimeGrid.appendChild(renderShowtimeItem("예매처", providerDisplayName(item.providerCode)));
  showtimeGrid.appendChild(renderShowtimeItem("극장/상영관", [item.theaterName, item.screenName].filter(Boolean).join(" · ")));
  showtimeGrid.appendChild(renderShowtimeItem("상영 시간", formatShowtime(item)));
  showtimeGrid.appendChild(renderShowtimeItem(price.label, price.value));
  body.appendChild(showtimeGrid);

  const actions = createElement("div", "ai-result-actions");
  const bookingButton = createElement("button", "ai-booking-button", "예매하기");
  bookingButton.type = "button";
  bookingButton.addEventListener("click", () => openBooking(item));
  actions.appendChild(bookingButton);

  const seatMapButton = createElement("button", "ai-seatmap-button", "좌석표보기");
  seatMapButton.type = "button";
  seatMapButton.addEventListener("click", openSeatMap);
  actions.appendChild(seatMapButton);

  body.appendChild(actions);
  inner.appendChild(poster);
  inner.appendChild(body);
  card.appendChild(inner);

  return card;
}

function renderResultsStep() {
  const response = state.run.response || {};
  const recommendations = Array.isArray(response.recommendations) ? response.recommendations : [];
  const modeLabel = optionLabel(modeOptions, response.mode || state.run.mode);
  const providerValue = state.run.aiProvider || state.aiProvider;
  const model = response.model || modeProfile(response.mode || state.run.mode).model;
  const isAiBacked = response.status === "ok";
  const resultSource = isAiBacked
    ? `${providerInfo.title} · ${modeLabel}`
    : "코드 점수 기반 fallback";
  const layout = createElement("section", "ai-result-layout");
  const side = createElement("aside", "ai-result-side");

  side.appendChild(createElement("p", "ai-kicker", "AI RESULT"));
  side.appendChild(renderTitle([
    { text: "다보의\n영화 추천은!" },
  ]));
  const resultNote = response.message || "지금 상황과 취향을 반영해, 바로 볼 수 있는 최적의 영화를 골랐어요.";
  side.appendChild(createElement("p", "ai-result-note", resultNote));

  const summary = createElement("div", "ai-result-summary");

  const addSummaryRow = (label, value) => {
    const row = createElement("div", "ai-result-summary-row");
    row.appendChild(createElement("strong", null, label));
    row.appendChild(createElement("span", null, value));
    summary.appendChild(row);
  };

  const searchText = searchContextText(state.searchContext);
  if (searchText) {
    addSummaryRow("상영 조건", searchText);
  }
  if (state.survey.audience) {
    addSummaryRow("함께 볼 사람", optionLabel(audienceOptions, state.survey.audience));
  }
  if (state.survey.mood) {
    addSummaryRow("오늘 컨디션", optionLabel(moodOptions, state.survey.mood));
  }
  if (state.survey.avoid.length > 0) {
    const avoidLabel = state.survey.avoid.map((value) => optionLabel(avoidOptions, value)).join(", ");
    addSummaryRow("보고 싶지 않은 요소", avoidLabel);
  }
  if (state.survey.preferredGenres.length > 0) {
    addSummaryRow("선호 장르", optionLabels(genreOptions, state.survey.preferredGenres));
  }
  if (state.posterChoices.likedSeedMovieIds.length) {
    addSummaryRow("포스터 취향", `${state.posterChoices.likedSeedMovieIds.length} / ${MIN_LIKED_POSTERS}+`);
  }
  addSummaryRow("추천 방식", modeLabel);
  addSummaryRow("요청 엔진", `${providerInfo.title} · ${model}`);
  addSummaryRow("실제 처리", resultSource);
  addSummaryRow("분석 깊이", isAiBacked
    ? (response.mode || state.run.mode) === "precise" ? "GPT 정밀 분석" : "GPT 빠른 분석"
    : "fallback 추천");

  side.appendChild(summary);

  const actions = createElement("div", "ai-result-side-actions");

  const topRow = createElement("div", "ai-result-side-actions-row");
  const retryButton = createElement("button", "ai-secondary-button", "다시 추천받기");
  retryButton.type = "button";
  retryButton.addEventListener("click", () => setStep("mode"));
  topRow.appendChild(retryButton);

  const resetButton = createElement("button", "ai-secondary-button", "초기화 후 메인으로");
  resetButton.type = "button";
  resetButton.addEventListener("click", resetProfile);
  topRow.appendChild(resetButton);

  actions.appendChild(topRow);

  const homeButton = createElement("button", "ai-secondary-button", "메인으로 이동");
  homeButton.type = "button";
  homeButton.style.width = "100%";
  homeButton.addEventListener("click", goToMainPage);
  actions.appendChild(homeButton);

  side.appendChild(actions);

  const list = createElement("div", "ai-result-list");
  recommendations.forEach((item, index) => {
    list.appendChild(renderResultCard(item, index, {
      providerValue,
      mode: response.mode || state.run.mode,
      isAiBacked,
    }));
  });

  layout.appendChild(side);
  layout.appendChild(list);

  return layout;
}

async function resetProfile() {
  if (!window.confirm("브라우저 ID와 서버 추천 이력을 초기화하고 메인으로 돌아갈까?")) {
    return;
  }

  const currentId = state.anonymousId;

  try {
    if (currentId) {
      await deleteRecommendationSession(currentId);
    }
    showToast("초기화했어.");
  } catch (error) {
    showToast(error.message || "서버 이력 삭제는 실패했지만 브라우저 ID는 지울게.");
  }

  localStorage.removeItem(STORAGE_KEY);
  resetInputs(false);
  goToMainPage();
}

function render() {
  clearChildren(screen);
  screen.classList.toggle("is-poster-screen", state.step === "posters");
  progress.textContent = progressText[state.step] || "";

  const subHeader = document.querySelector(".ai-sub-header");
  if (subHeader) {
    const showBatch = state.step === "posters" && posterBatchCount() > 1;
    subHeader.style.display = showBatch ? "flex" : "none";
  }

  const batchBtn = document.getElementById("aiNextBatchButton");
  if (batchBtn) {
    if (state.step === "posters" && posterBatchCount() > 1) {
      batchBtn.style.display = "flex";
      batchBtn.onclick = () => showPosterBatch((state.posters.activeBatchIndex + 1) % posterBatchCount());
    } else {
      batchBtn.style.display = "none";
      batchBtn.onclick = null;
    }
  }

  const progressLine = document.getElementById("aiProgressLine");
  if (progressLine) {
    const stepOrder = ["audience", "mood", "avoid", "genres", "posters", "mode"];
    let idx = stepOrder.indexOf(state.step);
    if (idx !== -1) {
      progressLine.style.width = `${((idx + 1) / stepOrder.length) * 100}%`;
    } else {
      progressLine.style.width = "100%";
    }
  }

  switch (state.step) {
    case "audience":
      screen.appendChild(renderAudienceStep());
      break;
    case "mood":
      screen.appendChild(renderMoodStep());
      break;
    case "avoid":
      screen.appendChild(renderAvoidStep());
      break;
    case "genres":
      screen.appendChild(renderGenreStep());
      break;
    case "posters":
      screen.appendChild(renderPosterStep());
      break;
    case "mode":
      screen.appendChild(renderModeStep());
      break;
    case "loading":
      screen.appendChild(renderLoadingStep());
      break;
    case "results":
      screen.appendChild(renderResultsStep());
      break;
    case "empty":
      screen.appendChild(renderEmptyStep());
      break;
    case "error":
      screen.appendChild(renderRecommendationErrorStep());
      break;
    case "sessionError":
      screen.appendChild(renderSessionErrorStep());
      break;
    default:
      screen.appendChild(renderAudienceStep());
      break;
  }
}

backButton.addEventListener("click", () => {
  const previousStep = stepBackMap[state.step];
  if (previousStep) {
    clearStateFromStep(previousStep);
    setStep(previousStep);
    return;
  }

  goToPreviousPage();
});

render();
ensureSession();
