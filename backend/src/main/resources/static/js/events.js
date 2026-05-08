const EventsApp = {
    async init() {
        console.log('Initializing Movie Events App...');

        // Setup filter listeners
        Filter.init(() => this.loadEvents());
        Render.init();

        // Initial load
        await this.loadEvents();
    },

    async loadEvents() {
        Render.showLoading();

        const { provider, category } = Filter.getFilters();

        try {
            const events = await API.fetchEvents(provider, category);
            Render.renderEvents(events);
        } catch (error) {
            Render.showError();
        }
    }
};

// Start app
document.addEventListener('DOMContentLoaded', () => {
    EventsApp.init();
});
