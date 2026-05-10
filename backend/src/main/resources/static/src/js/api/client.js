const DEFAULT_API_BASE_URL = "http://127.0.0.1:5500";
const FRONTEND_DEV_PORTS = new Set(["4173", "5173", "5510"]);

export function getApiBaseUrl() {
  if (window.DABOYEO_API_BASE_URL) {
    return window.DABOYEO_API_BASE_URL;
  }

  if (window.location.protocol.startsWith("http") && !FRONTEND_DEV_PORTS.has(window.location.port)) {
    return window.location.origin;
  }

  return DEFAULT_API_BASE_URL;
}

async function requestJson(path, options = {}) {
  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    ...options,
    headers: {
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    let message = "요청을 처리하지 못했어.";

    try {
      const errorBody = await response.json();
      if (typeof errorBody?.message === "string" && errorBody.message.trim()) {
        message = errorBody.message;
      }
    } catch {
      // 응답이 JSON이 아니어도 상태 코드 기준으로 실패 처리한다.
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

export async function fetchHealth() {
  return requestJson("/api/health");
}

export async function createRecommendationSession(existingAnonymousId) {
  const body = existingAnonymousId ? { anonymousId: existingAnonymousId } : {};

  return requestJson("/api/recommendation/sessions", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function deleteRecommendationSession(anonymousId) {
  return requestJson(`/api/recommendation/sessions/${encodeURIComponent(anonymousId)}`, {
    method: "DELETE",
  });
}

export async function getPosterSeed(limit = 16, genres = []) {
  const query = new URLSearchParams();
  query.set("limit", String(limit));
  query.set("_", String(Date.now()));
  (Array.isArray(genres) ? genres : [])
    .map((genre) => String(genre || "").trim().toLowerCase())
    .filter(Boolean)
    .forEach((genre) => query.append("genres", genre));

  return requestJson(`/api/recommendation/poster-seed?${query.toString()}`, {
    cache: "no-store",
  });
}

export async function getRecommendationProviderHealth() {
  return requestJson("/api/recommendation/providers/health");
}

export async function refreshShowtimes(reason = "entry") {
  return requestJson("/api/showtimes/refresh", {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

export async function fetchCgvSeatLayout(params) {
  const query = new URLSearchParams();
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim()) {
      query.set(key, String(value).trim());
    }
  });

  return requestJson(`/api/cgv/seat-layout?${query.toString()}`);
}

export async function requestRecommendations(payload) {
  return requestJson("/api/recommendations", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function sendRecommendationFeedback(runId, payload) {
  return requestJson(`/api/recommendations/${encodeURIComponent(runId)}/feedback`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
