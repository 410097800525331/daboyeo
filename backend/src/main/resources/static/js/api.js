const API = {
    async fetchEvents(provider = '', category = '') {
        const params = new URLSearchParams();
        if (provider) params.append('source', provider); // Backend uses 'source'
        if (category) params.append('category', category);

        const url = `/api/events${params.toString() ? '?' + params.toString() : ''}`;

        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error('API request failed');
            return await response.json();
        } catch (error) {
            console.error('Fetch error:', error);
            throw error;
        }
    }
};
