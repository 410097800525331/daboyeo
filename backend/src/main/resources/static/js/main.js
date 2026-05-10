const MainApp = {
    eventGrid: document.getElementById("main-event-grid"),

    async init() {
        if (!this.eventGrid) return;

        Render.init(this.eventGrid);
        await this.loadPopularEvents();
    },

    async loadPopularEvents() {
        Render.showLoading(this.eventGrid);

        try {
            const events = await API.fetchEvents({ limit: 4 });
            Render.renderEvents(events.slice(0, 4), this.eventGrid);
        } catch (error) {
            console.error("Failed to load popular events:", error);
            if (this.eventGrid) this.eventGrid.replaceChildren();
        }
    },
};

document.addEventListener("DOMContentLoaded", () => {
    void MainApp.init();
});
