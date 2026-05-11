const Render = {
    grid: document.getElementById("event-grid"),
    empty: document.getElementById("empty-state"),
    error: document.getElementById("error-state"),
    reclinerImageUrl: "",

    text(value, fallback = "") {
        const normalized = value === undefined || value === null ? "" : String(value).trim();
        return normalized || fallback;
    },

    createElement(tagName, className, content) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        if (content !== undefined && content !== null) {
            element.textContent = String(content);
        }
        return element;
    },

    showLoading(targetGrid = this.grid) {
        if (!targetGrid) return;
        targetGrid.replaceChildren();
        if (this.empty) this.empty.classList.add("hidden");
        if (this.error) this.error.classList.add("hidden");

        const count = targetGrid.id === "main-event-grid" ? 4 : 8;
        for (let i = 0; i < count; i++) {
            targetGrid.appendChild(this.createSkeleton());
        }
    },

    createSkeleton() {
        const skeleton = this.createElement("div", "event-card loading-skeleton");
        const imageWrapper = this.createElement("div", "card-image-wrapper");
        imageWrapper.appendChild(this.createElement("div", "skeleton-img"));
        skeleton.appendChild(imageWrapper);

        const content = this.createElement("div", "card-content");
        const badges = this.createElement("div", "card-badges");
        badges.appendChild(this.createElement("div", "skeleton-badge"));
        badges.appendChild(this.createElement("div", "skeleton-badge"));
        content.appendChild(badges);
        content.appendChild(this.createElement("div", "skeleton-text skeleton-title"));
        const line = this.createElement("div", "skeleton-text");
        line.style.width = "70%";
        content.appendChild(line);
        const footer = this.createElement("div", "card-footer");
        const date = this.createElement("div", "skeleton-text");
        date.style.width = "100px";
        date.style.marginBottom = "0";
        footer.appendChild(date);
        content.appendChild(footer);
        skeleton.appendChild(content);
        return skeleton;
    },

    showError() {
        if (this.grid) this.grid.replaceChildren();
        if (this.error) this.error.classList.remove("hidden");
        if (this.empty) this.empty.classList.add("hidden");
    },

    showEmpty() {
        if (this.grid) this.grid.replaceChildren();
        if (this.empty) this.empty.classList.remove("hidden");
        if (this.error) this.error.classList.add("hidden");
    },

    init(targetGrid = this.grid) {
        if (!targetGrid) return;

        targetGrid.onclick = (event) => {
            const card = event.target.closest(".event-card");
            if (!card || card.classList.contains("loading-skeleton")) return;

            const url = card.dataset.url;
            if (url && url !== "undefined" && url !== "null") {
                window.open(url, "_blank", "noopener,noreferrer");
            }
        };
    },

    renderEvents(events, targetGrid = this.grid) {
        if (!targetGrid) return;
        targetGrid.replaceChildren();

        if (!Array.isArray(events) || events.length === 0) {
            if (targetGrid === this.grid) this.showEmpty();
            return;
        }

        if (this.empty) this.empty.classList.add("hidden");
        if (this.error) this.error.classList.add("hidden");
        this.reclinerImageUrl = this.findReclinerImageUrl(events);

        const fragment = document.createDocumentFragment();
        events.forEach((event) => {
            fragment.appendChild(this.createCard(event));
        });
        targetGrid.appendChild(fragment);
    },

    findReclinerImageUrl(events) {
        const matched = events.find((event) => {
            const title = this.text(event.title);
            return title.includes("리클라이너") && title.includes("오픈") && this.text(event.imageUrl);
        });
        return matched ? this.text(matched.imageUrl) : "";
    },

    replacementImageUrl(event, currentUrl) {
        const title = this.text(event.title);
        if (!title.includes("리클라이너") || !title.includes("리뉴얼")) {
            return "";
        }
        const replacement = this.text(this.reclinerImageUrl);
        return replacement && replacement !== currentUrl ? replacement : "";
    },

    createCard(event) {
        const card = this.createElement("article", "event-card");
        card.dataset.eventId = this.text(event.id);
        card.dataset.url = this.text(event.eventUrl);
        const title = this.text(event.title, "Event");

        const imageWrapper = this.createElement("div", "card-image-wrapper");
        imageWrapper.style.pointerEvents = "none";
        const image = document.createElement("img");
        image.src = this.text(event.imageUrl, "https://via.placeholder.com/400x225?text=No+Image");
        image.alt = title;
        image.loading = "lazy";
        image.decoding = "async";
        image.addEventListener("error", () => {
            const replacement = this.replacementImageUrl(event, image.src);
            if (replacement && image.dataset.replaced !== "true") {
                image.dataset.replaced = "true";
                image.src = replacement;
                return;
            }
            image.remove();
            imageWrapper.classList.add("is-image-missing");
            if (!imageWrapper.querySelector(".event-image-fallback")) {
                const fallback = this.createElement("div", "event-image-fallback");
                fallback.appendChild(this.createElement("span", null, title));
                imageWrapper.prepend(fallback);
            }
        }, { once: true });
        imageWrapper.appendChild(image);

        if (this.text(event.dDay)) {
            imageWrapper.appendChild(this.createElement("span", "d-day-badge", event.dDay));
        }

        const overlay = this.createElement("div", "card-overlay");
        overlay.appendChild(this.createElement("span", null, "이벤트 보기"));
        imageWrapper.appendChild(overlay);
        card.appendChild(imageWrapper);

        const content = this.createElement("div", "card-content");
        content.style.pointerEvents = "none";
        const badges = this.createElement("div", "card-badges");
        const source = this.text(event.source, "EVENT");
        badges.appendChild(this.createElement("span", `badge badge-${source.toLowerCase()}`, source));
        badges.appendChild(this.createElement("span", "badge badge-category", this.text(event.category, "ALL")));
        content.appendChild(badges);
        content.appendChild(this.createElement("h3", "card-title", title));

        const footer = this.createElement("div", "card-footer");
        footer.appendChild(this.createElement("p", "card-date", `${this.formatDate(event.startDate)} ~ ${this.formatDate(event.endDate)}`));
        content.appendChild(footer);
        card.appendChild(content);

        return card;
    },

    formatDate(dateStr) {
        const value = this.text(dateStr);
        return value ? value.replace(/-/g, ".") : "상시 진행";
    },
};
