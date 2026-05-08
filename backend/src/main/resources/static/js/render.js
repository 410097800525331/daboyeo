const Render = {
    // Default grid (from events.html)
    grid: document.getElementById('event-grid'),
    empty: document.getElementById('empty-state'),
    error: document.getElementById('error-state'),

    showLoading(targetGrid = this.grid) {
        if (!targetGrid) return;
        targetGrid.innerHTML = '';
        if (this.empty) this.empty.classList.add('hidden');
        if (this.error) this.error.classList.add('hidden');

        // Show 4-6 skeletons
        const count = targetGrid.id === 'main-event-grid' ? 3 : 6;
        for (let i = 0; i < count; i++) {
            targetGrid.appendChild(this.createSkeleton());
        }
    },

    createSkeleton() {
        const skeleton = document.createElement('div');
        skeleton.className = 'event-card loading-skeleton';
        skeleton.innerHTML = `
            <div class="card-image-wrapper">
                <div class="skeleton-img"></div>
            </div>
            <div class="card-content">
                <div class="card-badges">
                    <div class="skeleton-badge"></div>
                    <div class="skeleton-badge"></div>
                </div>
                <div class="skeleton-text skeleton-title"></div>
                <div class="skeleton-text" style="width: 70%"></div>
                <div class="card-footer">
                    <div class="skeleton-text" style="width: 100px; margin-bottom: 0;"></div>
                </div>
            </div>
        `;
        return skeleton;
    },

    showError() {
        if (this.grid) this.grid.innerHTML = '';
        if (this.error) this.error.classList.remove('hidden');
        if (this.empty) this.empty.classList.add('hidden');
    },

    showEmpty() {
        if (this.grid) this.grid.innerHTML = '';
        if (this.empty) this.empty.classList.remove('hidden');
        if (this.error) this.error.classList.add('hidden');
    },

    init(targetGrid = this.grid) {
        if (!targetGrid) return;

        // Remove existing listener to avoid duplicates if re-init
        targetGrid.onclick = (e) => {
            const card = e.target.closest('.event-card');
            if (!card || card.classList.contains('loading-skeleton')) return;

            const url = card.dataset.url;
            const eventId = card.dataset.eventId;

            if (url && url !== 'undefined' && url !== 'null') {
                console.log("[EVENT CLICK]", url);
                window.open(url, '_blank', 'noopener,noreferrer');
            } else {
                console.warn("[EVENT CLICK] eventUrl missing for event:", eventId);
            }
        };
    },

    renderEvents(events, targetGrid = this.grid) {
        if (!targetGrid) return;
        targetGrid.innerHTML = '';

        if (events.length === 0) {
            if (targetGrid === this.grid) this.showEmpty();
            return;
        }

        if (this.empty) this.empty.classList.add('hidden');
        if (this.error) this.error.classList.add('hidden');

        const fragment = document.createDocumentFragment();
        events.forEach(event => {
            fragment.appendChild(this.createCard(event));
        });
        targetGrid.appendChild(fragment);
    },

    createCard(event) {
        const card = document.createElement('article');
        card.className = 'event-card';
        card.dataset.eventId = event.id;
        card.dataset.url = event.eventUrl || '';

        const providerClass = `badge-${event.source.toLowerCase()}`;

        card.innerHTML = `
            <div class="card-image-wrapper" style="pointer-events: none;">
                <img src="${event.imageUrl || 'https://via.placeholder.com/400x225?text=No+Image'}"
                     alt="${event.title}"
                     loading="lazy"
                     onerror="this.src='https://via.placeholder.com/400x225?text=Image+Load+Error'">
                ${event.dDay ? `<span class="d-day-badge">${event.dDay}</span>` : ''}
                <div class="card-overlay">
                    <span>이벤트 보기 <i class="fas fa-external-link-alt"></i></span>
                </div>
            </div>
            <div class="card-content" style="pointer-events: none;">
                <div class="card-badges">
                    <span class="badge ${providerClass}">${event.source}</span>
                    <span class="badge badge-category">${event.category}</span>
                </div>
                <h3 class="card-title">${event.title}</h3>
                <div class="card-footer">
                    <p class="card-date"><i class="far fa-calendar-alt"></i> ${this.formatDate(event.startDate)} ~ ${this.formatDate(event.endDate)}</p>
                </div>
            </div>
        `;

        return card;
    },

    formatDate(dateStr) {
        if (!dateStr) return '상시 진행';
        return dateStr.replace(/-/g, '.');
    }
};
