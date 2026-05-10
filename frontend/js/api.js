(function initDaboyeoApi(global) {
    const FRONTEND_DEV_PORTS = new Set(["3000", "5173", "5500", "5501", "5510"]);

    function getApiBaseUrl() {
        const configured = global.DABOYEO_API_BASE_URL;
        if (typeof configured === "string" && configured.trim()) {
            return configured.trim().replace(/\/$/, "");
        }

        const { protocol, hostname, port, origin } = global.location;
        if ((hostname === "localhost" || hostname === "127.0.0.1") && FRONTEND_DEV_PORTS.has(port)) {
            return `${protocol}//${hostname}:5500`;
        }
        return origin;
    }

    function normalizeFetchArgs(providerOrOptions = "", category = "") {
        if (providerOrOptions && typeof providerOrOptions === "object") {
            return providerOrOptions;
        }
        return { provider: providerOrOptions, category };
    }

    global.API = {
        getApiBaseUrl,

        async fetchEvents(providerOrOptions = "", category = "") {
            const options = normalizeFetchArgs(providerOrOptions, category);
            const params = new URLSearchParams();
            if (options.provider) params.append("source", options.provider);
            if (options.category) params.append("category", options.category);
            if (options.limit) params.append("limit", String(options.limit));

            const query = params.toString();
            const url = `${getApiBaseUrl()}/api/events${query ? `?${query}` : ""}`;
            const response = await fetch(url, {
                headers: { Accept: "application/json" },
                cache: "no-store",
            });
            if (!response.ok) {
                throw new Error("event api request failed");
            }
            return response.json();
        },
    };
})(window);
