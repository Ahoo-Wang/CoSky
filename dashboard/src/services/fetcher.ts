import {fetcher, UrlBuilder} from "@ahoo-wang/fetcher";
import {tokenRefresher, tokenStorage} from "../security/tokenStorage.ts";
import {CoSecConfigurer} from "@ahoo-wang/fetcher-cosec";

fetcher.urlBuilder = new UrlBuilder(import.meta.env.VITE_API_BASE_URL);
fetcher.timeout = 1000 * 60 * 2;

export const coSecConfigurer = new CoSecConfigurer({
    appId: 'cosky',
    tokenRefresher: tokenRefresher,
    tokenStorage: tokenStorage,
    onUnauthorized: () => {
        window.location.replace("/login");
    },
});

coSecConfigurer.applyTo(fetcher);