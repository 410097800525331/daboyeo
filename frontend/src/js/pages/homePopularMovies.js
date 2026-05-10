import { getApiBaseUrl } from "../api/client.js";

const grid = document.querySelector(".movie-cards-grid");
const formatter = new Intl.NumberFormat("ko-KR");

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

function providerLabel(provider) {
  if (provider === "LOTTE") return "롯데시네마";
  if (provider === "MEGA") return "메가박스";
  return provider || "기타";
}

function providerLogo(provider) {
  if (provider === "CGV") return "./src/assets/CGV_logo.svg";
  if (provider === "LOTTE") return "./src/assets/LOTTECINEMA_logo.jpg";
  if (provider === "MEGA") return "./src/assets/MEGABOX_logo.jpg";
  return "";
}

function routeToMovie(movie) {
  const params = new URLSearchParams();
  params.set("query", text(movie.title));
  window.location.href = `./src/pages/movies.html?${params.toString()}`;
}

function appendProviderRows(priceList, movie) {
  const providers = Array.isArray(movie.providers) && movie.providers.length > 0
    ? movie.providers
    : ["CGV", "LOTTE", "MEGA"];

  providers.slice(0, 3).forEach((provider, index) => {
    const row = createElement("div", `price-item ${index === 0 ? "lowest-price" : ""}`.trim());
    const name = createElement("span", `price-cinema ${provider.toLowerCase()}`);
    const logo = providerLogo(provider);
    if (logo) {
      const img = document.createElement("img");
      img.src = logo;
      img.alt = `${providerLabel(provider)} LOGO`;
      name.appendChild(img);
    }
    name.appendChild(document.createTextNode(` ${providerLabel(provider)}`));
    row.appendChild(name);
    row.appendChild(createElement("span", "price-amount", index === 0 ? formatPrice(movie.min_price_amount) : "상영중"));
    priceList.appendChild(row);
  });
}

function createMovieCard(movie, index) {
  const card = createElement("div", "movie-card");
  const posterBox = createElement("div", "movie-poster has-live-poster");
  const posterUrl = resolvePosterUrl(movie.poster_url);

  if (posterUrl) {
    const img = document.createElement("img");
    img.src = posterUrl;
    img.alt = `${text(movie.title, "영화")} 포스터`;
    img.loading = "lazy";
    img.decoding = "async";
    img.addEventListener("error", () => {
      posterBox.classList.add("is-missing-poster");
      img.remove();
      posterBox.appendChild(createElement("span", "movie-poster-fallback", "포스터 준비중"));
    }, { once: true });
    posterBox.appendChild(img);
  } else {
    posterBox.classList.add("is-missing-poster");
    posterBox.appendChild(createElement("span", "movie-poster-fallback", "포스터 준비중"));
  }

  const rating = createElement("div", "movie-rating");
  rating.appendChild(createElement("span", "movie-rating-text", text(movie.age_rating, "ALL")));
  posterBox.appendChild(rating);
  posterBox.appendChild(createElement("div", "movie-rank", String(index + 1)));
  posterBox.appendChild(createElement("div", `movie-release-badge is-${releaseState(movie)}`, releaseLabel(movie)));

  const info = createElement("div", "movie-info");
  info.appendChild(createElement("div", "movie-title", text(movie.title, "제목 없음")));

  const priceList = createElement("div", "price-list");
  appendProviderRows(priceList, movie);
  info.appendChild(priceList);

  const meta = createElement("div", "movie-time-info");
  meta.appendChild(createElement("p", null, `예매율: ${movie.booking_rate ?? "-"}%`));
  meta.appendChild(createElement("p", null, `${releaseLabel(movie)} · ${text(movie.age_rating, "ALL")} · 상영 ${movie.showtime_count ?? 0}건`));
  info.appendChild(meta);

  const button = createElement("button", "booking-btn", "상영 정보 보기");
  button.type = "button";
  button.addEventListener("click", () => routeToMovie(movie));
  info.appendChild(button);

  card.append(posterBox, info);
  return card;
}

async function loadHomePopularMovies() {
  if (!grid) {
    return;
  }

  try {
    const query = new URLSearchParams({ limit: "3" });
    const response = await fetch(`${getApiBaseUrl()}/api/live/movies?${query.toString()}`, {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error("popular movie api failed");
    }

    const data = await response.json();
    const movies = Array.isArray(data?.movies) ? data.movies : [];
    if (movies.length === 0) {
      return;
    }

    grid.replaceChildren(...movies.slice(0, 3).map(createMovieCard));
  } catch (error) {
    console.warn("Failed to load popular movies:", error);
  }
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", loadHomePopularMovies, { once: true });
} else {
  void loadHomePopularMovies();
}
