const mbtiProfiles = [
  {
    code: "INTJ",
    title: "전략가",
    mark: "N",
    summary: "한 번의 선택도 논리적으로, 최적의 몰입을 추구하는 타입이에요.",
    traits: ["깊은 몰입 선호", "분석적 관람", "조용한 환경 선호", "방해 요소 회피"],
    primary: "중앙 몰입석",
    primaryCopy: "화면과 사운드 균형이 가장 안정적인 중앙 구역",
    secondary: "후열 안정석",
    secondaryCopy: "시야 전체를 편안하게 조망할 수 있는 후열 구역",
    zone: "center",
    score: 96,
    reasons: ["스토리 흐름을 전체적으로 파악하기 좋아요.", "몰입도를 유지하며 장면 분석에 집중할 수 있어요.", "주변 방해가 적어 온전히 영화에 빠져들 수 있어요."],
  },
  {
    code: "INTP",
    title: "논리술사",
    mark: "Q",
    summary: "관찰과 해석을 즐기는 타입이라 화면 전체가 읽히는 자리가 잘 맞아요.",
    traits: ["전체 구조 관찰", "조용한 좌석", "시야 균형", "혼자 보기 편함"],
    primary: "후열 중앙석",
    primaryCopy: "화면 전체와 주변 흐름을 한눈에 읽기 좋은 구역",
    secondary: "중앙 사이드석",
    secondaryCopy: "몰입감은 유지하면서 답답함이 덜한 구역",
    zone: "rear",
    score: 93,
    reasons: ["화면 구성과 디테일을 차분하게 관찰하기 좋아요.", "주변 움직임에 덜 예민한 안정적인 시야를 줘요.", "혼자 관람해도 부담이 적은 거리감이에요."],
  },
  {
    code: "ENTJ",
    title: "통솔자",
    mark: "F",
    summary: "효율과 시야를 모두 챙기는 타입이라 중앙에서 약간 통로 쪽이 잘 맞아요.",
    traits: ["효율 선호", "빠른 이동", "선명한 시야", "집중력"],
    primary: "중앙 통로석",
    primaryCopy: "시야 중심과 이동 편의성을 동시에 챙기는 구역",
    secondary: "중앙 몰입석",
    secondaryCopy: "화면과 사운드 밸런스가 안정적인 구역",
    zone: "aisle",
    score: 91,
    reasons: ["입장과 퇴장이 빠르면서도 시야 손실이 적어요.", "선택의 효율을 중시하는 관람 스타일과 잘 맞아요.", "중앙에 가까워 몰입감을 충분히 유지할 수 있어요."],
  },
  {
    code: "ENTP",
    title: "변론가",
    mark: "B",
    summary: "반응과 대화를 즐기는 타입이라 답답하지 않은 중간열이 잘 맞아요.",
    traits: ["활발한 반응", "동행 관람", "통로 접근", "유연한 선택"],
    primary: "중간 통로석",
    primaryCopy: "동행과 대화하기 편하고 움직임이 자유로운 구역",
    secondary: "중앙 사이드석",
    secondaryCopy: "시야를 확보하면서 답답함이 덜한 구역",
    zone: "aisle",
    score: 90,
    reasons: ["영화 중 반응을 나누기 편한 거리감이에요.", "답답하지 않은 배치라 관람 피로가 적어요.", "중앙과 통로의 장점을 함께 가져갈 수 있어요."],
  },
  {
    code: "INFJ",
    title: "옹호자",
    mark: "H",
    summary: "감정선을 깊게 따라가는 타입이라 조용한 중앙 후열이 잘 맞아요.",
    traits: ["감정 몰입", "조용한 환경", "전체 시야", "안정감"],
    primary: "후열 중앙석",
    primaryCopy: "장면의 감정과 화면 전체를 안정적으로 받아들이는 구역",
    secondary: "중앙 몰입석",
    secondaryCopy: "사운드와 화면 균형이 깊은 몰입을 돕는 구역",
    zone: "rear",
    score: 94,
    reasons: ["감정선을 방해 없이 따라가기 좋아요.", "전체 화면을 차분하게 바라볼 수 있어요.", "사람 움직임이 적어 집중을 유지하기 쉬워요."],
  },
  {
    code: "INFP",
    title: "중재자",
    mark: "V",
    summary: "분위기와 여운을 중요하게 보는 타입이라 편안한 후열이 잘 맞아요.",
    traits: ["분위기 중시", "편안함", "감성 몰입", "여유로운 시야"],
    primary: "후열 안정석",
    primaryCopy: "화면 전체를 부드럽게 받아들이는 편안한 구역",
    secondary: "중앙 사이드석",
    secondaryCopy: "감정 몰입과 개인 공간을 함께 챙기는 구역",
    zone: "rear",
    score: 92,
    reasons: ["장면의 여운을 편안하게 느끼기 좋아요.", "가까운 화면 부담이 적어 감정 몰입이 자연스러워요.", "개인적인 관람 리듬을 유지하기 쉬워요."],
  },
  {
    code: "ENFJ",
    title: "선도자",
    mark: "G",
    summary: "함께 보는 경험을 중시해 동행과 균형 잡힌 중앙 좌석이 잘 맞아요.",
    traits: ["동행 배려", "균형감", "감정 공유", "중앙 선호"],
    primary: "중앙 동행석",
    primaryCopy: "동행과 같은 시야와 감정을 공유하기 좋은 구역",
    secondary: "중간 통로석",
    secondaryCopy: "함께 움직이기 편한 중간열 구역",
    zone: "group",
    score: 91,
    reasons: ["동행과 비슷한 관람 경험을 나누기 좋아요.", "화면과 대화의 균형을 챙길 수 있어요.", "관람 후 감상을 공유하기 좋은 자리감이에요."],
  },
  {
    code: "ENFP",
    title: "활동가",
    mark: "S",
    summary: "즉각적인 반응과 분위기를 즐겨 중간 통로석이 잘 맞아요.",
    traits: ["즉흥적", "반응 공유", "통로 선호", "편한 움직임"],
    primary: "중간 통로석",
    primaryCopy: "반응을 나누기 쉽고 움직임이 자유로운 구역",
    secondary: "중앙 동행석",
    secondaryCopy: "동행과 같은 장면을 함께 즐기기 좋은 구역",
    zone: "aisle",
    score: 89,
    reasons: ["장면마다 자연스럽게 반응을 나누기 좋아요.", "답답함이 덜해 관람 리듬이 살아나요.", "동행과 함께 보기 편한 거리감이에요."],
  },
  {
    code: "ISTJ",
    title: "현실주의자",
    mark: "D",
    summary: "예측 가능하고 안정적인 관람을 선호해 중앙 후열이 잘 맞아요.",
    traits: ["안정 선호", "예측 가능", "정돈된 시야", "방해 회피"],
    primary: "후열 안정석",
    primaryCopy: "전체 화면과 주변 상황을 안정적으로 확인하는 구역",
    secondary: "중앙 몰입석",
    secondaryCopy: "관람 품질이 일정하게 유지되는 구역",
    zone: "rear",
    score: 95,
    reasons: ["시야와 동선이 안정적이라 예측하기 쉬워요.", "화면 전체를 정돈된 느낌으로 볼 수 있어요.", "주변 방해 요소가 적은 편이에요."],
  },
  {
    code: "ISFJ",
    title: "수호자",
    mark: "P",
    summary: "편안함과 배려를 중시해 후열 또는 통로 근처가 잘 맞아요.",
    traits: ["편안함", "동행 배려", "안정감", "통로 접근"],
    primary: "후열 통로석",
    primaryCopy: "편안한 시야와 이동 배려를 함께 챙기는 구역",
    secondary: "후열 안정석",
    secondaryCopy: "관람 피로가 적고 안정적인 구역",
    zone: "rearAisle",
    score: 91,
    reasons: ["동행을 챙기기 쉬운 위치예요.", "관람 중 불편함이 적어 편안해요.", "필요할 때 움직이기 쉬운 거리감이에요."],
  },
  {
    code: "ESTJ",
    title: "경영자",
    mark: "L",
    summary: "명확한 선택과 효율을 선호해 중앙 통로석이 잘 맞아요.",
    traits: ["효율", "선명한 시야", "빠른 동선", "실용적"],
    primary: "중앙 통로석",
    primaryCopy: "관람 품질과 동선 효율을 동시에 확보하는 구역",
    secondary: "중앙 몰입석",
    secondaryCopy: "가장 무난하게 좋은 관람 품질을 주는 구역",
    zone: "aisle",
    score: 92,
    reasons: ["빠르게 들어가고 나올 수 있어요.", "중앙에 가까워 시야 손실이 적어요.", "실용적인 선택을 선호하는 타입과 잘 맞아요."],
  },
  {
    code: "ESFJ",
    title: "집정관",
    mark: "C",
    summary: "함께 보는 사람의 편안함까지 챙겨 중앙 동행석이 잘 맞아요.",
    traits: ["동행 중심", "분위기", "배려", "균형"],
    primary: "중앙 동행석",
    primaryCopy: "같이 앉은 사람과 관람 경험을 나누기 좋은 구역",
    secondary: "후열 통로석",
    secondaryCopy: "편안함과 배려를 함께 챙기는 구역",
    zone: "group",
    score: 90,
    reasons: ["동행과 같은 시야를 공유하기 좋아요.", "관람 중 편안함을 챙기기 쉬워요.", "분위기를 함께 즐기기에 안정적인 위치예요."],
  },
  {
    code: "ISTP",
    title: "장인",
    mark: "T",
    summary: "실용적이고 자유로운 관람을 선호해 사이드 통로석이 잘 맞아요.",
    traits: ["자유로움", "실용적", "통로 선호", "과한 몰입 회피"],
    primary: "사이드 통로석",
    primaryCopy: "움직임이 자유롭고 부담이 적은 실용적인 구역",
    secondary: "중앙 사이드석",
    secondaryCopy: "시야와 자유로움의 균형을 맞춘 구역",
    zone: "side",
    score: 88,
    reasons: ["답답함이 적고 움직임이 편해요.", "필요한 만큼만 몰입하기 좋은 거리예요.", "실용적인 좌석 선택에 가까워요."],
  },
  {
    code: "ISFP",
    title: "모험가",
    mark: "A",
    summary: "감각적인 장면과 편안한 분위기를 즐겨 중앙 사이드석이 잘 맞아요.",
    traits: ["감각적", "편안함", "분위기", "개인 공간"],
    primary: "중앙 사이드석",
    primaryCopy: "시야는 확보하면서 개인적인 여유를 주는 구역",
    secondary: "후열 안정석",
    secondaryCopy: "편안한 관람 리듬을 유지하기 좋은 구역",
    zone: "side",
    score: 89,
    reasons: ["장면의 분위기를 부담 없이 즐기기 좋아요.", "개인 공간감이 살아 있어 편안해요.", "중앙에 가까워 화면 몰입도도 유지돼요."],
  },
  {
    code: "ESTP",
    title: "사업가",
    mark: "Z",
    summary: "생동감과 즉각적인 반응을 좋아해 앞쪽 중간열이 잘 맞아요.",
    traits: ["생동감", "즉각 반응", "활동적", "현장감"],
    primary: "전방 생동석",
    primaryCopy: "화면의 에너지를 가까이 느끼는 앞쪽 중간 구역",
    secondary: "중간 통로석",
    secondaryCopy: "현장감과 움직임 자유도를 함께 주는 구역",
    zone: "front",
    score: 87,
    reasons: ["액션과 사운드의 현장감을 크게 느낄 수 있어요.", "빠른 장면 전환에 즉각적으로 반응하기 좋아요.", "관람 경험이 더 생동감 있게 다가와요."],
  },
  {
    code: "ESFP",
    title: "연예인",
    mark: "M",
    summary: "영화관의 분위기와 반응을 즐겨 중간 동행석이 잘 맞아요.",
    traits: ["분위기", "반응 공유", "동행", "즐거움"],
    primary: "중앙 동행석",
    primaryCopy: "동행과 영화관 분위기를 함께 즐기기 좋은 구역",
    secondary: "전방 생동석",
    secondaryCopy: "화면의 에너지를 가깝게 느끼는 구역",
    zone: "group",
    score: 88,
    reasons: ["같이 웃고 반응하기 좋은 거리예요.", "영화관 분위기를 자연스럽게 즐길 수 있어요.", "장면의 에너지를 동행과 함께 느끼기 좋아요."],
  },
];

const profileIcons = {
  INTJ: { alias: "전략가", x: "0%", y: "0%" },
  INTP: { alias: "논리술사", x: "33.333%", y: "0%" },
  ENTJ: { alias: "통솔자", x: "66.667%", y: "0%" },
  ENTP: { alias: "변론가", x: "100%", y: "0%" },
  INFJ: { alias: "옹호자", x: "0%", y: "33.333%" },
  INFP: { alias: "중재자", x: "33.333%", y: "33.333%" },
  ENFJ: { alias: "선도자", x: "66.667%", y: "33.333%" },
  ENFP: { alias: "활동가", x: "100%", y: "33.333%" },
  ISTJ: { alias: "현실주의자", x: "0%", y: "66.667%" },
  ISFJ: { alias: "수호자", x: "33.333%", y: "66.667%" },
  ESTJ: { alias: "경영자", x: "66.667%", y: "66.667%" },
  ESFJ: { alias: "집정관", x: "100%", y: "66.667%" },
  ISTP: { alias: "장인", x: "0%", y: "100%" },
  ISFP: { alias: "모험가", x: "33.333%", y: "100%" },
  ESTP: { alias: "사업가", x: "66.667%", y: "100%" },
  ESFP: { alias: "연예인", x: "100%", y: "100%" },
};

const zoneTargets = {
  center: { row: 4.6, col: 8.5, rowWeight: 10, colWeight: 4, copy: "화면과 사운드 균형" },
  rear: { row: 7.2, col: 8.5, rowWeight: 8, colWeight: 3.4, copy: "편안한 전체 시야" },
  aisle: { row: 5.2, col: 3.2, altCol: 13.8, rowWeight: 7, colWeight: 6, copy: "움직임이 편한 동선" },
  rearAisle: { row: 7.3, col: 3.2, altCol: 13.8, rowWeight: 7, colWeight: 6, copy: "후열과 통로의 안정감" },
  side: { row: 5.2, col: 2.4, altCol: 14.6, rowWeight: 6.5, colWeight: 5.5, copy: "부담 적은 사이드 시야" },
  front: { row: 2.5, col: 8.5, rowWeight: 9, colWeight: 3.5, copy: "가까운 현장감" },
  group: { row: 5.1, col: 7.2, rowWeight: 7.5, colWeight: 3.3, copy: "함께 보기 좋은 균형" },
};

const zoneSeats = {
  center: ["D7", "D8", "D9", "D10", "E7", "E8", "E9", "E10", "F7", "F8", "F9", "F10"],
  rear: ["G6", "G7", "G8", "G9", "G10", "G11", "H6", "H7", "H8", "H9", "H10", "H11"],
  aisle: ["D4", "D13", "E4", "E13", "F4", "F13", "G4", "G13"],
  rearAisle: ["G4", "G13", "H4", "H13", "I4", "I13"],
  side: ["D2", "D3", "D14", "D15", "E2", "E3", "E14", "E15", "F2", "F3", "F14", "F15"],
  front: ["B7", "B8", "B9", "B10", "C7", "C8", "C9", "C10"],
  group: ["E6", "E7", "E8", "E9", "F6", "F7", "F8", "F9"],
};

const mapZoneClasses = {
  center: "map-zone-label-center",
  rear: "map-zone-label-rear",
  aisle: "map-zone-label-aisle",
  rearAisle: "map-zone-label-rear-aisle",
  side: "is-hidden",
  front: "map-zone-label-front",
  group: "map-zone-label-group",
};

const metricPresets = {
  center: [
    ["◎", "몰입도", "높음"],
    ["◈", "시야 균형", "최상"],
    ["◌", "방해 회피", "좋음"],
  ],
  rear: [
    ["◡", "편안함", "높음"],
    ["□", "전체 시야", "넓음"],
    ["◇", "안정감", "최상"],
  ],
  aisle: [
    ["⇄", "동선 효율", "최상"],
    ["↗", "움직임", "편함"],
    ["◎", "몰입도", "균형"],
  ],
  rearAisle: [
    ["◇", "안정감", "높음"],
    ["⇄", "동선 효율", "좋음"],
    ["◡", "편안함", "높음"],
  ],
  side: [
    ["◍", "개인 공간", "좋음"],
    ["–", "부담감", "낮음"],
    ["◎", "시야 확보", "균형"],
  ],
  front: [
    ["⚡", "현장감", "최상"],
    ["♪", "사운드 체감", "강함"],
    ["✦", "반응성", "높음"],
  ],
  group: [
    ["●●", "동행 경험", "최상"],
    ["♡", "감정 공유", "좋음"],
    ["⇄", "동선", "균형"],
  ],
};

const grid = document.getElementById("mbtiGrid");
const seatMap = document.getElementById("seatMap");
const randomButton = document.getElementById("randomButton");
const ctaButton = document.getElementById("ctaButton");
const fitMetrics = document.getElementById("fitMetrics");
const profileIcon = document.getElementById("profileIcon");
let selectedProfile = mbtiProfiles[0];

function createElement(tagName, className, text) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  if (text !== undefined) {
    element.textContent = text;
  }
  return element;
}

function renderMbtiGrid() {
  grid.replaceChildren();

  mbtiProfiles.forEach((profile) => {
    const icon = profileIcons[profile.code] || profileIcons.INTJ;
    const button = createElement("button", "mbti-card");
    button.type = "button";
    button.dataset.mbti = profile.code;
    button.style.setProperty("--sprite-x", icon.x);
    button.style.setProperty("--sprite-y", icon.y);
    button.setAttribute("aria-pressed", profile.code === selectedProfile.code ? "true" : "false");
    button.innerHTML = `
      <span class="mbti-card-check" aria-hidden="true">✓</span>
      <span class="mbti-card-code">${profile.code}</span>
      <span class="mbti-card-title">${profile.title}</span>
      <span class="mbti-card-icon mbti-sprite-icon" aria-hidden="true"></span>
    `;
    button.addEventListener("click", () => selectProfile(profile.code));
    grid.appendChild(button);
  });
}

function renderTraits() {
  const traitList = document.getElementById("traitList");
  traitList.replaceChildren();

  selectedProfile.traits.forEach((trait) => {
    traitList.appendChild(createElement("span", "trait-chip", trait));
  });
}

function renderReasons() {
  const reasonList = document.getElementById("reasonList");
  reasonList.replaceChildren();

  selectedProfile.reasons.forEach((reason) => {
    const item = createElement("li", null, reason);
    reasonList.appendChild(item);
  });
}

function renderFitMetrics() {
  fitMetrics.replaceChildren();

  const metrics = metricPresets[selectedProfile.zone] || metricPresets.center;
  metrics.forEach(([marker, label, value]) => {
    const item = createElement("div", "fit-metric");
    item.innerHTML = `
      <span class="fit-metric-icon" aria-hidden="true">${marker}</span>
      <span>${label}</span>
      <strong>${value}</strong>
    `;
    fitMetrics.appendChild(item);
  });
}

function getSeatFitScore(rowNumber, colNumber, profile) {
  const target = zoneTargets[profile.zone] || zoneTargets.center;
  const colDistance = target.altCol
    ? Math.min(Math.abs(colNumber - target.col), Math.abs(colNumber - target.altCol))
    : Math.abs(colNumber - target.col);
  const rowDistance = Math.abs(rowNumber - target.row);
  const distancePenalty = rowDistance * target.rowWeight + colDistance * target.colWeight;
  const centerBonus = Math.max(0, 9 - Math.abs(colNumber - 8.5)) * 0.9;
  const edgePenalty = colNumber <= 1 || colNumber >= 16 ? 7 : 0;
  const rawScore = profile.score - distancePenalty + centerBonus - edgePenalty;

  return Math.max(42, Math.min(99, Math.round(rawScore)));
}

function getZoneKeyFromLabel(label) {
  if (label.includes("전방")) {
    return "front";
  }

  if (label.includes("후열")) {
    return label.includes("통로") ? "rearAisle" : "rear";
  }

  if (label.includes("사이드")) {
    return "side";
  }

  if (label.includes("통로")) {
    return "aisle";
  }

  if (label.includes("동행")) {
    return "group";
  }

  return "center";
}

function setMapZoneLabel(element, label, zoneKey) {
  const className = mapZoneClasses[zoneKey] || mapZoneClasses.center;
  element.className = `map-zone-label ${className}`;
  element.textContent = zoneKey === "side" ? "사이드 집중석(좌측/우측)" : label;
}

function getSeatDepthLabel(rowNumber) {
  if (rowNumber <= 3) {
    return "전방";
  }

  if (rowNumber >= 7) {
    return "후방";
  }

  return "중앙";
}

function renderSeatMap() {
  const recommendedSeats = new Set(zoneSeats[selectedProfile.zone] || zoneSeats.center);
  seatMap.replaceChildren();

  for (let rowIndex = 0; rowIndex < 10; rowIndex += 1) {
    const rowName = String.fromCharCode(65 + rowIndex);
    for (let seatIndex = 1; seatIndex <= 16; seatIndex += 1) {
      const seatId = `${rowName}${seatIndex}`;
      const fitScore = getSeatFitScore(rowIndex + 1, seatIndex, selectedProfile);
      const depthLabel = getSeatDepthLabel(rowIndex + 1);
      const gridColumn = seatIndex + (seatIndex > 4 ? 1 : 0) + (seatIndex > 12 ? 1 : 0);
      const seat = createElement("button", "map-seat");
      seat.type = "button";
      seat.dataset.fit = String(fitScore);
      seat.dataset.row = rowName;
      seat.dataset.depth = depthLabel;
      seat.style.gridColumn = String(gridColumn);
      seat.innerHTML = `
        <span class="seat-id">${seatId}</span>
        <span class="seat-percent">${fitScore}%</span>
      `;
      seat.setAttribute("aria-label", `${seatId} ${depthLabel} 좌석 성향 적합도 ${fitScore}%`);
      seat.title = `${seatId} ${depthLabel} 좌석 성향 적합도 ${fitScore}%`;
      seat.classList.toggle("is-recommended", recommendedSeats.has(seatId));
      seat.classList.toggle("is-best", fitScore >= 90);
      seat.classList.toggle("is-good", fitScore >= 75 && fitScore < 90);
      seat.classList.toggle("is-low", fitScore < 60);
      seatMap.appendChild(seat);
    }
  }
}

function renderSelectedProfile() {
  const primaryZoneKey = selectedProfile.zone;
  const secondaryZoneKey = getZoneKeyFromLabel(selectedProfile.secondary);
  document.getElementById("selectedCode").textContent = selectedProfile.code;
  document.getElementById("selectedTitle").textContent = selectedProfile.title;
  document.getElementById("selectedSummary").textContent = selectedProfile.summary;
  document.getElementById("zoneTitle").textContent = selectedProfile.primary;
  document.getElementById("zoneScore").textContent = `${selectedProfile.score}%`;
  document.getElementById("primaryZone").textContent = selectedProfile.primary;
  document.getElementById("primaryZoneCopy").textContent = selectedProfile.primaryCopy;
  document.getElementById("secondaryZone").textContent = selectedProfile.secondary;
  document.getElementById("secondaryZoneCopy").textContent = selectedProfile.secondaryCopy;
  setMapZoneLabel(document.getElementById("primaryZoneLabel"), selectedProfile.primary, primaryZoneKey);
  setMapZoneLabel(document.getElementById("secondaryZoneLabel"), selectedProfile.secondary, secondaryZoneKey);
  const icon = profileIcons[selectedProfile.code] || profileIcons.INTJ;
  profileIcon?.style.setProperty("--sprite-x", icon.x);
  profileIcon?.style.setProperty("--sprite-y", icon.y);
  ctaButton.textContent = `${selectedProfile.code} 좌석 추천 보기`;
  renderTraits();
  renderReasons();
  renderFitMetrics();
  renderSeatMap();
}

function selectProfile(code) {
  selectedProfile = mbtiProfiles.find((profile) => profile.code === code) || mbtiProfiles[0];
  renderMbtiGrid();
  renderSelectedProfile();
}

function selectRandomProfile() {
  const candidates = mbtiProfiles.filter((profile) => profile.code !== selectedProfile.code);
  const nextProfile = candidates[Math.floor(Math.random() * candidates.length)] || mbtiProfiles[0];
  selectProfile(nextProfile.code);
}

function handleCtaClick() {
  const mapSection = document.querySelector(".seat-map-section");
  mapSection?.scrollIntoView({ behavior: "smooth", block: "center" });
}

randomButton.addEventListener("click", selectRandomProfile);
ctaButton.addEventListener("click", handleCtaClick);

renderMbtiGrid();
renderSelectedProfile();
