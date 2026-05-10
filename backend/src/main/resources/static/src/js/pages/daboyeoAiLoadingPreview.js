const percent = document.getElementById("loadingPercent");
const fill = document.getElementById("loadingProgressFill");
const stageText = document.getElementById("loadingStageText");
const stages = Array.from(document.querySelectorAll(".stage-dot"));

const timeline = [
  { value: 34, label: "포스터 취향 확인", active: 0 },
  { value: 51, label: "장르 매칭", active: 1 },
  { value: 72, label: "상영 후보 스캔", active: 2 },
  { value: 89, label: "추천 정렬 준비", active: 3 },
  { value: 94, label: "마지막 정리", active: 3 },
];

let index = 0;

function renderFrame() {
  const frame = timeline[index];
  percent.textContent = `${frame.value}%`;
  fill.style.width = `${frame.value}%`;
  stageText.textContent = frame.label;

  stages.forEach((stage, stageIndex) => {
    stage.classList.toggle("is-complete", stageIndex < frame.active);
    stage.classList.toggle("is-active", stageIndex === frame.active);
  });

  index = (index + 1) % timeline.length;
}

renderFrame();
window.setInterval(renderFrame, 1200);
