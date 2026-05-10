import { getApiBaseUrl } from "../api/client.js";

const formatter = new Intl.NumberFormat("ko-KR");
const CATALOG_LIMIT = 36;
const SECTION_LIMIT = 8;
let catalogMovies = [];

function text(value, fallback = "") {
  const normalized = value === undefined || value === null ? "" : String(value).trim();
  return normalized || fallback;
}

function createElement(tagName, className, content) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  if (content !== undefined && content !== null) {
    element.textContent = String(content);
  }
  return element;
}

function resolvePosterUrl(url) {
  const value = text(url);
  if (!value) {
    return "";
  }
  try {
    return new URL(value, window.location.origin).href;
  } catch {
    return "";
  }
}

function formatPrice(value) {
  const amount = Number(value);
  return Number.isFinite(amount) && amount > 0 ? `₩${formatter.format(amount)}` : "상영중";
}

function formatReleaseDate(value) {
  const normalized = text(value);
  if (!normalized) {
    return "";
  }
  return normalized.replaceAll("-", ".");
}

function releaseState(movie) {
  const normalized = text(movie.release_state, "unknown").toLowerCase();
  if (normalized === "upcoming" || normalized === "now_playing") {
    return normalized;
  }
  return "unknown";
}

function releaseLabel(movie) {
  const state = releaseState(movie);
  const date = formatReleaseDate(movie.release_date);
  if (state === "upcoming") {
    return date ? `상영예정 ${date}` : "상영예정";
  }
  if (state === "now_playing") {
    return "상영중";
  }
  return date ? `개봉일 ${date}` : "개봉 정보 미확인";
}

function createReleaseChip(movie, className = "release-status-chip") {
  return createElement("span", `${className} is-${releaseState(movie)}`, releaseLabel(movie));
}

function movieMeta(movie) {
  const runtime = Number(movie.runtime_minutes);
  const pieces = [releaseLabel(movie), text(movie.age_rating, "ALL")];
  if (Number.isFinite(runtime) && runtime > 0) {
    pieces.push(`${runtime}분`);
  }
  if (movie.showtime_count) {
    pieces.push(`상영 ${movie.showtime_count}건`);
  }
  return pieces.join(" · ");
}

function tagsFor(movie) {
  return Array.isArray(movie.tags) ? movie.tags.map((tag) => String(tag).toLowerCase()) : [];
}

function hasTag(movie, tagName) {
  const normalized = String(tagName).toLowerCase();
  return tagsFor(movie).some((tag) => tag === normalized);
}

function tagValueLabels(movie, limit = 3) {
  return tagsFor(movie)
    .filter((tag) => tag.startsWith("genre:"))
    .map((tag) => tag.slice("genre:".length))
    .map((value) => {
      if (value === "sf") return "SF";
      if (value === "romance") return "로맨스";
      if (value === "thriller") return "스릴러";
      if (value === "horror") return "공포";
      if (value === "animation") return "애니메이션";
      if (value === "drama") return "드라마";
      if (value === "action") return "액션";
      if (value === "comedy") return "코미디";
      return value;
    })
    .slice(0, limit);
}

function routeToMovie(movie) {
  const params = new URLSearchParams();
  params.set("query", text(movie.title));
  window.location.href = `./movies.html?${params.toString()}`;
}

function createPosterImage(movie, className = "live-poster-image") {
  const wrapper = createElement("div", className);
  const posterUrl = resolvePosterUrl(movie.poster_url);

  if (posterUrl) {
    const img = document.createElement("img");
    img.src = posterUrl;
    img.alt = `${text(movie.title, "영화")} 포스터`;
    img.loading = "lazy";
    img.decoding = "async";
    img.addEventListener("error", () => {
      wrapper.classList.add("is-missing");
      img.remove();
      wrapper.appendChild(createElement("span", "live-poster-fallback", "포스터 준비중"));
    }, { once: true });
    wrapper.appendChild(img);
  } else {
    wrapper.classList.add("is-missing");
    wrapper.appendChild(createElement("span", "live-poster-fallback", "포스터 준비중"));
  }

  return wrapper;
}

function renderLoading(container, message = "영화 데이터를 불러오는 중") {
  if (!container) {
    return;
  }
  container.replaceChildren(createElement("div", "catalog-loading-card", message));
}

function renderTrending(movies) {
  const container = document.querySelector(".trending-list-container");
  if (!container || movies.length === 0) {
    return;
  }

  container.replaceChildren(...movies.slice(0, 3).map((movie, index) => {
    const item = createElement("div", `trending-item rank-${index + 1}`);
    item.appendChild(createElement("div", "rank-num", String(index + 1)));

    const posterBox = createElement("div", "movie-poster-box has-live-poster");
    posterBox.appendChild(createPosterImage(movie));
    item.appendChild(posterBox);

    const details = createElement("div", "movie-details");
    const meta = createElement("div", "movie-meta");
    if (index === 0) {
      meta.appendChild(createElement("span", "badge-hot", "HOT"));
    }
    meta.appendChild(createReleaseChip(movie));
    meta.appendChild(createElement("span", "movie-info-text", movieMeta(movie)));
    details.appendChild(meta);
    details.appendChild(createElement("h3", "movie-title", text(movie.title, "제목 없음")));
    details.appendChild(createElement("p", "movie-comment", `예매율 ${movie.booking_rate ?? "-"}%, 현재 수집된 상영 데이터 기준 인기작입니다.`));
    item.appendChild(details);

    const action = createElement("div", "movie-side-action");
    const price = createElement("div", "price-tag");
    price.appendChild(document.createTextNode("최저 "));
    price.appendChild(createElement("strong", null, formatPrice(movie.min_price_amount)));
    action.appendChild(price);
    const button = createElement("button", "btn-view-now", "상영 정보 보기");
    button.type = "button";
    button.addEventListener("click", () => routeToMovie(movie));
    action.appendChild(button);
    item.appendChild(action);

    return item;
  }));
}

function renderGenreMovies(activeGenre = "action") {
  const row = document.getElementById("genreMovieRow");
  if (!row || catalogMovies.length === 0) {
    return;
  }

  const normalizedGenre = String(activeGenre || "").toLowerCase();
  const matched = catalogMovies.filter((movie) => tagsFor(movie).some((tag) => tag === `genre:${normalizedGenre}` || tag.endsWith(`:${normalizedGenre}`)));
  const movies = (matched.length >= 3 ? matched : catalogMovies).slice(0, 8);

  row.replaceChildren(...movies.map((movie) => {
    const card = createElement("div", "genre-movie-card");
    const poster = createElement("div", "genre-poster");
    poster.appendChild(createPosterImage(movie, "poster-mini-lg has-live-poster"));
    card.appendChild(poster);

    const info = createElement("div", "genre-movie-info");
    info.appendChild(createElement("div", "genre-movie-title", text(movie.title, "제목 없음")));
    info.appendChild(createReleaseChip(movie, "release-status-chip compact"));
    info.appendChild(createElement("div", "genre-movie-sub", movieMeta(movie)));
    const price = createElement("div", "genre-movie-price");
    price.appendChild(document.createTextNode("최저 "));
    price.appendChild(createElement("strong", null, formatPrice(movie.min_price_amount)));
    info.appendChild(price);
    card.appendChild(info);
    card.addEventListener("click", () => routeToMovie(movie));
    return card;
  }));
}

function updateAiPick(movie) {
  if (!movie) {
    return;
  }

  const poster = document.querySelector(".ai-pick-poster");
  if (poster) {
    poster.replaceChildren(createPosterImage(movie, "ai-poster-live-image"), createElement("div", "ai-pick-glow"));
  }

  const title = document.querySelector(".ai-pick-movie-title");
  if (title) title.textContent = text(movie.title, "오늘의 추천");

  const bubble = document.querySelector(".ai-pick-reason-bubble span");
  if (bubble) {
    bubble.textContent = `현재 상영 데이터 기준 예매율 ${movie.booking_rate ?? "-"}% 인기작입니다`;
  }

  const meta = document.querySelector(".ai-pick-movie-meta");
  if (meta) {
    meta.replaceChildren(
      createElement("span", null, movie.runtime_minutes ? `${movie.runtime_minutes}분` : "상영중"),
      createReleaseChip(movie, "release-status-chip compact"),
      createElement("span", null, `예매율 ${movie.booking_rate ?? "-"}%`),
      createElement("span", null, text(movie.age_rating, "ALL")),
      createElement("span", null, `상영 ${movie.showtime_count ?? 0}건`),
    );
  }
}

function soloReason(movie) {
  if (hasTag(movie, "audience:alone")) {
    return "혼영 태그와 예매 흐름이 함께 잡힌 영화입니다.";
  }
  const tags = tagsFor(movie);
  if (tags.includes("genre:sf") || tags.includes("genre:thriller") || tags.includes("genre:horror")) {
    return "혼자 몰입하기 좋은 장르 신호와 예매율을 함께 반영했습니다.";
  }
  return "현재 API 예매율과 상영 데이터를 기준으로 혼자 보기 좋은 후보를 골랐습니다.";
}

function renderSoloMovies(movies) {
  const grid = document.querySelector(".mood-cards-grid");
  if (!grid || movies.length === 0) {
    return;
  }

  grid.replaceChildren(...movies.slice(0, 4).map((movie, index) => {
    const card = createElement("div", "mood-card solo-theme");
    const poster = createElement("div", "mood-poster");
    poster.appendChild(createPosterImage(movie, "poster-placeholder-lg has-live-poster"));
    const label = createElement("div", "mood-label solo-label");
    label.appendChild(createElement("i", index % 2 === 0 ? "fas fa-headphones" : "fas fa-eye"));
    label.appendChild(document.createTextNode(index === 0 ? " 혼영 추천" : " 몰입 후보"));
    poster.appendChild(label);
    card.appendChild(poster);

    const info = createElement("div", "mood-info");
    info.appendChild(createElement("div", "mood-title", text(movie.title, "제목 없음")));
    const reason = createElement("div", "mood-reason");
    reason.appendChild(createElement("i", "fas fa-quote-left"));
    reason.appendChild(document.createTextNode(` ${soloReason(movie)}`));
    info.appendChild(reason);

    const tags = createElement("div", "mood-tags");
    const labels = tagValueLabels(movie);
    [releaseLabel(movie), ...labels, `예매율 ${movie.booking_rate ?? "-"}%`].slice(0, 3).forEach((labelText) => {
      tags.appendChild(createElement("span", "mood-tag", labelText));
    });
    info.appendChild(tags);

    const priceRow = createElement("div", "mood-price-row");
    priceRow.appendChild(createElement("span", null, `최저 ${formatPrice(movie.min_price_amount)}`));
    const button = createElement("button", "mood-btn", "상영 정보");
    button.type = "button";
    button.addEventListener("click", () => routeToMovie(movie));
    priceRow.appendChild(button);
    info.appendChild(priceRow);
    card.appendChild(info);
    return card;
  }));
}

function coupleDescription(movie) {
  const romance = hasTag(movie, "genre:romance");
  if (romance) {
    return "로맨스 태그가 잡힌 현재 상영작 중에서 예매 흐름이 좋은 작품입니다.";
  }
  return "데이트 관람에 무난한 장르와 현재 예매 흐름을 함께 반영한 후보입니다.";
}

function renderCoupleMovies(movies) {
  const banner = document.querySelector(".couple-banner");
  const subGrid = document.querySelector(".couple-sub-grid");
  if (!banner || !subGrid || movies.length === 0) {
    return;
  }

  const [mainMovie, ...subMovies] = movies;
  const bannerText = createElement("div", "couple-banner-text");
  const label = createElement("div", "couple-banner-label");
  label.appendChild(createElement("i", "fas fa-heart"));
  label.appendChild(document.createTextNode(" COUPLE PICK"));
  bannerText.appendChild(label);
  bannerText.appendChild(createElement("div", "couple-banner-title", text(mainMovie.title, "데이트 추천")));
  bannerText.appendChild(createElement("div", "couple-banner-desc", coupleDescription(mainMovie)));

  const meta = createElement("div", "couple-banner-meta");
  [movieMeta(mainMovie), `예매율 ${mainMovie.booking_rate ?? "-"}%`, `최저 ${formatPrice(mainMovie.min_price_amount)}`].forEach((value) => {
    meta.appendChild(createElement("span", null, value));
  });
  bannerText.appendChild(meta);

  const mainButton = createElement("button", "couple-main-btn", "지금 상영 정보 보기");
  mainButton.type = "button";
  mainButton.addEventListener("click", () => routeToMovie(mainMovie));
  bannerText.appendChild(mainButton);

  const bannerPoster = createElement("div", "couple-banner-poster has-live-poster");
  bannerPoster.appendChild(createPosterImage(mainMovie, "couple-poster-placeholder has-live-poster"));
  banner.replaceChildren(bannerText, bannerPoster);

  const candidates = subMovies.length >= 4 ? subMovies : movies.slice(1).concat(catalogMovies).filter((movie, index, arr) => {
    const key = text(movie.movie_key, text(movie.title));
    return key && arr.findIndex((item) => text(item.movie_key, text(item.title)) === key) === index;
  });

  subGrid.replaceChildren(...candidates.slice(0, 4).map((movie) => {
    const card = createElement("div", "couple-sub-card");
    const poster = createElement("div", "couple-sub-poster");
    poster.appendChild(createPosterImage(movie, "poster-mini-sm has-live-poster"));
    card.appendChild(poster);

    const info = createElement("div", "couple-sub-info");
    info.appendChild(createElement("div", "couple-sub-title", text(movie.title, "제목 없음")));
    const tags = createElement("div", "couple-sub-tags");
    tags.appendChild(createReleaseChip(movie, "release-status-chip compact"));
    tagValueLabels(movie, 2).forEach((labelText) => tags.appendChild(createElement("span", "tag", labelText)));
    info.appendChild(tags);
    info.appendChild(createElement("div", "couple-sub-price", `최저 ${formatPrice(movie.min_price_amount)}`));
    card.appendChild(info);

    const button = createElement("button", "couple-sub-btn", "상영");
    button.type = "button";
    button.addEventListener("click", () => routeToMovie(movie));
    card.appendChild(button);
    return card;
  }));
}

function setupGenreTabs() {
  document.querySelectorAll(".genre-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".genre-tab").forEach((item) => item.classList.remove("active"));
      tab.classList.add("active");
      renderGenreMovies(tab.dataset.genre);
    });
  });
}

async function fetchCatalog(params) {
  const query = new URLSearchParams(params);
  const response = await fetch(`${getApiBaseUrl()}/api/live/movies?${query.toString()}`, {
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error("movie catalog api failed");
  }
  const data = await response.json();
  return Array.isArray(data?.movies) ? data.movies : [];
}

async function loadCatalog() {
  renderLoading(document.querySelector(".trending-list-container"));
  renderLoading(document.getElementById("genreMovieRow"));
  renderLoading(document.querySelector(".mood-cards-grid"), "혼영 추천 데이터를 불러오는 중");
  renderLoading(document.querySelector(".couple-sub-grid"), "데이트 추천 데이터를 불러오는 중");

  try {
    const [popularMovies, soloMovies, coupleMovies] = await Promise.all([
      fetchCatalog({ limit: String(CATALOG_LIMIT) }),
      fetchCatalog({ limit: String(SECTION_LIMIT), section: "alone", releaseState: "now_playing" }),
      fetchCatalog({ limit: String(SECTION_LIMIT), section: "couple" }),
    ]);

    catalogMovies = popularMovies;
    if (catalogMovies.length === 0) {
      return;
    }

    renderTrending(catalogMovies);
    updateAiPick(catalogMovies[0]);
    renderGenreMovies(document.querySelector(".genre-tab.active")?.dataset.genre);
    renderSoloMovies(soloMovies.length > 0 ? soloMovies : catalogMovies);
    renderCoupleMovies(coupleMovies.length > 0 ? coupleMovies : catalogMovies);
  } catch (error) {
    console.warn("Failed to load all movie catalog:", error);
  }
}

setupGenreTabs();

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", loadCatalog, { once: true });
} else {
  void loadCatalog();
}
