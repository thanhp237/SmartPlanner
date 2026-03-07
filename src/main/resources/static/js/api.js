export function showLoading(show) {
    const el = document.getElementById("loading-overlay");
    if (el) el.style.display = show ? "flex" : "none";
}

export function showToast(msg, type = "info") {
    const container = document.getElementById("toast-container");
    if (!container) return;
    const toast = document.createElement("div");

    let alertClass = "";
    let iconColor = "currentColor";
    let title = "";

    switch (type) {
        case "error":
        case "danger":
            alertClass = "text-fg-danger-strong bg-danger-soft border-danger-subtle";
            title = "Lỗi!";
            break;
        case "success":
            alertClass = "text-fg-success-strong bg-success-soft border-success-subtle";
            title = "Thành công!";
            break;
        case "warning":
            alertClass = "text-fg-warning bg-warning-soft border-warning-subtle";
            title = "Cảnh báo!";
            break;
        default: // info
            alertClass = "text-fg-brand-strong bg-brand-softer border-brand-subtle";
            title = "Thông báo!";
            break;
    }

    toast.className = `flex items-start sm:items-center p-4 mb-4 text-sm rounded-base border ${alertClass} opacity-0 translate-y-4 transition-all duration-300`;
    toast.setAttribute("role", "alert");

    const isList = Array.isArray(msg);

    if (isList) {
        toast.innerHTML = `
            <svg class="w-4 h-4 me-2 shrink-0" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 11h2v5m-2 0h4m-2.592-8.5h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"/></svg>
            <div>
                <span class="font-medium">${title}</span>
                <ul class="mt-2 list-disc list-outside space-y-1 ps-2.5">
                    ${msg.map(m => `<li>${m}</li>`).join("")}
                </ul>
            </div>
        `;
    } else {
        toast.innerHTML = `
            <svg class="w-4 h-4 me-2 shrink-0 mt-0.5 sm:mt-0" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 11h2v5m-2 0h4m-2.592-8.5h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"/></svg>
            <p><span class="font-medium me-1">${title}</span> ${msg}</p>
        `;
    }

    container.appendChild(toast);

    // Trigger entrance animation
    requestAnimationFrame(() => {
        toast.classList.remove("opacity-0", "translate-y-4");
        toast.classList.add("opacity-100", "translate-y-0");
    });

    // Final dismissal logic
    setTimeout(() => {
        toast.classList.remove("opacity-100", "translate-y-0");
        toast.classList.add("opacity-0", "translate-y-2");
        setTimeout(() => toast.remove(), 300);
    }, 4500);
}

export async function apiRequest(path, options) {
    if (options && options.showLoading !== false) showLoading(true);
    try {
        const res = await fetch(path, {
            credentials: "include",
            headers: {
                "Content-Type": "application/json",
                ...(options && options.headers ? options.headers : {})
            },
            ...options
        });
        if (res.status === 401) {
            // Redirect without importing auth to avoid circular deps
            if (!window.location.pathname.startsWith("/auth/")) {
                window.location.href = "/auth/login.html";
            }
            return null;
        }
        const text = await res.text();
        if (res.status === 204 || text === "") return null;
        if (!res.ok) {
            console.error("API error", path, res.status, text);
            try {
                const errJson = JSON.parse(text);
                showToast(errJson.message || errJson.error || text, "error");
            } catch {
                showToast(text || ("Lỗi API " + res.status), "error");
            }
            return null;
        }
        try { return JSON.parse(text); } catch { return text; }
    } finally {
        if (options && options.showLoading !== false) showLoading(false);
    }
}

export function renderAlert(msg, type = "danger") {
    let alertClass = "";
    let title = "";
    switch (type) {
        case "error":
        case "danger":
            alertClass = "text-fg-danger-strong bg-danger-soft border-danger-subtle";
            title = "Danger alert!";
            break;
        case "success":
            alertClass = "text-fg-success-strong bg-success-soft border-success-subtle";
            title = "Success alert!";
            break;
        case "warning":
            alertClass = "text-fg-warning bg-warning-soft border-warning-subtle";
            title = "Warning alert!";
            break;
        case "dark":
            alertClass = "text-heading bg-neutral-secondary-medium border-default-medium";
            title = "Dark alert!";
            break;
        default: // info
            alertClass = "text-fg-brand-strong bg-brand-softer border-brand-subtle";
            title = "Info alert!";
            break;
    }

    return `
        <div class="flex items-start sm:items-center p-4 mb-4 text-sm ${alertClass} rounded-base border" role="alert">
            <svg class="w-4 h-4 me-2 shrink-0 mt-0.5 sm:mt-0" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 11h2v5m-2 0h4m-2.592-8.5h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"/></svg>
            <p><span class="font-medium me-1">${title}</span> ${msg}</p>
        </div>
    `;
}

export function renderAlertList(title, items, type = "danger") {
    let alertClass = "";
    let typeLabel = "Info";
    switch (type) {
        case "error":
        case "danger":
            alertClass = "text-fg-danger-strong bg-danger-soft border-danger-subtle";
            typeLabel = "Danger";
            break;
        case "success":
            alertClass = "text-fg-success-strong bg-success-soft border-success-subtle";
            typeLabel = "Success";
            break;
        case "warning":
            alertClass = "text-fg-warning bg-warning-soft border-warning-subtle";
            typeLabel = "Warning";
            break;
        default: // info
            alertClass = "text-fg-brand-strong bg-brand-softer border-brand-subtle";
            typeLabel = "Info";
            break;
    }

    return `
        <div class="flex p-4 mb-4 text-sm ${alertClass} rounded-base border" role="alert">
            <svg class="w-4 h-4 me-2 shrink-0" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24"><path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 11h2v5m-2 0h4m-2.592-8.5h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"/></svg>
            <span class="sr-only">${typeLabel}</span>
            <div>
                <span class="font-medium">${title}</span>
                <ul class="mt-2 list-disc list-outside space-y-1 ps-2.5">
                    ${items.map(item => `<li>${item}</li>`).join("")}
                </ul>
            </div>
        </div>
    `;
}
