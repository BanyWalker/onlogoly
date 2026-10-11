import { createRouter, createWebHistory } from "vue-router";

import { appConfig } from "@/config/appConfig";
import AppLayout from "@/layouts/AppLayout.vue";

import { appRouteChildren, notFoundRoute } from "./routes";

const router = createRouter({
	history: createWebHistory(import.meta.env.VITE_BASE_URL || "/"),
	routes: [
		{
			path: "/",
			component: AppLayout,
			redirect: { name: appConfig.homeRouteName },
			children: [...appRouteChildren, notFoundRoute]
		}
	]
});

export default router;
