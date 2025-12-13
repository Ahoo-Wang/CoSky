import {IGNORE_REFRESH_TOKEN_ATTRIBUTE_KEY, TokenStorage} from '@ahoo-wang/fetcher-cosec';
import type {TokenRefresher} from '@ahoo-wang/fetcher-cosec';
import {CompositeToken} from "../generated";
import {authenticateApiClient} from "./clients.ts";

const tokenStorage = new TokenStorage({
    earlyPeriod: 8,
});

const tokenRefresher: TokenRefresher = {
    refresh(token: CompositeToken): Promise<CompositeToken> {
        const username = tokenStorage.get()?.access?.payload?.sub
        if (!username) {
            return Promise.reject(new Error('No username'));
        }
        return authenticateApiClient.refresh(username, {
            body: token
        }, {
            [IGNORE_REFRESH_TOKEN_ATTRIBUTE_KEY]: true
        })
    }
}

export {tokenRefresher, tokenStorage};