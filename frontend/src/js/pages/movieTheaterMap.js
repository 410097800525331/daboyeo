const DEFAULT_POSITION = { lat: 37.5665, lng: 126.9780 };
const FALLBACK_THEATERS = [
  { name: "CGV 강남", address: "서울 강남구 강남대로 438", brand: "CGV", distance: "기준 위치 주변" },
  { name: "롯데시네마 건대입구", address: "서울 광진구 아차산로 272", brand: "롯데시네마", distance: "기준 위치 주변" },
  { name: "메가박스 코엑스", address: "서울 강남구 봉은사로 524", brand: "메가박스", distance: "기준 위치 주변" },
  { name: "CGV 왕십리", address: "서울 성동구 왕십리광장로 17", brand: "CGV", distance: "기준 위치 주변" },
];

const brandClassMap = {
  CGV: "brand-cgv",
  롯데시네마: "brand-lotte",
  메가박스: "brand-mega",
};

document.addEventListener("DOMContentLoaded", () => {
  const mapContainer = document.getElementById("map");
  const listEl = document.getElementById("placesList");
  const countEl = document.querySelector(".count-num");
  const relocateButton = document.querySelector(".btn-relocate");
  const filterButtons = document.querySelectorAll(".filter-btn");

  if (!mapContainer || !listEl) {
    return;
  }

  let currentMap = null;
  let infoWindow = null;
  let markers = [];
  let currentPlaces = [];
  let selectedBrand = "all";

  function renderStatus(message) {
    mapContainer.innerHTML = `
      <div class="map-loading">
        <div class="loading-spinner"></div>
        <span>${message}</span>
      </div>
    `;
  }

  function renderFallback(message = "지도를 불러오지 못해 기본 주변 영화관을 표시합니다.") {
    mapContainer.innerHTML = `
      <div class="map-loading">
        <i class="fas fa-location-dot" aria-hidden="true"></i>
        <span>${message}</span>
      </div>
    `;
    currentPlaces = FALLBACK_THEATERS;
    renderPlaces();
  }

  function getBrand(name) {
    if (name.includes("CGV")) return "CGV";
    if (name.includes("롯데")) return "롯데시네마";
    if (name.includes("메가")) return "메가박스";
    return "기타";
  }

  function clearMarkers() {
    markers.forEach((marker) => marker.setMap(null));
    markers = [];
  }

  function addMarker(place) {
    if (!currentMap || !window.kakao?.maps) {
      return;
    }

    const position = new kakao.maps.LatLng(Number(place.y), Number(place.x));
    const marker = new kakao.maps.Marker({ position, map: currentMap });
    kakao.maps.event.addListener(marker, "click", () => {
      infoWindow.setContent(`<div style="padding:6px 8px;font-size:12px;color:#111;">${place.place_name}</div>`);
      infoWindow.open(currentMap, marker);
    });
    markers.push(marker);
  }

  function renderPlaces() {
    const filtered = selectedBrand === "all"
      ? currentPlaces
      : currentPlaces.filter((place) => (place.brand || getBrand(place.place_name || place.name)) === selectedBrand);

    countEl.textContent = String(filtered.length);
    listEl.classList.toggle("theater-list-empty", filtered.length === 0);
    listEl.classList.toggle("theater-list", filtered.length > 0);

    if (filtered.length === 0) {
      listEl.textContent = "현재 조건에 맞는 주변 영화관이 없습니다.";
      clearMarkers();
      return;
    }

    listEl.innerHTML = filtered.map((place) => {
      const name = place.place_name || place.name;
      const brand = place.brand || getBrand(name);
      const address = place.road_address_name || place.address_name || place.address || "";
      const distance = place.distance ? `${(Number(place.distance) / 1000).toFixed(1)}km` : place.distance || "";
      const placeUrl = place.place_url || "#";
      const targetAttr = placeUrl === "#" ? "" : ` target="_blank" rel="noreferrer"`;
      return `
        <article class="place-item">
          <span class="place-brand ${brandClassMap[brand] || ""}">${brand}</span>
          <h3 class="place-name">${name}</h3>
          <p class="place-address">${address}</p>
          <p class="place-distance">${distance}</p>
          <a class="btn-booking" href="${placeUrl}"${targetAttr}>상세 보기</a>
        </article>
      `;
    }).join("");

    clearMarkers();
    filtered.forEach(addMarker);
  }

  function searchNearby(position) {
    if (!window.kakao?.maps?.services) {
      renderFallback("Kakao 지도 SDK를 불러오지 못해 기본 목록을 표시합니다.");
      return;
    }

    const ps = new kakao.maps.services.Places();
    ps.keywordSearch("영화관", (data, status) => {
      if (status !== kakao.maps.services.Status.OK || !Array.isArray(data)) {
        renderFallback("주변 영화관 검색에 실패해 기본 목록을 표시합니다.");
        return;
      }

      currentPlaces = data
        .filter((place) => ["CGV", "롯데", "메가"].some((brand) => place.place_name.includes(brand)))
        .map((place) => ({ ...place, brand: getBrand(place.place_name) }));
      renderPlaces();
    }, {
      location: position,
      radius: 5000,
      sort: kakao.maps.services.SortBy.DISTANCE,
    });
  }

  function moveToPosition(lat, lng) {
    if (!window.kakao?.maps) {
      renderFallback();
      return;
    }

    const position = new kakao.maps.LatLng(lat, lng);
    if (!currentMap) {
      currentMap = new kakao.maps.Map(mapContainer, { center: position, level: 4 });
      infoWindow = new kakao.maps.InfoWindow({ zIndex: 1 });
    } else {
      currentMap.setCenter(position);
    }
    searchNearby(position);
  }

  function locate() {
    renderStatus("현재 위치를 확인하는 중...");
    if (!navigator.geolocation) {
      moveToPosition(DEFAULT_POSITION.lat, DEFAULT_POSITION.lng);
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => moveToPosition(position.coords.latitude, position.coords.longitude),
      () => moveToPosition(DEFAULT_POSITION.lat, DEFAULT_POSITION.lng),
      { enableHighAccuracy: true, timeout: 7000, maximumAge: 300000 }
    );
  }

  filterButtons.forEach((button) => {
    button.type = "button";
    button.addEventListener("click", () => {
      const label = button.textContent.trim();
      selectedBrand = selectedBrand === label ? "all" : label;
      filterButtons.forEach((item) => item.classList.toggle("active", item === button && selectedBrand !== "all"));
      renderPlaces();
    });
  });

  relocateButton?.addEventListener("click", locate);

  if (window.kakao?.maps?.load) {
    kakao.maps.load(locate);
  } else {
    locate();
  }
});
