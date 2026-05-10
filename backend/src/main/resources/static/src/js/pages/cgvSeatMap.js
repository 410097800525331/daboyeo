import { fetchCgvSeatLayout } from "../api/client.js";

const SAMPLE_LAYOUT_URL = "../data/cgv-seat-layout.sample.json";
const AUTO_REFRESH_MS = 15000;
const DEFAULT_STATUS = ["available", "special", "sold", "unavailable", "unknown"];
const STATUS_LABELS = {
  available: "예매가능",
  special: "특수석",
  sold: "판매완료",
  unavailable: "선택불가",
  unknown: "미확인",
};

const elements = {
  form: document.getElementById("liveSeatForm"),
  siteNo: document.getElementById("siteNoInput"),
  screeningDate: document.getElementById("screeningDateInput"),
  screenNo: document.getElementById("screenNoInput"),
  screenSequence: document.getElementById("screenSequenceInput"),
  seatAreaNo: document.getElementById("seatAreaNoInput"),
  autoRefresh: document.getElementById("autoRefreshInput"),
  liveFetchButton: document.getElementById("liveFetchButton"),
  reloadSampleButton: document.getElementById("reloadSampleButton"),
  fileInput: document.getElementById("layoutFileInput"),
  sourceLabel: document.getElementById("dataSourceLabel"),
  theaterName: document.getElementById("theaterName"),
  screenName: document.getElementById("screenName"),
  showTime: document.getElementById("showTime"),
  fetchedAt: document.getElementById("fetchedAt"),
  totalSeatCount: document.getElementById("totalSeatCount"),
  remainingSeatCount: document.getElementById("remainingSeatCount"),
  zoneCount: document.getElementById("zoneCount"),
  statusFilter: document.getElementById("statusFilter"),
  clearSelectionButton: document.getElementById("clearSelectionButton"),
  zoomRange: document.getElementById("zoomRange"),
  zoomOutput: document.getElementById("zoomOutput"),
  selectedStatus: document.getElementById("selectedStatus"),
  selectedLabel: document.getElementById("selectedLabel"),
  selectedCoords: document.getElementById("selectedCoords"),
  selectedType: document.getElementById("selectedType"),
  selectedZone: document.getElementById("selectedZone"),
  mapTitle: document.getElementById("mapTitle"),
  statusCounts: document.getElementById("statusCounts"),
  viewport: document.getElementById("mapViewport"),
  canvas: document.getElementById("seatCanvas"),
};

const state = {
  layout: null,
  selectedSeatId: "",
  visibleStatuses: new Set(DEFAULT_STATUS),
  autoRefreshTimer: null,
  isFetchingLive: false,
};

function text(value, fallback = "-") {
  const normalized = value === undefined || value === null ? "" : String(value).trim();
  return normalized || fallback;
}

function number(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function getSeatWidth(seat) {
  return number(seat.w ?? seat.width, 1);
}

function getSeatHeight(seat) {
  return number(seat.h ?? seat.height, 1);
}

function getStatus(seat) {
  const status = text(seat.status ?? seat.normalized_status, "unknown").toLowerCase();
  return DEFAULT_STATUS.includes(status) ? status : "unknown";
}

function normalizeLayout(layout) {
  const seats = Array.isArray(layout?.seats) ? layout.seats : [];
  const zoneBoxes = Array.isArray(layout?.zoneBoxes) ? layout.zoneBoxes : [];
  const rowLabels = Array.isArray(layout?.rowLabels) ? layout.rowLabels : [];
  const entrances = Array.isArray(layout?.entrances) ? layout.entrances : [];

  return {
    ...layout,
    seats,
    zoneBoxes,
    rowLabels,
    entrances,
  };
}

function formatScreeningDate(value) {
  const date = text(value, "");
  if (/^\d{8}$/.test(date)) {
    return `${date.slice(0, 4)}.${date.slice(4, 6)}.${date.slice(6, 8)}`;
  }
  return text(value);
}

function formatFetchedAt(value) {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return text(value);
  }
  return new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

function getLayoutTitle(layout) {
  const theater = text(layout.theaterName ?? layout.siteName, "CGV");
  const screen = text(layout.screenName ?? layout.screenNo, "상영관");
  return `${theater} ${screen}`;
}

function getLayoutShowTime(layout) {
  const date = layout.showDate ?? layout.screeningDate;
  const start = layout.startTime ?? layout.start_time ?? "";
  return [formatScreeningDate(date), text(start, "")].filter(Boolean).join(" ");
}

function statusCounts(layout) {
  const counts = Object.fromEntries(DEFAULT_STATUS.map((status) => [status, 0]));
  layout.seats.forEach((seat) => {
    counts[getStatus(seat)] += 1;
  });
  return counts;
}

function buildBounds(layout) {
  const points = [];

  layout.seats.forEach((seat) => {
    const x = number(seat.x);
    const y = number(seat.y);
    points.push([x, y], [x + getSeatWidth(seat), y + getSeatHeight(seat)]);
  });

  layout.zoneBoxes.forEach((zone) => {
    const x = number(zone.x);
    const y = number(zone.y);
    points.push([x, y], [x + number(zone.w ?? zone.width, 1), y + number(zone.h ?? zone.height, 1)]);
  });

  layout.rowLabels.forEach((row) => {
    points.push([0, number(row.y)]);
  });

  layout.entrances.forEach((entrance) => {
    points.push([number(entrance.x), number(entrance.y)]);
  });

  if (!points.length) {
    return { minX: 0, minY: 0, maxX: 30, maxY: 20 };
  }

  const xs = points.map(([x]) => x);
  const ys = points.map(([, y]) => y);
  return {
    minX: Math.min(...xs),
    minY: Math.min(...ys),
    maxX: Math.max(...xs),
    maxY: Math.max(...ys),
  };
}

function createElement(tagName, className, content) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  if (content !== undefined) {
    element.textContent = content;
  }
  return element;
}

function applyPosition(element, item, bounds, unit, gutter) {
  const x = number(item.x);
  const y = number(item.y);
  const width = number(item.w ?? item.width, 1);
  const height = number(item.h ?? item.height, 1);
  element.style.left = `${gutter + (x - bounds.minX) * unit}px`;
  element.style.top = `${gutter + (y - bounds.minY) * unit}px`;
  element.style.width = `${Math.max(width * unit, 12)}px`;
  element.style.height = `${Math.max(height * unit, 12)}px`;
}

function renderStatusFilters(layout) {
  const counts = statusCounts(layout);
  elements.statusFilter.replaceChildren();

  DEFAULT_STATUS.forEach((status) => {
    const button = createElement("button", "status-toggle");
    button.type = "button";
    button.classList.toggle("is-off", !state.visibleStatuses.has(status));
    button.innerHTML = `
      <span><i class="status-dot ${status}" aria-hidden="true"></i>${STATUS_LABELS[status]}</span>
      <strong>${counts[status]}</strong>
    `;
    button.addEventListener("click", () => {
      if (state.visibleStatuses.has(status)) {
        state.visibleStatuses.delete(status);
      } else {
        state.visibleStatuses.add(status);
      }
      renderLayout(state.layout);
    });
    elements.statusFilter.appendChild(button);
  });
}

function renderStatusCounts(layout) {
  const counts = statusCounts(layout);
  elements.statusCounts.replaceChildren();

  DEFAULT_STATUS.forEach((status) => {
    const chip = createElement("span", "count-chip", `${STATUS_LABELS[status]} ${counts[status]}`);
    elements.statusCounts.appendChild(chip);
  });
}

function renderSummary(layout) {
  const remaining = layout.remainingSeatCount ?? statusCounts(layout).available;
  elements.theaterName.textContent = text(layout.theaterName ?? layout.siteName);
  elements.screenName.textContent = text(layout.screenName ?? layout.screenNo);
  elements.showTime.textContent = getLayoutShowTime(layout) || "-";
  elements.fetchedAt.textContent = formatFetchedAt(layout.fetchedAt);
  elements.totalSeatCount.textContent = text(layout.totalSeatCount ?? layout.seats.length, "0");
  elements.remainingSeatCount.textContent = text(remaining, "0");
  elements.zoneCount.textContent = text(layout.zoneBoxes.length, "0");
  elements.mapTitle.textContent = getLayoutTitle(layout);
}

function renderSelectedSeat(seat) {
  if (!seat) {
    elements.selectedStatus.textContent = "-";
    elements.selectedLabel.textContent = "좌석을 선택해봐.";
    elements.selectedCoords.textContent = "-";
    elements.selectedType.textContent = "-";
    elements.selectedZone.textContent = "-";
    return;
  }

  const status = getStatus(seat);
  elements.selectedStatus.textContent = STATUS_LABELS[status] || status;
  elements.selectedLabel.textContent = text(seat.label ?? seat.seat_label ?? seat.id, "좌석");
  elements.selectedCoords.textContent = `x:${text(seat.x)} y:${text(seat.y)} w:${text(getSeatWidth(seat))} h:${text(getSeatHeight(seat))}`;
  elements.selectedType.textContent = text(seat.type ?? seat.seat_kind_name ?? seat.seat_kind_code);
  elements.selectedZone.textContent = text(seat.zone ?? seat.seat_zone_name ?? seat.seat_zone_kind_code);
}

function renderZones(layout, bounds, unit, gutter) {
  layout.zoneBoxes.forEach((zone) => {
    const box = createElement("div", "zone-box");
    applyPosition(box, zone, bounds, unit, gutter);
    elements.canvas.appendChild(box);

    const label = createElement("span", "zone-box-label", text(zone.label, "구역"));
    label.style.left = box.style.left;
    label.style.top = `${Number.parseFloat(box.style.top) - 18}px`;
    elements.canvas.appendChild(label);
  });
}

function renderRowLabels(layout, bounds, unit, gutter) {
  layout.rowLabels.forEach((row) => {
    const label = createElement("span", "row-label", text(row.row));
    label.style.left = `${Math.max(6, gutter - 34)}px`;
    label.style.top = `${gutter + (number(row.y) - bounds.minY) * unit}px`;
    elements.canvas.appendChild(label);
  });
}

function renderEntrances(layout, bounds, unit, gutter) {
  layout.entrances.forEach((entrance) => {
    const label = createElement("span", "entrance-label", text(entrance.label, "입구"));
    label.style.left = `${gutter + (number(entrance.x) - bounds.minX) * unit}px`;
    label.style.top = `${gutter + (number(entrance.y) - bounds.minY) * unit}px`;
    elements.canvas.appendChild(label);
  });
}

function renderSeats(layout, bounds, unit, gutter) {
  const selectedSeat = layout.seats.find((seat) => text(seat.id ?? seat.seat_key, "") === state.selectedSeatId);
  renderSelectedSeat(selectedSeat);

  layout.seats.forEach((seat, index) => {
    const status = getStatus(seat);
    const id = text(seat.id ?? seat.seat_key, `seat-${index}`);
    const label = text(seat.label ?? seat.seat_label, id);
    const button = createElement("button", `seat-node ${status}`, label);
    button.type = "button";
    button.dataset.seatId = id;
    button.classList.toggle("is-hidden-status", !state.visibleStatuses.has(status));
    button.classList.toggle("is-selected", id === state.selectedSeatId);
    button.setAttribute("aria-label", `${label} ${STATUS_LABELS[status] || status}`);
    button.title = `${label} / ${STATUS_LABELS[status] || status}`;
    applyPosition(button, seat, bounds, unit, gutter);
    button.style.setProperty("--seat-font-size", `${Math.max(8, Math.min(12, getSeatWidth(seat) * unit * 0.22))}px`);
    button.addEventListener("click", () => {
      state.selectedSeatId = id;
      renderLayout(state.layout);
    });
    elements.canvas.appendChild(button);
  });
}

function renderEmpty(message) {
  elements.canvas.replaceChildren();
  const box = createElement("div", "map-message", message);
  elements.canvas.appendChild(box);
}

function renderLayout(rawLayout) {
  if (!rawLayout) {
    renderEmpty("좌석 데이터를 불러오는 중");
    return;
  }

  const layout = normalizeLayout(rawLayout);
  state.layout = layout;
  elements.canvas.replaceChildren();

  if (!layout.seats.length) {
    renderSummary(layout);
    renderStatusFilters(layout);
    renderStatusCounts(layout);
    renderSelectedSeat(null);
    renderEmpty("좌석 데이터가 비어있어.");
    return;
  }

  const bounds = buildBounds(layout);
  const unit = 28;
  const gutter = 58;
  const width = Math.max(720, (bounds.maxX - bounds.minX) * unit + gutter * 2);
  const height = Math.max(440, (bounds.maxY - bounds.minY) * unit + gutter * 2);
  const zoom = number(elements.zoomRange.value, 100) / 100;

  elements.canvas.style.width = `${width}px`;
  elements.canvas.style.height = `${height}px`;
  elements.canvas.style.transform = `scale(${zoom})`;
  elements.canvas.style.marginBottom = `${Math.max(0, height * (zoom - 1))}px`;
  elements.zoomOutput.textContent = `${elements.zoomRange.value}%`;

  renderSummary(layout);
  renderStatusFilters(layout);
  renderStatusCounts(layout);
  renderZones(layout, bounds, unit, gutter);
  renderRowLabels(layout, bounds, unit, gutter);
  renderEntrances(layout, bounds, unit, gutter);
  renderSeats(layout, bounds, unit, gutter);
}

function setSourceLabel(label) {
  elements.sourceLabel.textContent = label;
}

function getLiveParams() {
  return {
    siteNo: elements.siteNo.value,
    screeningDate: elements.screeningDate.value,
    screenNo: elements.screenNo.value,
    screenSequence: elements.screenSequence.value,
    seatAreaNo: elements.seatAreaNo.value,
  };
}

function syncInputsFromLayout(layout) {
  const request = layout.request || {};
  elements.siteNo.value = text(request.siteNo ?? layout.siteNo, elements.siteNo.value);
  elements.screeningDate.value = text(request.screeningDate ?? layout.screeningDate ?? layout.showDate, elements.screeningDate.value);
  elements.screenNo.value = text(request.screenNo ?? layout.screenNo, elements.screenNo.value);
  elements.screenSequence.value = text(request.screenSequence ?? layout.screenSequence, elements.screenSequence.value);
  elements.seatAreaNo.value = text(request.seatAreaNo ?? layout.seatAreaNo, "");
}

async function loadSample() {
  clearAutoRefresh();
  setSourceLabel("로컬 샘플 로딩");
  const response = await fetch(SAMPLE_LAYOUT_URL, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error("샘플 좌석 데이터를 불러오지 못했어.");
  }
  const layout = await response.json();
  setSourceLabel("로컬 샘플");
  syncInputsFromLayout(layout);
  renderLayout({ ...layout, source: "sample-json" });
}

async function loadLive() {
  if (!elements.form.reportValidity()) {
    return;
  }
  if (state.isFetchingLive) {
    return;
  }

  state.isFetchingLive = true;
  elements.liveFetchButton.disabled = true;
  setSourceLabel("실시간 조회 중");

  try {
    const layout = await fetchCgvSeatLayout(getLiveParams());
    setSourceLabel("실시간 CGV");
    syncInputsFromLayout(layout);
    renderLayout(layout);
  } catch (error) {
    setSourceLabel("실시간 실패");
    console.warn(error);
  } finally {
    state.isFetchingLive = false;
    elements.liveFetchButton.disabled = false;
  }
}

function clearAutoRefresh() {
  if (state.autoRefreshTimer) {
    clearInterval(state.autoRefreshTimer);
    state.autoRefreshTimer = null;
  }
  elements.autoRefresh.checked = false;
}

function configureAutoRefresh() {
  if (state.autoRefreshTimer) {
    clearInterval(state.autoRefreshTimer);
    state.autoRefreshTimer = null;
  }

  if (elements.autoRefresh.checked) {
    loadLive();
    state.autoRefreshTimer = setInterval(loadLive, AUTO_REFRESH_MS);
  }
}

async function loadFile(file) {
  if (!file) {
    return;
  }
  clearAutoRefresh();
  const textContent = await file.text();
  const layout = JSON.parse(textContent);
  setSourceLabel("업로드 JSON");
  syncInputsFromLayout(layout);
  renderLayout(layout);
}

elements.form.addEventListener("submit", (event) => {
  event.preventDefault();
  loadLive();
});

elements.reloadSampleButton.addEventListener("click", () => {
  loadSample().catch((error) => {
    setSourceLabel("샘플 실패");
    renderEmpty(error.message);
  });
});

elements.autoRefresh.addEventListener("change", configureAutoRefresh);
elements.fileInput.addEventListener("change", (event) => {
  loadFile(event.target.files?.[0]).catch((error) => {
    setSourceLabel("JSON 실패");
    renderEmpty(error.message);
  });
});

elements.clearSelectionButton.addEventListener("click", () => {
  state.selectedSeatId = "";
  renderLayout(state.layout);
});

elements.zoomRange.addEventListener("input", () => {
  renderLayout(state.layout);
});

loadSample().catch((error) => {
  setSourceLabel("샘플 실패");
  renderEmpty(error.message);
});
