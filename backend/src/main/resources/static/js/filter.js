const Filter = {
    providerSelect: document.getElementById('provider-filter'),
    categoryTabs: document.getElementById('category-tabs'),

    currentProvider: '',
    currentCategory: '',

    init(onFilterChange) {
        // Provider change
        this.providerSelect.addEventListener('change', (e) => {
            this.currentProvider = e.target.value;
            onFilterChange();
        });

        // Category tab change
        this.categoryTabs.addEventListener('click', (e) => {
            const btn = e.target.closest('.tab-btn');
            if (!btn) return;

            // Update UI
            this.categoryTabs.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // Update state
            this.currentCategory = btn.dataset.category;
            onFilterChange();
        });
    },

    getFilters() {
        return {
            provider: this.currentProvider,
            category: this.currentCategory
        };
    }
};
