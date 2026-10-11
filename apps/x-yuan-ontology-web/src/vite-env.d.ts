/// <reference types="vite/client" />

export {};

// eslint-disable-next-line @typescript-eslint/no-unused-vars
interface ImportMetaEnv {
	readonly VITE_BASE_URL: string;
	readonly VITE_APP_BASE_URL: string;
	readonly VITE_APP_BASE_URL_PROXY: string;
	readonly VITE_APP_BASE_LOG_SERVER_URL: string;
}
