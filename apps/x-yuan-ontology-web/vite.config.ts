import path from "node:path";
import { fileURLToPath, URL } from "node:url";

import vue from "@vitejs/plugin-vue";
import { defineConfig, loadEnv } from "vite";

const rootDir = path.dirname(fileURLToPath(import.meta.url));
const srcDir = fileURLToPath(new URL("./src", import.meta.url));

const nodeModulesPattern = /[\\/]node_modules[\\/]/;
const frameworkModulesPattern = /[\\/]node_modules[\\/](vue|vue-router|pinia|vue-i18n)[\\/]/;
const elementPlusModulesPattern = /[\\/]node_modules[\\/](element-plus|@element-plus|@vueuse)[\\/]/;

function getAssetFileName(assetInfo: { name?: string }): string {
	const assetName = assetInfo.name ?? "";
	const extension = path.extname(assetName).slice(1);

	if (/^(png|jpe?g|gif|svg|webp|avif|ico)$/i.test(extension)) {
		return "assets/images/[name]-[hash][extname]";
	}

	if (/^(woff2?|eot|ttf|otf)$/i.test(extension)) {
		return "assets/fonts/[name]-[hash][extname]";
	}

	if (/^(mp4|webm|ogg|mp3|wav|flac|aac)$/i.test(extension)) {
		return "assets/media/[name]-[hash][extname]";
	}

	return "assets/[name]-[hash][extname]";
}

export default defineConfig(({ mode }) => {
	const env = loadEnv(mode, rootDir, "");
	const enableSourceMap = mode === "development";

	return {
		base: env.VITE_BASE_URL || "/",
		plugins: [vue()],
		resolve: {
			alias: {
				"@": srcDir
			}
		},
		server: {
			host: "0.0.0.0",
			port: 5173,
			open: false,
			proxy: {
				// 后端建模 API（services/api，默认 127.0.0.1:8080）
				"^/api(?:/|$)": {
					target: env.VITE_API_PROXY_TARGET || "http://127.0.0.1:8080",
					changeOrigin: true
				}
			}
		},
		build: {
			outDir: "dist",
			assetsDir: "assets",
			emptyOutDir: true,
			sourcemap: enableSourceMap,
			reportCompressedSize: true,
			// The app currently installs Element Plus globally, so the vendor chunk
			// is expected to be larger than Vite's default warning threshold.
			chunkSizeWarningLimit: 1100,
			rollupOptions: {
				output: {
					entryFileNames: "assets/[name]-[hash].js",
					chunkFileNames: "assets/[name]-[hash].js",
					assetFileNames: getAssetFileName,
					codeSplitting: {
						groups: [
							{
								name: "framework",
								test: frameworkModulesPattern,
								priority: 30
							},
							{
								name: "element-plus",
								test: elementPlusModulesPattern,
								priority: 20
							},
							{
								name: "vendor",
								test: nodeModulesPattern,
								priority: 10
							}
						]
					}
				}
			}
		}
	};
});
