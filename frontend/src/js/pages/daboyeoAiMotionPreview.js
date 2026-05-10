const sceneName = document.getElementById("motionSceneName");
const sceneCopy = document.getElementById("motionSceneCopy");
const statusSweep = document.getElementById("motionStatusSweep");
const stepNodes = Array.from(document.querySelectorAll(".status-steps span"));
const particleField = document.getElementById("particleField");
const candidateStream = document.getElementById("candidateStream");

const scenes = [
  {
    name: "포스터 신호 추출",
    copy: "선택한 포스터에서 분위기와 장르 신호를 추출하고 있어.",
    active: 0,
    sweep: 18,
  },
  {
    name: "취향 입자 정렬",
    copy: "포스터와 장르 선택값을 작은 취향 입자로 분해해서 후보를 좁히는 중이야.",
    active: 1,
    sweep: 43,
  },
  {
    name: "후보 압축",
    copy: "맞지 않는 후보를 뒤로 밀고, 설득력 있는 후보만 앞으로 남기고 있어.",
    active: 2,
    sweep: 69,
  },
  {
    name: "추천 카드 조립",
    copy: "남은 후보 세 개를 결과 카드 형태로 조립하는 중이야.",
    active: 3,
    sweep: 92,
  },
];

const particlePalette = ["#a78bfa", "#7dd3fc", "#5eead4", "#f472b6", "#cdc4ff"];
const candidateStates = [
  "drop",
  "keep",
  "drop",
  "scan",
  "keep",
  "drop",
  "scan",
  "drop",
  "keep",
  "drop",
  "scan",
  "drop",
];

let sceneIndex = 0;

function seededFraction(index, salt) {
  const raw = Math.sin(index * 12.9898 + salt * 78.233) * 43758.5453;
  return raw - Math.floor(raw);
}

function createParticles() {
  const fragment = document.createDocumentFragment();

  for (let index = 0; index < 58; index += 1) {
    const particle = document.createElement("span");
    const fromX = 9 + seededFraction(index, 1) * 22;
    const fromY = 23 + seededFraction(index, 2) * 44;
    const toX = 45 + seededFraction(index, 3) * 10;
    const toY = 36 + seededFraction(index, 4) * 26;
    const driftX = 68 + seededFraction(index, 5) * 22;
    const driftY = 24 + seededFraction(index, 6) * 48;
    const size = 3 + seededFraction(index, 7) * 5;

    particle.className = "taste-particle";
    particle.style.setProperty("--from-x", `${fromX}%`);
    particle.style.setProperty("--from-y", `${fromY}%`);
    particle.style.setProperty("--to-x", `${toX}%`);
    particle.style.setProperty("--to-y", `${toY}%`);
    particle.style.setProperty("--drift-x", `${driftX}%`);
    particle.style.setProperty("--drift-y", `${driftY}%`);
    particle.style.setProperty("--size", `${size}px`);
    particle.style.setProperty("--delay", `${(index % 18) * 76}ms`);
    particle.style.setProperty("--color", particlePalette[index % particlePalette.length]);
    fragment.appendChild(particle);
  }

  particleField.appendChild(fragment);
}

function createCandidates() {
  const fragment = document.createDocumentFragment();

  candidateStates.forEach((state, index) => {
    const card = document.createElement("span");
    card.className = `candidate-chip is-${state}`;
    card.style.setProperty("--i", index);
    card.innerHTML = "<b></b><i></i><em></em>";
    fragment.appendChild(card);
  });

  candidateStream.appendChild(fragment);
}

function renderScene() {
  const scene = scenes[sceneIndex];

  sceneName.textContent = scene.name;
  sceneCopy.textContent = scene.copy;
  statusSweep.style.width = `${scene.sweep}%`;

  stepNodes.forEach((step, index) => {
    step.classList.toggle("is-active", index === scene.active);
    step.classList.toggle("is-complete", index < scene.active);
  });

  sceneIndex = (sceneIndex + 1) % scenes.length;
}

createParticles();
createCandidates();
renderScene();
window.setInterval(renderScene, 2000);
