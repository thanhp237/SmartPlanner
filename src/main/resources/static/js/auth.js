import { apiRequest } from './api.js';

export let currentUser = null;

export function redirectToAuthLogin() {
    if (window.location.pathname.startsWith("/auth/")) return;
    window.location.href = "/auth/login.html";
}

export function updateHeaderUser(me) {
    const userEl = document.getElementById("header-user");
    if (!userEl) return;
    if (!me || typeof me !== "object") {
        userEl.textContent = "User";
        return;
    }
    const display = (me.fullName || me.email || "").trim();
    userEl.textContent = display ? display : "User";
}

export async function checkSession() {
    const me = await apiRequest("/api/auth/me", { method: "GET" });
    if (!me || typeof me === "string") {
        redirectToAuthLogin();
        return false;
    }
    currentUser = me;
    updateHeaderUser(me);
    window.currentUser = currentUser;
    return true;
}
