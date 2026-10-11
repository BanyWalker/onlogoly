import { createApp } from "vue";
import ElementPlus from "element-plus";
import { createPinia } from "pinia";

import App from "./App.vue";
import "./utils/getBrowser";
import router from "./router";
import i18n from "./locales";
import { ctffElementPlusInstallOptions } from "./config/ctffElementPlusConfig";
import { initializeCtffTheme } from "./hooks/useCtffTheme";
import "./assets/css/index.scss";
import "./assets/css/tailwind.css";

initializeCtffTheme();

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(i18n);
app.use(ElementPlus, ctffElementPlusInstallOptions);

app.mount("#app");
