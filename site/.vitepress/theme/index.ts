import DefaultTheme from "vitepress/theme";
import LandingHome from "./LandingHome.vue";
import Layout from "./Layout.vue";
import "./styles.css";

export default {
  extends: DefaultTheme,
  Layout,
  enhanceApp({ app }) {
    app.component("LandingHome", LandingHome);
  },
};
