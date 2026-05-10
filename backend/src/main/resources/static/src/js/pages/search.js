import { fetchHealth } from "../api/client.js";

export function bindSearchPage() {
  const form = document.querySelector("[data-search-form]");
  const resultList = document.querySelector("[data-result-list]");

  if (!form || !resultList) {
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const query = new FormData(form).get("q")?.toString().trim() || "";
    renderMessage(resultList, query ? `"${query}" 검색 준비됨` : "영화 제목을 입력해줘");

    try {
      const health = await fetchHealth();
      renderMessage(resultList, `백엔드 연결됨: ${health.status}`);
    } catch {
      renderMessage(resultList, "백엔드 API 연결 전이야. 프론트 골격은 정상.");
    }
  });
}

function renderMessage(container, message) {
  container.replaceChildren();

  const item = document.createElement("article");
  item.className = "movie-item";

  const title = document.createElement("strong");
  title.textContent = message;

  const description = document.createElement("span");
  description.textContent = "실제 상영 데이터 API가 연결되면 결과 목록으로 바뀐다.";

  item.append(title, description);
  container.append(item);
}
