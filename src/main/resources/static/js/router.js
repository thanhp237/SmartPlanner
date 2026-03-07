// router.js — pure view-switching utility (no load* dependencies)
export const VIEW_NAMES = ["dashboard", "timetable", "courses", "tasks", "settings"];

export function showView(name) {
    VIEW_NAMES.forEach(v => {
        const el = document.getElementById("view-" + v);
        if (el) {
            const isActive = v === name;
            el.classList.toggle("sp-view-active", isActive);
            // Remove "hidden" class that may be on settings
            el.classList.remove("hidden");
            el.style.display = isActive ? "block" : "none";
        }
    });
    document.querySelectorAll(".sp-nav-item, .sp-sidebar-item").forEach(btn => {
        const view = btn.getAttribute("data-view");
        const isActive = view === name;

        if (isActive) {
            btn.classList.add("text-white", "bg-sp-green", "shadow-md", "shadow-green-500/20");
            btn.classList.remove("text-slate-600", "hover:bg-slate-100");
        } else {
            btn.classList.remove("text-white", "bg-sp-green", "shadow-md", "shadow-green-500/20");
            btn.classList.add("text-slate-600", "hover:bg-slate-100");
        }
    });
}
