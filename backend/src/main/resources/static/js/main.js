const MainApp = {
    eventGrid: document.getElementById('main-event-grid'),

    async init() {
        if (!this.eventGrid) return;

        Render.init(this.eventGrid);
        await this.loadPopularEvents();
    },

    async loadPopularEvents() {
        Render.showLoading(this.eventGrid);

        try {
            // Fetch all events and filter TOP 3
            const events = await API.fetchEvents();

            // Sort criteria:
            // 1. Not ended (handled by backend usually, but let's be safe)
            // 2. HOT category first
            // 3. Newest startDate first
            const sortedEvents = events.sort((a, b) => {
                // HOT priority
                const aHot = a.category === 'HOT' ? 1 : 0;
                const bHot = b.category === 'HOT' ? 1 : 0;
                if (aHot !== bHot) return bHot - aHot;

                // Newest first
                return new Date(b.startDate) - new Date(a.startDate);
            });

            const top4 = sortedEvents.slice(0, 4);
            Render.renderEvents(top4, this.eventGrid);
        } catch (error) {
            console.error('Failed to load popular events:', error);
            if (this.eventGrid) this.eventGrid.innerHTML = '';
        }
    }
};

document.addEventListener('DOMContentLoaded', () => {
    MainApp.init();
});
