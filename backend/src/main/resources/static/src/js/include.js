(() => {
  const includeScript =
    document.currentScript ||
    Array.from(document.scripts).find((script) => script.src.endsWith("/src/js/include.js") || script.src.endsWith("\\src\\js\\include.js") || script.src.endsWith("include.js"));

  const siteRootUrl = includeScript ? new URL("../../", includeScript.src) : new URL("./", window.location.href);
  const siteRoot = siteRootUrl.href.endsWith("/") ? siteRootUrl.href.slice(0, -1) : siteRootUrl.href;

  const includeMap = {
    header: "components/header.html",
    footer: "components/footer.html",
    topButton: "components/top_button.html",
  };

  function applyTemplate(html) {
    return html.replaceAll("{{root}}", siteRoot);
  }

  async function includeIntoTarget(targetId, componentPath) {
    const target = document.getElementById(targetId);
    if (!target) {
      return;
    }

    const response = await fetch(new URL(componentPath, siteRootUrl));
    if (!response.ok) {
      throw new Error(`Failed to load ${componentPath}: ${response.status}`);
    }

    target.innerHTML = applyTemplate(await response.text());

    const includeClass = target.dataset.includeClass;
    if (includeClass && target.firstElementChild) {
      target.firstElementChild.classList.add(...includeClass.split(" ").filter(Boolean));
    }
  }

  async function loadCommonComponents() {
    const entries = Object.entries(includeMap);

    await Promise.all(
      entries.map(async ([targetId, componentPath]) => {
        try {
          await includeIntoTarget(targetId, componentPath);
        } catch (error) {
          console.error(`[include.js] ${targetId} include failed`, error);
        }
      }),
    );

    window.DABOYEO_SITE_ROOT = siteRoot;
    window.__daboyeoComponentsReady = true;
    document.dispatchEvent(new CustomEvent("daboyeo:components-loaded", { detail: { siteRoot } }));
  }

  document.addEventListener("DOMContentLoaded", () => {
    void loadCommonComponents();
  }, { once: true });

  window.loadCommonComponents = loadCommonComponents;
})();
