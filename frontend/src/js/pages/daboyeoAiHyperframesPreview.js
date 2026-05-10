const percent = document.getElementById("hfPercent");
const fill = document.getElementById("hfProgressFill");
const stageName = document.getElementById("hfStageName");
const stageCopy = document.getElementById("hfStageCopy");
const dots = Array.from(document.querySelectorAll(".hf-stage-dots span"));

window.__timelines = window.__timelines || {};
window.__timelines["daboyeo-ai-loading-motion"] = {
  duration: 8,
  description: "DABOYEO AI loading motion preview. CSS timeline loop, HyperFrames-style composition metadata.",
};

const frames = [
  {
    percent: 38,
    stage: "포스터 취향 정리",
    copy: "선택한 포스터의 분위기와 선호 신호를 먼저 모으고 있어.",
    active: 0,
  },
  {
    percent: 56,
    stage: "장르 태그 매칭",
    copy: "오늘 고른 장르와 피하고 싶은 요소를 후보 기준에 맞춰보고 있어.",
    active: 1,
  },
  {
    percent: 74,
    stage: "상영 후보 스캔",
    copy: "시간대, 극장, 가격, 좌석 여유 같은 상영 후보 정보를 훑는 중이야.",
    active: 2,
  },
  {
    percent: 91,
    stage: "추천 카드 조립",
    copy: "가장 설득력 있는 후보 세 개를 결과 카드로 정리하고 있어.",
    active: 3,
  },
];

let frameIndex = 0;

function renderFrame() {
  const frame = frames[frameIndex];
  percent.textContent = `${frame.percent}%`;
  fill.style.width = `${frame.percent}%`;
  stageName.textContent = frame.stage;
  stageCopy.textContent = frame.copy;

  dots.forEach((dot, index) => {
    dot.classList.toggle("is-active", index === frame.active);
  });

  frameIndex = (frameIndex + 1) % frames.length;
}

renderFrame();
window.setInterval(renderFrame, 2000);
