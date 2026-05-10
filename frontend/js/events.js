const EventsApp = {
    async init() {
        Filter.init(() => this.loadEvents());
        Render.init();
        await this.loadEvents();
    },

    async loadEvents() {
        Render.showLoading();

        const { provider, category } = Filter.getFilters();

        try {
            const events = await API.fetchEvents({ provider, category, limit: 80 });
            Render.renderEvents(events);
        } catch (error) {
            console.error("Failed to load events:", error);
            Render.showError();
        }
    },
};

document.addEventListener("DOMContentLoaded", () => {
    void EventsApp.init();
});
