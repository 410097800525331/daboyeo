document.addEventListener("DOMContentLoaded", () => {
  const SEARCH_CONTEXT_KEY = "daboyeoSearchContext";
  const DEFAULT_REGION = "전체";
  const AI_PAGE_URL = "./src/pages/daboyeoAi.html";
  const DIRECT_COMPARE_PAGE_URL = "./src/pages/movies.html";
  const SEAT_MBTI_PAGE_URL = "./src/pages/seatRecommendMbti.html";
  const TIME_RANGE_PARAMS = {
    morning: { timeStart: "06:00", timeEnd: "10:59" },
    brunch: { timeStart: "11:00", timeEnd: "16:59" },
    night: { timeStart: "17:00", timeEnd: "02:00" },
  };

  const dateInput = document.getElementById("dateInput");
  const calendar = document.getElementById("calendar");
  const datesContainer = document.getElementById("calendarDates");
  const monthYear = document.getElementById("monthYear");
  const prevMonthBtn = document.getElementById("prevMonth");
  const nextMonthBtn = document.getElementById("nextMonth");
  const personCountDisplay = document.getElementById("personCount");
  const increaseBtn = document.getElementById("increaseBtn");
  const decreaseBtn = document.getElementById("decreaseBtn");
  const searchBtn = document.getElementById("searchBtn");
  const nearbyBtn = document.getElementById("nearbyBtn");
  const regionInput = document.getElementById("regionInput");
  const seatFlowTriggers = document.querySelectorAll("[data-seat-flow]");

  const sidoContainer = document.getElementById("sidoContainer");
  const gugunContainer = document.getElementById("gugunContainer");
  const dongContainer = document.getElementById("dongContainer");
  const sidoDisplay = document.getElementById("sidoDisplay");
  const gugunDisplay = document.getElementById("gugunDisplay");
  const dongDisplay = document.getElementById("dongDisplay");
  const sidoOptions = document.getElementById("sidoOptions");
  const gugunOptions = document.getElementById("gugunOptions");
  const dongOptions = document.getElementById("dongOptions");

  let currentDate = new Date();
  let currentPersonCount = 1;
  let selectedSido = "";
  let selectedGugun = "";
  let selectedDong = "";
  let regionSelectInitialized = false;
  let pendingRestoreRegion = null;

  function initializeDateInput() {
    if (!dateInput || dateInput.value) {
      return;
    }

    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, "0");
    const day = String(today.getDate()).padStart(2, "0");
    dateInput.value = `${year}-${month}-${day}`;
  }

  function renderCalendar() {
    if (!datesContainer || !monthYear) {
      return;
    }

    const year = currentDate.getFullYear();
    const month = currentDate.getMonth();
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    monthYear.textContent = `${year}.${String(month + 1).padStart(2, "0")}`;
    datesContainer.innerHTML = "";

    const firstDay = new Date(year, month, 1).getDay();
    const lastDate = new Date(year, month + 1, 0).getDate();

    for (let index = 0; index < firstDay; index += 1) {
      datesContainer.appendChild(document.createElement("div"));
    }

    for (let day = 1; day <= lastDate; day += 1) {
      const dateCell = document.createElement("div");
      const thisDate = new Date(year, month, day);
      dateCell.textContent = String(day);

      if (thisDate.getTime() === today.getTime()) {
        dateCell.classList.add("today");
      }

      if (thisDate < today) {
        dateCell.classList.add("disabled");
      } else {
        dateCell.addEventListener("click", () => {
          dateInput.value = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
          if (calendar) {
            calendar.style.display = "none";
          }
        });
      }

      datesContainer.appendChild(dateCell);
    }
  }

  function updatePersonCount() {
    if (personCountDisplay) {
      personCountDisplay.textContent = String(currentPersonCount);
    }
  }

  function selectedTimeRange() {
    return document.querySelector('input[name="timeRange"]:checked')?.value || "morning";
  }

  function normalizeRegion(value) {
    const trimmed = value?.trim() || "";
    return trimmed && trimmed !== DEFAULT_REGION ? trimmed : DEFAULT_REGION;
  }

  function selectedRegion() {
    if (sidoContainer && selectedSido) {
      const dong = selectedDong && selectedDong !== DEFAULT_REGION ? selectedDong : "";
      return normalizeRegion(`${selectedSido} ${selectedGugun} ${dong}`);
    }
    return normalizeRegion(regionInput?.value);
  }

  function syncHiddenRegionInput() {
    if (regionInput) {
      const region = selectedRegion();
      regionInput.value = region === DEFAULT_REGION ? "" : region;
    }
  }

  function buildSearchContext() {
    return {
      region: selectedRegion(),
      date: dateInput?.value || "",
      timeRange: selectedTimeRange(),
      personCount: currentPersonCount,
    };
  }

  function saveSearchContext(searchContext) {
    sessionStorage.setItem(SEARCH_CONTEXT_KEY, JSON.stringify(searchContext));
  }

  function readSearchContext() {
    try {
      const raw = sessionStorage.getItem(SEARCH_CONTEXT_KEY);
      if (!raw) {
        return null;
      }

      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== "object") {
        return null;
      }

      const personCount = Number(parsed.personCount);
      return {
        region: normalizeRegion(parsed.region),
        date: typeof parsed.date === "string" ? parsed.date : "",
        timeRange: typeof parsed.timeRange === "string" ? parsed.timeRange : "morning",
        personCount: Number.isFinite(personCount) && personCount > 0 ? Math.floor(personCount) : 1,
      };
    } catch {
      return null;
    }
  }

  function closeAllSelects() {
    document.querySelectorAll(".custom-select").forEach((select) => select.classList.remove("active"));
  }

  function displayText(display, value) {
    const span = display?.querySelector("span");
    if (span) {
      span.textContent = value;
    }
  }

  function populateOptions(listEl, options) {
    if (!listEl) {
      return;
    }

    listEl.innerHTML = "";
    options.forEach((option) => {
      const li = document.createElement("li");
      li.dataset.value = option;
      li.textContent = option;
      listEl.appendChild(li);
    });
  }

  function markSelected(listEl, value) {
    listEl?.querySelectorAll("li").forEach((item) => {
      item.classList.toggle("selected", item.dataset.value === value);
    });
  }

  function setupCustomSelect(container, display, optionsList, onSelect) {
    if (!container || !display || !optionsList) {
      return;
    }

    display.addEventListener("click", (event) => {
      event.stopPropagation();
      if (container.classList.contains("disabled")) {
        return;
      }

      const wasActive = container.classList.contains("active");
      closeAllSelects();
      if (!wasActive) {
        container.classList.add("active");
      }
    });

    optionsList.addEventListener("click", (event) => {
      const item = event.target.closest("li");
      if (!item) {
        return;
      }

      const value = item.dataset.value || "";
      displayText(display, item.textContent || value);
      markSelected(optionsList, value);
      container.classList.remove("active");
      onSelect(value);
      syncHiddenRegionInput();
    });
  }

  function regionData() {
    return window.DABOYEO_REGIONS && typeof window.DABOYEO_REGIONS === "object" ? window.DABOYEO_REGIONS : {};
  }

  function setRegionSelection(region) {
    if (!regionSelectInitialized || !region || region === DEFAULT_REGION) {
      return;
    }

    const regions = regionData();
    const [sido, gugun, ...dongParts] = region.split(/\s+/);
    if (!sido || !regions[sido]) {
      return;
    }

    selectedSido = sido;
    displayText(sidoDisplay, sido);
    markSelected(sidoOptions, sido);

    const guguns = Object.keys(regions[selectedSido] || {});
    populateOptions(gugunOptions, guguns);
    gugunContainer?.classList.remove("disabled");

    if (gugun && regions[selectedSido]?.[gugun]) {
      selectedGugun = gugun;
      displayText(gugunDisplay, gugun);
      markSelected(gugunOptions, gugun);
      populateOptions(dongOptions, [DEFAULT_REGION, ...(regions[selectedSido][gugun] || [])]);
      dongContainer?.classList.remove("disabled");
    }

    const dong = dongParts.join(" ");
    if (dong) {
      selectedDong = dong;
      displayText(dongDisplay, dong);
      markSelected(dongOptions, dong);
    }

    syncHiddenRegionInput();
  }

  function initializeRegionSelects() {
    if (regionSelectInitialized || !sidoContainer || !sidoOptions) {
      return;
    }

    const regions = regionData();
    const sidoList = Object.keys(regions);
    if (sidoList.length === 0) {
      window.addEventListener("daboyeo:regions-ready", initializeRegionSelects, { once: true });
      return;
    }

    populateOptions(sidoOptions, sidoList);
    setupCustomSelect(sidoContainer, sidoDisplay, sidoOptions, (value) => {
      selectedSido = value;
      selectedGugun = "";
      selectedDong = "";
      displayText(gugunDisplay, "시/군/구 선택");
      displayText(dongDisplay, "읍/면/동 선택");
      populateOptions(gugunOptions, Object.keys(regions[value] || {}));
      populateOptions(dongOptions, []);
      gugunContainer?.classList.remove("disabled");
      dongContainer?.classList.add("disabled");
    });

    setupCustomSelect(gugunContainer, gugunDisplay, gugunOptions, (value) => {
      selectedGugun = value;
      selectedDong = "";
      displayText(dongDisplay, "읍/면/동 선택");
      populateOptions(dongOptions, [DEFAULT_REGION, ...(regions[selectedSido]?.[value] || [])]);
      dongContainer?.classList.remove("disabled");
    });

    setupCustomSelect(dongContainer, dongDisplay, dongOptions, (value) => {
      selectedDong = value;
    });

    regionSelectInitialized = true;
    setRegionSelection(pendingRestoreRegion);
  }

  function restoreSearchContext() {
    const context = readSearchContext();
    if (!context) {
      return;
    }

    pendingRestoreRegion = context.region;
    setRegionSelection(context.region);

    if (regionInput && context.region !== DEFAULT_REGION) {
      regionInput.value = context.region;
    }

    if (dateInput && context.date) {
      dateInput.value = context.date;
      const restoredDate = new Date(`${context.date}T00:00:00`);
      if (!Number.isNaN(restoredDate.getTime())) {
        currentDate = restoredDate;
      }
    }

    currentPersonCount = context.personCount;
    updatePersonCount();

    const timeRadio = document.querySelector(`input[name="timeRange"][value="${context.timeRange}"]`);
    if (timeRadio) {
      timeRadio.checked = true;
    }
  }

  function performSearch(event) {
    event?.preventDefault();
    saveSearchContext(buildSearchContext());
    window.location.href = AI_PAGE_URL;
  }

  function buildDirectCompareUrl(searchContext, coordinates) {
    const timeRange = TIME_RANGE_PARAMS[searchContext.timeRange] || TIME_RANGE_PARAMS.morning;
    const params = new URLSearchParams({
      region: searchContext.region,
      date: searchContext.date,
      timeStart: timeRange.timeStart,
      timeEnd: timeRange.timeEnd,
      personCount: String(searchContext.personCount),
    });

    if (coordinates) {
      params.set("lat", String(coordinates.lat));
      params.set("lng", String(coordinates.lng));
    }

    return `${DIRECT_COMPARE_PAGE_URL}?${params.toString()}`;
  }

  function geocodeRegion(region) {
    if (
      !region
      || region === DEFAULT_REGION
      || !window.kakao
      || !kakao.maps
      || !kakao.maps.services
    ) {
      return Promise.resolve(null);
    }

    return new Promise((resolve) => {
      const geocoder = new kakao.maps.services.Geocoder();
      geocoder.addressSearch(region, (result, status) => {
        if (status === kakao.maps.services.Status.OK && result.length > 0) {
          resolve({ lat: Number(result[0].y), lng: Number(result[0].x) });
          return;
        }
        resolve(null);
      });
    });
  }

  async function openDirectCompare(event) {
    event?.preventDefault();
    const searchContext = buildSearchContext();
    saveSearchContext(searchContext);
    const coordinates = await geocodeRegion(searchContext.region);
    window.location.href = buildDirectCompareUrl(searchContext, coordinates);
  }

  function openSeatFlow(flow) {
    saveSearchContext(buildSearchContext());
    window.location.href = `${SEAT_MBTI_PAGE_URL}?flow=${encodeURIComponent(flow || "mbti")}`;
  }

  if (dateInput && calendar) {
    dateInput.addEventListener("click", (event) => {
      event.stopPropagation();
      calendar.style.display = "block";
      renderCalendar();
    });
  }

  if (prevMonthBtn) {
    prevMonthBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      const today = new Date();
      if (
        currentDate.getFullYear() > today.getFullYear()
        || (currentDate.getFullYear() === today.getFullYear() && currentDate.getMonth() > today.getMonth())
      ) {
        currentDate.setMonth(currentDate.getMonth() - 1);
        renderCalendar();
      }
    });
  }

  if (nextMonthBtn) {
    nextMonthBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      currentDate.setMonth(currentDate.getMonth() + 1);
      renderCalendar();
    });
  }

  document.addEventListener("click", (event) => {
    if (calendar && !event.target.closest(".form-group")) {
      calendar.style.display = "none";
    }
    if (!event.target.closest(".custom-select")) {
      closeAllSelects();
    }
  });

  if (increaseBtn) {
    increaseBtn.addEventListener("click", (event) => {
      event.preventDefault();
      currentPersonCount += 1;
      updatePersonCount();
    });
  }

  if (decreaseBtn) {
    decreaseBtn.addEventListener("click", (event) => {
      event.preventDefault();
      currentPersonCount = Math.max(1, currentPersonCount - 1);
      updatePersonCount();
    });
  }

  if (searchBtn) {
    searchBtn.addEventListener("click", performSearch);
  }

  if (nearbyBtn) {
    nearbyBtn.addEventListener("click", openDirectCompare);
  }

  seatFlowTriggers.forEach((trigger) => {
    const open = () => openSeatFlow(trigger.dataset.seatFlow);

    trigger.addEventListener("click", open);
    trigger.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        open();
      }
    });
  });

  window.updateRegionFromMap = (sidoOrAddress, maybeGugun, maybeDong) => {
    let sido = "";
    let gugun = "";
    let dong = "";

    if (maybeGugun && maybeDong) {
      sido = sidoOrAddress || "";
      gugun = maybeGugun;
      dong = maybeDong;
    } else if (sidoOrAddress) {
      const parts = String(sidoOrAddress).split(/\s+/);
      [sido = "", gugun = "", dong = ""] = parts;
    } else {
      return;
    }

    const regions = regionData();
    const matchedSidoKey = Object.keys(regions)
      .find((key) => key.startsWith(sido) || sido.startsWith(key));
    if (!matchedSidoKey) {
      return;
    }

    selectedSido = matchedSidoKey;
    displayText(sidoDisplay, matchedSidoKey);
    markSelected(sidoOptions, matchedSidoKey);

    const gugunData = regions[matchedSidoKey] || {};
    const gugunKeys = Object.keys(gugunData);
    populateOptions(gugunOptions, gugunKeys);
    gugunContainer?.classList.remove("disabled");

    const compactGugun = gugun.replace(/\s/g, "");
    const matchedGugunKey = gugunKeys
      .filter((key) => {
        const compactKey = key.replace(/\s/g, "");
        return key === gugun
          || key.startsWith(gugun)
          || gugun.startsWith(key)
          || compactKey === compactGugun;
      })
      .sort((a, b) => b.length - a.length)[0];

    if (!matchedGugunKey) {
      selectedGugun = "";
      selectedDong = "";
      displayText(gugunDisplay, "시/군/구 선택");
      displayText(dongDisplay, "읍/면/동 선택");
      dongContainer?.classList.add("disabled");
      syncHiddenRegionInput();
      return;
    }

    selectedGugun = matchedGugunKey;
    displayText(gugunDisplay, matchedGugunKey);
    markSelected(gugunOptions, matchedGugunKey);

    const dongData = [DEFAULT_REGION, ...(gugunData[matchedGugunKey] || [])];
    populateOptions(dongOptions, dongData);
    dongContainer?.classList.remove("disabled");

    const normalizeDong = (name) => String(name || "")
      .replace(/[0-9]+(가|동)?$/, "")
      .replace(/제?[0-9]+동$/, "")
      .replace(/동$/, "");
    const normalizedDong = normalizeDong(dong);
    const matchedDong = dongData.find((candidate) => {
      if (candidate === DEFAULT_REGION) {
        return false;
      }
      if (candidate === dong || dong.startsWith(candidate) || candidate.startsWith(dong)) {
        return true;
      }
      const normalizedCandidate = normalizeDong(candidate);
      return normalizedCandidate.length > 1
        && (normalizedDong.startsWith(normalizedCandidate) || normalizedCandidate.startsWith(normalizedDong));
    });

    selectedDong = matchedDong || DEFAULT_REGION;
    displayText(dongDisplay, selectedDong);
    markSelected(dongOptions, selectedDong);
    syncHiddenRegionInput();
  };

  initializeDateInput();
  initializeRegionSelects();
  restoreSearchContext();
  updatePersonCount();
});
