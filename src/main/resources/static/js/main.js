
import { apiRequest, showLoading, showToast, renderAlert, renderAlertList } from './api.js';
import { checkSession, redirectToAuthLogin, updateHeaderUser } from './auth.js';
import { showView } from './router.js';

let currentWeekOffset = 0;
let currentUser = null;

// ── Global navigation (defined here — has access to all load* functions) ──
window.appNavigate = async function appNavigate(view) {
    if (!view) return;
    showView(view);
    if (view === "dashboard") await loadDashboard();
    else if (view === "timetable") await loadTimetable();
    else if (view === "courses") await loadCourses();
    else if (view === "tasks") await loadTasks();
    else if (view === "settings") {
        await loadPreferences();
        await loadAvailabilityModal();
    }

    // Re-render Lucide icons after navigation (for dynamic content)
    if (window.lucide) window.lucide.createIcons();

    // Feature 6: check availability on dashboard/timetable
    if ((view === "dashboard" || view === "timetable" || view === "courses") && window.currentUser) {
        checkAndPromptAvailability();
    }
};

let currentTaskFilter = "ALL";

async function checkAndPromptAvailability() {
    const promptEl = document.getElementById("availability-prompt");
    if (!promptEl) return;
    try {
        const avail = await apiRequest("/api/settings/availability", { method: "GET", showLoading: false });
        if (!avail || avail.length === 0) {
            promptEl.classList.remove("translate-y-full");
        } else {
            promptEl.classList.add("translate-y-full");
        }
    } catch (e) { }
}

// ── Expose utilities globally (used by HTML onclick= attributes) ──
window.apiRequest = apiRequest;
window.showLoading = showLoading;
window.showToast = showToast;
window.checkSession = checkSession;
window.redirectToAuthLogin = redirectToAuthLogin;
window.updateHeaderUser = updateHeaderUser;
window.showView = showView;
window.openCourseForm = openCourseForm;
window.toggleCourseForm = toggleCourseForm;
window.toggleTaskForm = toggleTaskForm;
window.openTaskForm = openTaskForm;
window.deleteTask = deleteTask;
window.deleteCourse = deleteCourse;
window.openAvailFormModal = openAvailFormModal;
window.handleAvailSubmitModal = handleAvailSubmitModal;
window.deleteAvailabilityModal = deleteAvailabilityModal;
window.generateTimetable = generateTimetable;
window.exportTimetablePdf = exportTimetablePdf;
window.logout = logout;

// ── Helpers for inline onclick handlers ──
window.navWeek = async function (dir) {
    currentWeekOffset += dir;
    await loadTimetable();
};
window.setCalView = async function (mode) {
    window.currentCalendarMode = mode;
    const modes = { "day": "btn-view-day", "week": "btn-view-week" };
    Object.entries(modes).forEach(([m, id]) => {
        const b = document.getElementById(id);
        if (!b) return;
        if (m === mode) {
            b.classList.add("bg-white", "text-slate-800", "shadow-sm");
            b.classList.remove("text-slate-500");
        } else {
            b.classList.remove("bg-white", "text-slate-800", "shadow-sm");
            b.classList.add("text-slate-500");
        }
    });
    await loadTimetable();
};
window.setTaskFilter = function (tabEl, status) {
    document.querySelectorAll(".sp-task-tab").forEach(t => {
        t.classList.remove("active", "bg-sp-green", "text-white");
        t.classList.add("text-slate-500");
    });
    tabEl.classList.add("active", "bg-sp-green", "text-white");
    tabEl.classList.remove("text-slate-500");
    currentTaskFilter = status;
    loadTasks();
};



// ── UX Utilities ─────────────────────────────────────────────────────────



function confirmDialog(title, message, options) {
    return new Promise(resolve => {
        const overlay = document.getElementById("modal-overlay");
        const titleEl = document.getElementById("modal-title");
        const msgEl = document.getElementById("modal-message");
        const btnOk = document.getElementById("modal-confirm");
        const btnCancel = document.getElementById("modal-cancel");

        if (!overlay) {
            resolve(window.confirm(message)); // Fallback
            return;
        }

        titleEl.textContent = title;
        msgEl.textContent = message;
        const defaultCancel = btnCancel.textContent;
        const defaultOk = btnOk.textContent;
        if (options && typeof options === "object") {
            if (options.cancelLabel) btnCancel.textContent = options.cancelLabel;
            if (options.confirmLabel) btnOk.textContent = options.confirmLabel;
        }
        overlay.style.display = "flex";

        const cleanup = () => {
            overlay.style.display = "none";
            btnOk.onclick = null;
            btnCancel.onclick = null;
            btnCancel.textContent = defaultCancel;
            btnOk.textContent = defaultOk;
        };

        btnOk.onclick = () => {
            cleanup();
            resolve(true);
        };

        btnCancel.onclick = () => {
            cleanup();
            resolve(false);
        };
    });
}

function runReflectionFlow(sessionId, hours) {
    return new Promise(resolve => {
        const overlay = document.getElementById("reflection-overlay");
        const formEl = document.getElementById("reflection-form");
        const loadingEl = document.getElementById("reflection-loading");
        const resultEl = document.getElementById("reflection-result");
        const noteEl = document.getElementById("reflection-note");
        const diffEl = document.getElementById("reflection-difficulty");
        const errEl = document.getElementById("reflection-error");

        const noteViewEl = document.getElementById("reflection-note-view");
        const qualityEl = document.getElementById("reflection-quality");
        const summaryEl = document.getElementById("reflection-summary");
        const nextEl = document.getElementById("reflection-next");
        const revisionEl = document.getElementById("reflection-revision");

        const btnCancel = document.getElementById("reflection-cancel");
        const btnSave = document.getElementById("reflection-save");
        const btnClose = document.getElementById("reflection-close");

        if (!overlay) {
            resolve(null);
            return;
        }

        const showForm = () => {
            if (formEl) formEl.style.display = "block";
            if (loadingEl) loadingEl.style.display = "none";
            if (resultEl) resultEl.style.display = "none";
            if (errEl) errEl.innerHTML = "";
            if (noteEl) noteEl.value = "";
            if (diffEl) diffEl.value = "";
            if (noteViewEl) noteViewEl.textContent = "";
        };

        const showLoading = () => {
            if (formEl) formEl.style.display = "none";
            if (loadingEl) loadingEl.style.display = "block";
            if (resultEl) resultEl.style.display = "none";
            if (errEl) errEl.innerHTML = "";
            if (noteViewEl) noteViewEl.textContent = "";
        };

        const showResult = (data) => {
            if (formEl) formEl.style.display = "none";
            if (loadingEl) loadingEl.style.display = "none";
            if (resultEl) resultEl.style.display = "block";

            if (errEl) errEl.innerHTML = "";
            if (noteViewEl) noteViewEl.textContent = data?.note || "";

            if (qualityEl) qualityEl.textContent = data?.aiQualityScore ?? "";
            if (summaryEl) summaryEl.textContent = data?.aiSummary ?? "";
            if (nextEl) nextEl.textContent = data?.aiNextAction ?? "";
            if (revisionEl) revisionEl.textContent = data?.aiRevisionSuggestion ?? "";
            if (data?.aiStatus === "FAILED" && errEl) {
                errEl.innerHTML = renderAlert(data?.aiError || "AI phân tích thất bại", "error");
            }
        };

        const cleanup = () => {
            overlay.style.display = "none";
            btnCancel.onclick = null;
            btnSave.onclick = null;
            btnClose.onclick = null;
        };

        overlay.style.display = "flex";
        showForm();

        btnCancel.onclick = () => {
            cleanup();
            resolve(null);
        };

        btnClose.onclick = () => {
            cleanup();
        };

        btnSave.onclick = async () => {
            const note = (noteEl.value || "").trim();
            const difficulty = (diffEl.value || "").trim();

            showLoading();

            const payload = {
                actualHoursLogged: hours,
                difficulty: difficulty || null,
                note: note || null
            };

            const completeResult = await apiRequest("/api/sessions/" + sessionId + "/complete", {
                method: "PUT",
                body: JSON.stringify(payload),
                showLoading: false
            });

            if (completeResult === null) {
                errEl.innerHTML = renderAlert("Không thể lưu session", "error");
                showForm();
                return;
            }

            if (!note) {
                cleanup();
                showToast("Đã lưu session (bỏ qua AI vì note trống).", "info");
                resolve(completeResult);
                return;
            }

            let reflection = null;
            for (let i = 0; i < 12; i++) {
                reflection = await apiRequest("/api/sessions/" + sessionId + "/reflection", { method: "GET", showLoading: false });
                const status = (reflection?.aiStatus || "").toUpperCase();
                if (status === "DONE" || status === "FAILED") break;
                await new Promise(r => setTimeout(r, 1500));
            }

            showResult(reflection);
            resolve(completeResult);
        };
    });
}

function openReflectionViewer(session) {
    return new Promise(resolve => {
        const overlay = document.getElementById("reflection-overlay");
        const formEl = document.getElementById("reflection-form");
        const loadingEl = document.getElementById("reflection-loading");
        const resultEl = document.getElementById("reflection-result");
        const errEl = document.getElementById("reflection-error");

        const noteViewEl = document.getElementById("reflection-note-view");
        const qualityEl = document.getElementById("reflection-quality");
        const summaryEl = document.getElementById("reflection-summary");
        const nextEl = document.getElementById("reflection-next");
        const revisionEl = document.getElementById("reflection-revision");

        const btnCancel = document.getElementById("reflection-cancel");
        const btnSave = document.getElementById("reflection-save");
        const btnClose = document.getElementById("reflection-close");

        if (!overlay) {
            resolve(null);
            return;
        }

        const cleanup = () => {
            overlay.style.display = "none";
            if (btnCancel) btnCancel.style.display = "";
            if (btnSave) btnSave.style.display = "";
            btnClose.onclick = null;
        };

        const showLoadingState = () => {
            if (formEl) formEl.style.display = "none";
            if (loadingEl) loadingEl.style.display = "";
            if (resultEl) resultEl.style.display = "none";
            if (errEl) errEl.textContent = "";
        };

        const showResultState = (data) => {
            if (formEl) formEl.style.display = "none";
            if (loadingEl) loadingEl.style.display = "none";
            if (resultEl) resultEl.style.display = "block";

            if (noteViewEl) noteViewEl.textContent = data?.note || "(Không có ghi chú)";

            const status = (data?.aiStatus || "").toUpperCase();
            if (!data) {
                qualityEl.textContent = "";
                summaryEl.textContent = "";
                nextEl.textContent = "";
                revisionEl.textContent = "";
                if (errEl) errEl.textContent = "Không lấy được reflection";
                return;
            }

            qualityEl.textContent = data?.aiQualityScore ?? "-";
            summaryEl.textContent = data?.aiSummary ?? "-";
            nextEl.textContent = data?.aiNextAction ?? "-";
            revisionEl.textContent = data?.aiRevisionSuggestion ?? "-";

            if (status === "FAILED") {
                if (errEl) errEl.textContent = data?.aiError || "AI phân tích thất bại";
                return;
            }

            if (errEl) errEl.textContent = "";
        };

        overlay.style.display = "flex";
        if (btnCancel) btnCancel.style.display = "none";
        if (btnSave) btnSave.style.display = "none";

        // Show loading initially
        showLoadingState();

        btnClose.onclick = () => {
            cleanup();
            resolve(null);
        };

        (async () => {
            let reflection = null;
            // Poll for result, but show immediately if done
            reflection = await apiRequest("/api/sessions/" + session.id + "/reflection", { method: "GET", showLoading: false });

            // If already done or failed, show immediately
            const status = (reflection?.aiStatus || "").toUpperCase();
            if (status === "COMPLETED" || status === "DONE" || status === "FAILED") {
                showResultState(reflection);
                return;
            }

            // Otherwise poll
            for (let i = 0; i < 12; i++) {
                reflection = await apiRequest("/api/sessions/" + session.id + "/reflection", { method: "GET", showLoading: false });
                const st = (reflection?.aiStatus || "").toUpperCase();
                if (st === "COMPLETED" || st === "DONE" || st === "FAILED") break;
                await new Promise(r => setTimeout(r, 1200));
            }
            showResultState(reflection);
        })();
    });
}







function showDashboardSkeleton() {
    // Left empty for now, or you could implement Tailwind skeletons in HTML.
}

async function loadDashboard() {
    const todayStr = new Date().toLocaleDateString('vi-VN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
    const dateEl = document.getElementById("dashboard-current-date");
    if (dateEl) dateEl.textContent = todayStr;

    const [overview, deadlines, timetable, tasks] = await Promise.all([
        apiRequest("/api/dashboard/overview", { method: "GET" }),
        apiRequest("/api/dashboard/deadlines?limit=6", { method: "GET" }),
        apiRequest("/api/timetable?weekOffset=0", { method: "GET", showLoading: false }),
        apiRequest("/api/tasks", { method: "GET", showLoading: false }),
    ]);
    renderDashboardOverview(overview, deadlines, timetable, tasks);
}

function renderDashboardOverview(overview, deadlines, timetable, tasks) {
    const t = overview?.taskSummary || {};
    const c = overview?.courseSummary || {};
    const h = overview?.studyHours || {};

    // ── Row 1: KPI Cards ────────────────────────────────────────────
    const el = id => document.getElementById(id);

    if (el("dash-kpi-tasks")) {
        el("dash-kpi-tasks").innerHTML = `${t.open ?? 0} <span class="text-sm text-slate-400 font-normal">/ ${t.total ?? 0}</span>`;
    }

    if (el("dash-kpi-courses")) {
        el("dash-kpi-courses").innerHTML = `${c.active ?? 0} <span class="text-sm text-slate-400 font-normal">nghiêm túc</span>`;
    }

    if (el("dash-kpi-hours")) {
        const actual = h.actualThisWeek ?? 0;
        const planned = h.plannedThisWeek ?? 0;
        el("dash-kpi-hours").innerHTML = `${actual}h <span class="text-sm text-slate-400 font-normal">/ ${planned}h</span>`;
    }

    // ── Progress Ring ───────────────────────────────────────────────
    const rate = h.completionRate ?? 0;
    const ring = el("dash-progress-ring");
    const text = el("dash-progress-text");
    if (ring && text) {
        // Circumference for r=40 is 251.2
        const offset = 251.2 - (rate / 100) * 251.2;
        text.textContent = rate + "%";
        // Animate the ring
        setTimeout(() => {
            ring.style.strokeDashoffset = offset;
        }, 100);
    }

    // ── Recent Activity ─────────────────────────────────────────────
    const recentList = el("dash-recent-list");
    if (recentList) {
        recentList.innerHTML = "";

        let recentItems = [];
        if (timetable && Array.isArray(timetable.sessions)) {
            recentItems = timetable.sessions
                .filter(s => s.status === "COMPLETED" || s.status === "SKIPPED")
                .sort((a, b) => new Date(b.updatedAt || b.sessionDate) - new Date(a.updatedAt || a.sessionDate))
                .slice(0, 3);
        }

        if (recentItems.length === 0) {
            recentList.innerHTML = `
                <div class="px-6 py-8 text-center bg-slate-50/50 rounded-b-2xl">
                    <p class="text-slate-500 text-sm">Chưa có hoạt động học tập nào gần đây.</p>
                </div>`;
        } else {
            recentItems.forEach((s, idx) => {
                const isLast = idx === recentItems.length - 1;
                const isComplete = s.status === "COMPLETED";
                const icon = isComplete ? `<i data-lucide="check-circle-2" class="w-5 h-5 text-emerald-500"></i>` : `<i data-lucide="x-circle" class="w-5 h-5 text-rose-500"></i>`;
                const actionText = isComplete ? `Hoàn thành ca học` : `Bỏ lỡ hoặc bỏ qua`;

                recentList.innerHTML += `
                    <div class="px-6 py-4 flex items-start gap-4 hover:bg-slate-50 transition-colors ${!isLast ? 'border-b border-slate-100' : ''}">
                        <div class="mt-0.5">${icon}</div>
                        <div>
                            <p class="text-sm font-semibold text-slate-800">${actionText}</p>
                            <p class="text-xs text-slate-500 mt-1">${s.sessionDate} • ${s.startTime.slice(0, 5)} - ${s.endTime.slice(0, 5)}</p>
                        </div>
                    </div>
                `;
            });
            // Re-init lucide icons for newly appended strings
            if (window.lucide) window.lucide.createIcons();
        }
    }

    // ── Upcoming Deadlines ──────────────────────────────────────────
    const deadlinesList = el("dash-deadlines-list");
    const dashCount = el("dash-deadline-count");
    if (dashCount) dashCount.textContent = (deadlines && deadlines.length) ? deadlines.length : 0;

    if (deadlinesList) {
        deadlinesList.innerHTML = "";
        if (!Array.isArray(deadlines) || deadlines.length === 0) {
            deadlinesList.innerHTML = `
                <div class="flex-1 flex flex-col items-center justify-center text-center p-6 bg-slate-50/50 rounded-xl mb-2">
                    <div class="w-12 h-12 bg-white rounded-full shadow-sm flex items-center justify-center mb-3">
                        <i data-lucide="coffee" class="w-6 h-6 text-slate-400"></i>
                    </div>
                    <p class="text-slate-600 font-medium text-sm">Thảnh thơi!</p>
                    <p class="text-slate-400 text-xs mt-1">Không có deadline nào sắp tới.</p>
                </div>`;
        } else {
            deadlines.forEach(d => {
                const isUrgent = d.daysRemaining <= 2;
                deadlinesList.innerHTML += `
                    <div class="p-3 bg-white border border-slate-100 rounded-xl shadow-sm hover:shadow-md transition-shadow relative overflow-hidden group">
                        ${isUrgent ? '<div class="absolute left-0 top-0 bottom-0 w-1 bg-rose-500"></div>' : '<div class="absolute left-0 top-0 bottom-0 w-1 bg-amber-400"></div>'}
                        <div class="pl-3">
                            <h4 class="text-sm font-bold text-slate-800 line-clamp-1">${d.taskTitle} <span class="text-xs font-normal text-slate-500 ml-1">(${d.courseName})</span></h4>
                            <div class="flex items-center justify-between mt-2">
                                <span class="text-xs font-medium ${isUrgent ? 'text-rose-600 bg-rose-100' : 'text-amber-600 bg-amber-100'} px-2 py-0.5 rounded-md">
                                    ${d.daysRemaining === 0 ? 'Hôm nay' : (d.daysRemaining < 0 ? 'Quá hạn' : 'Còn ' + d.daysRemaining + ' ngày')}
                                </span>
                                <span class="text-xs text-slate-500 flex items-center gap-1">
                                    <i data-lucide="calendar" class="w-3 h-3"></i> ${d.deadlineDate || 'None'}
                                </span>
                            </div>
                        </div>
                    </div>
                `;
            });
        }
        if (window.lucide) window.lucide.createIcons();
    }
}

async function loadTimetable() {
    // Show skeleton while fetching
    const grid = document.getElementById("timetable-grid");
    if (grid) {
        grid.innerHTML = Array.from({ length: 7 }, () =>
            `<div class="sp-skeleton sp-skeleton-col"></div>`
        ).join("");
    }
    const data = await apiRequest("/api/timetable?weekOffset=" + currentWeekOffset, { method: "GET" });

    if (!data) return;
    const label = document.getElementById("week-label");
    label.textContent = data.weekStartDate + " - " + data.weekEndDate;
    const evalEl = document.getElementById("timetable-eval");
    if (evalEl) {
        if (data.evaluation) {
            const score = data.evaluation.score ?? 0;
            const level = (data.evaluation.level || "MEDIUM").toLowerCase();
            const feedback = data.evaluation.feedback || "";
            const circumference = 2 * Math.PI * 28; // r=28
            const offset = circumference - (score / 100) * circumference;

            const levelMap = {
                "high": { label: "Phong độ tuyệt vời", icon: "◈" },
                "medium": { label: "Lộ trình cân bằng", icon: "◍" },
                "low": { label: "Cần chú ý hơn", icon: "◆" }
            };
            const levelInfo = levelMap[level] || levelMap["medium"];

            evalEl.innerHTML = `
                <div class="sp-score-ring">
                    <svg width="64" height="64" viewBox="0 0 64 64">
                        <circle class="sp-score-ring-track" cx="32" cy="32" r="28"/>
                        <circle class="sp-score-ring-fill"
                            cx="32" cy="32" r="28"
                            stroke-dasharray="${circumference.toFixed(1)}"
                            stroke-dashoffset="${circumference.toFixed(1)}"
                            data-offset="${offset.toFixed(1)}"/>
                    </svg>
                    <span class="sp-score-ring-label">${score}</span>
                </div>
                <div class="sp-eval-text">
                    <div class="sp-eval-text-main">
                        <span>${levelInfo.icon}</span>
                        Điểm lịch: ${score}/100 &ndash; ${levelInfo.label}
                    </div>
                    <div class="sp-eval-text-sub">${feedback || "Cố gắng duy trì lộ trình học tập để đạt kết quả tốt nhất!"}</div>
                </div>`;
            evalEl.className = "sp-eval-banner score-" + level + " is-visible";
            // Animate ring after paint
            requestAnimationFrame(() => {
                const fill = evalEl.querySelector(".sp-score-ring-fill");
                if (fill) fill.style.strokeDashoffset = fill.dataset.offset;
            });
        } else {
            evalEl.className = "sp-eval-banner";
            evalEl.innerHTML = "";
        }
    }

    if (grid) grid.innerHTML = "";

    // Update legend note to clarify skip/regenerate behavior
    const legend = document.getElementById("timetable-legend");
    if (legend) {
        const note = legend.querySelector(".sp-legend-note");
        const now = new Date();
        let skippedInSlot = 0;
        let missedCount = 0;
        if (Array.isArray(data.sessions)) {
            data.sessions.forEach(s => {
                const st = (s.status || "").toUpperCase();
                const endParts = (s.endTime || "00:00").split(":");
                const dateParts = (s.sessionDate || "1970-01-01").split("-");
                const startParts = (s.startTime || "00:00").split(":");
                const sessionStart = new Date(parseInt(dateParts[0]), parseInt(dateParts[1]) - 1, parseInt(dateParts[2]), parseInt(startParts[0]), parseInt(startParts[1]), 0);
                const sessionEnd = new Date(parseInt(dateParts[0]), parseInt(dateParts[1]) - 1, parseInt(dateParts[2]), parseInt(endParts[0]), parseInt(endParts[1]), 0);
                const isWithinWindow = now >= sessionStart && now < sessionEnd;
                const isPast = now >= sessionEnd;
                if (st === "SKIPPED" && isWithinWindow) skippedInSlot++;
                if ((st === "PLANNED" && isPast) || (st === "SKIPPED" && isPast)) missedCount++;
            });
        }
        if (note) {
            if (skippedInSlot > 0 || missedCount > 0) {
                note.textContent = "Có " + skippedInSlot + " phiên Skipped (in slot), " + missedCount + " phiên Missed. Bấm Generate để sắp lại.";
            } else {
                note.textContent = "Bấm Generate để cập nhật lịch theo giờ rảnh và deadline.";
            }
        }
    }
    // Global or file-scope variables
    if (typeof window.currentCalendarMode === 'undefined') {
        window.currentCalendarMode = 'week';
    }

    const byDate = new Map();
    if (Array.isArray(data.sessions)) {
        data.sessions.forEach(s => {
            const key = s.sessionDate;
            if (!byDate.has(key)) byDate.set(key, []);
            byDate.get(key).push(s);
        });
    }

    const daysEl = document.getElementById("timetable-header-days");
    const gridInner = document.getElementById("timetable-grid");
    const timeAxis = document.getElementById("timetable-time-axis");
    const gridLines = document.getElementById("timetable-grid-lines");

    const dates = [];

    if (window.currentCalendarMode === 'day') {
        // Show only today
        const dt = new Date();
        const y = dt.getFullYear();
        const m = String(dt.getMonth() + 1).padStart(2, "0");
        const d = String(dt.getDate()).padStart(2, "0");
        dates.push(y + "-" + m + "-" + d);
        // Force the grid to 1 column
        if (gridInner) gridInner.className = "grid grid-cols-1 gap-3 sm:gap-4 h-full";
        if (daysEl) daysEl.className = "grid grid-cols-1 border-b border-slate-200 bg-slate-50/80 sticky top-0 z-10";
    } else if (window.currentCalendarMode === 'week') {
        // Show 7 days of the week starting from weekStartDate
        let cur = new Date(data.weekStartDate);
        for (let i = 0; i < 7; i++) {
            const y = cur.getFullYear();
            const m = String(cur.getMonth() + 1).padStart(2, "0");
            const d = String(cur.getDate()).padStart(2, "0");
            dates.push(y + "-" + m + "-" + d);
            cur.setDate(cur.getDate() + 1);
        }
        if (gridInner) gridInner.className = "grid grid-cols-7 gap-3 sm:gap-4 h-full";
        if (daysEl) daysEl.className = "grid grid-cols-7 border-b border-slate-200 bg-slate-50/80 sticky top-0 z-10";
    }

    // Sort sessions by start time for each day
    dates.forEach(dateStr => {
        const sessions = byDate.get(dateStr) || [];
        sessions.forEach(s => {
            const start = (s.startTime || "00:00").split(':').map(Number);
            s._startMins = start[0] * 60 + start[1];
        });
        sessions.sort((a, b) => a._startMins - b._startMins);
    });

    if (daysEl) daysEl.innerHTML = "";
    if (gridInner) gridInner.innerHTML = "";
    if (timeAxis) timeAxis.innerHTML = "";
    if (gridLines) gridLines.innerHTML = "";

    // ── Day Header Row & Columns ────────────────────────────────────
    const dayNames = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];
    const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    const today = new Date();

    dates.forEach(dateStr => {
        const dt = new Date(dateStr);
        const isToday = today.getFullYear() === dt.getFullYear() &&
            today.getMonth() === dt.getMonth() &&
            today.getDate() === dt.getDate();
        const dayLabel = dayNames[dt.getDay()];
        const dayNum = dt.getDate();
        const sessions = byDate.get(dateStr) || [];
        const sessCount = sessions.length;

        // Header
        if (daysEl) {
            const headCell = document.createElement("div");
            headCell.className = `p-3 flex flex-col items-center justify-center border-r border-slate-200 last:border-r-0 ${isToday ? 'bg-indigo-50/50' : ''}`;
            headCell.innerHTML = `
                <span class="text-[11px] font-bold tracking-wider uppercase ${isToday ? 'text-indigo-600' : 'text-slate-500'}">${dayLabel}</span>
                <span class="mt-1 w-8 h-8 flex items-center justify-center rounded-full text-sm font-bold ${isToday ? 'bg-indigo-600 text-white shadow-md' : 'text-slate-800'}">${dayNum}</span>
                <span class="mt-1 flex items-center gap-1 text-[10px] text-slate-400">
                    ${sessCount > 0 ? `<div class="w-1.5 h-1.5 rounded-full ${isToday ? 'bg-indigo-400' : 'bg-sp-green'}"></div>` : ''} 
                    ${sessCount} slot
                </span>
            `;
            daysEl.appendChild(headCell);
        }

        // Agenda Column (Flex Stack)
        if (gridInner) {
            const col = document.createElement("div");
            col.className = `col-span-1 h-full flex flex-col gap-3 p-1 rounded-lg ${isToday ? 'bg-indigo-50/20' : ''}`;

            if (sessions.length === 0) {
                col.innerHTML = `<div class="text-center py-6 text-xs text-slate-400 font-medium">Trống</div>`;
            }

            sessions.forEach(s => {
                const statusUpper = (s.status || "").toUpperCase();
                const isComplete = statusUpper === "COMPLETED" || statusUpper.startsWith("HOÀN THÀNH");
                const isInProg = statusUpper === "IN_PROGRESS";
                const isSkipped = statusUpper === "SKIPPED";

                const nowMins = today.getHours() * 60 + today.getMinutes();
                const todayStr = today.getFullYear() + "-" +
                    String(today.getMonth() + 1).padStart(2, "0") + "-" +
                    String(today.getDate()).padStart(2, "0");

                const sp = (s.startTime || "00:00").split(":").map(Number);
                const ep = (s.endTime || "00:00").split(":").map(Number);
                const sMins = sp[0] * 60 + sp[1];
                const eMins = ep[0] * 60 + ep[1];

                let displayStatus;
                if (isInProg) displayStatus = "in_progress";
                else if (isComplete) displayStatus = "completed";
                else if (isSkipped) displayStatus = "skipped";
                else {
                    if (dateStr < todayStr) displayStatus = "missed";
                    else if (dateStr === todayStr) {
                        if (nowMins >= sMins && nowMins < eMins) displayStatus = "active";
                        else if (nowMins >= eMins) displayStatus = "missed";
                        else displayStatus = "upcoming";
                    } else displayStatus = "upcoming";
                    if (statusUpper === "EXPIRED") displayStatus = "missed";
                }

                const PALETTE = {
                    active: { leftBorder: "border-l-indigo-500", border: "border-indigo-500", bg: "bg-indigo-50 shadow-md ring-2 ring-indigo-500/30", text: "text-indigo-900", badgeText: "Học ngay" },
                    upcoming: { leftBorder: "border-l-slate-300", border: "border-slate-300", bg: "bg-white", text: "text-slate-700", badgeText: "Sắp tới" },
                    missed: { leftBorder: "border-l-rose-400", border: "border-rose-400", bg: "bg-rose-50/50", text: "text-rose-900", badgeText: "Bỏ lỡ" },
                    in_progress: { leftBorder: "border-l-amber-500", border: "border-amber-500", bg: "bg-amber-50 shadow-md ring-2 ring-amber-500", text: "text-amber-900", badgeText: "Đang học" },
                    completed: { leftBorder: "border-l-emerald-500", border: "border-emerald-500", bg: "bg-emerald-50/50", text: "text-emerald-900", badgeText: "Hoàn thành" },
                    skipped: { leftBorder: "border-l-purple-400", border: "border-purple-400", bg: "bg-purple-50/30", text: "text-purple-900", badgeText: "Bỏ qua" },
                };
                const p = PALETTE[displayStatus] || PALETTE.upcoming;

                const card = document.createElement("div");
                card.className = `sp-session ${p.bg} ${p.leftBorder} cursor-pointer group`;

                let durationBadge = "";
                if (isComplete && s.actualHoursLogged != null) {
                    const formatTime = (mins) => {
                        const h = Math.floor(mins / 60);
                        const m = mins % 60;
                        if (h > 0 && m > 0) return `${h}h${m}m`;
                        if (h > 0) return `${h}h`;
                        return `${m}m`;
                    };
                    const actualMins = Math.round(s.actualHoursLogged * 60);
                    durationBadge = `<span class="ml-2 px-1.5 py-0.5 bg-emerald-100 text-emerald-700 text-[10px] font-bold rounded-md whitespace-nowrap">${formatTime(actualMins)}/${formatTime(s.durationMinutes)}</span>`;
                }

                card.innerHTML = `
                    <div class="sp-session-time">
                        <div class="flex items-center gap-1.5 min-w-0 flex-1">
                             <i data-lucide="clock" class="w-3.5 h-3.5 flex-shrink-0 text-slate-400"></i> 
                             <span class="truncate pr-1">${(s.startTime || "").slice(0, 5)} - ${(s.endTime || "").slice(0, 5)}</span>
                        </div>
                    </div>
                    <div class="flex items-baseline justify-between mt-0.5">
                        <div class="text-[13px] font-bold ${p.text} leading-snug line-clamp-2 flex-1">${s.taskTitle || "Task"}</div>
                        ${durationBadge}
                    </div>
                    <div class="text-[11px] font-medium text-slate-500 truncate mt-1">
                        ${s.courseName || s.taskType || "Không phân loại"}
                    </div>
                    <div class="mt-2 flex items-center justify-between w-full">
                        <div class="text-[10px] font-bold px-2 py-0.5 rounded shadow-sm border ${p.border} self-start opacity-90">${p.badgeText}</div>
                        ${isComplete ? `
                            <button class="sp-btn-reset-session p-1.5 text-slate-500 hover:text-rose-600 transition-colors bg-white/70 hover:bg-white rounded-lg border border-slate-200 shadow-sm font-bold text-lg leading-none" title="Reset ca học">
                                &#x21BB;
                            </button>
                        ` : ''}
                    </div>
                `;

                const btnReset = card.querySelector(".sp-btn-reset-session");
                if (btnReset) {
                    btnReset.onclick = async (e) => {
                        e.stopPropagation();
                        if (!await confirmDialog("Reset ca học?", "Bạn có chắc muốn chuyển ca học này về trạng thái chưa hoàn thành?")) return;
                        const ok = await apiRequest("/api/sessions/" + s.id + "/reset", { method: "PUT" });
                        if (ok) {
                            showToast("Đã reset ca học.", "success");
                            await loadTimetable();
                            await loadDashboard();
                        }
                    };
                }

                card.onclick = () => {
                    if (displayStatus === "in_progress") { resumeSession(s); }
                    else if (displayStatus === "active") { startSession(s); }
                    else if (displayStatus === "completed") { openSessionAction(s); }
                    else if (displayStatus === "missed") { showToast("Slot này đã qua — không thể bắt đầu.", "warning"); }
                    else if (displayStatus === "upcoming") { showToast("Chưa đến giờ học cho slot này.", "info"); }
                };

                col.appendChild(card);
            });
            gridInner.appendChild(col);
        }
    });

    if (window.lucide) window.lucide.createIcons();

    // Load sidebar data before finishing
    await loadAvailability();
    await loadPreferences();
} // end loadTimetable

function sessionStatusClass(status) {
    const s = (status || "").toUpperCase();
    if (s === "COMPLETED" || (s.startsWith("HOÀN THÀNH"))) return "sp-session-completed";
    if (s === "IN_PROGRESS") return "sp-session-in-progress";
    if (s === "EXPIRED") return "sp-session-expired"; // New class
    if (s === "SCHEDULED") return "sp-session-planned"; // Map SCHEDULED to planned style
    return "sp-session-planned";
}

let timerInterval = null;
let currentSession = null;

function getSessionDurationSeconds(s) {
    const minutes = typeof s?.durationMinutes === "number" ? s.durationMinutes : parseInt(String(s?.durationMinutes || ""), 10);
    if (Number.isFinite(minutes) && minutes > 0) return minutes * 60;

    const start = String(s?.startTime || "");
    const end = String(s?.endTime || "");
    const startParts = start.split(":").map(n => parseInt(n, 10));
    const endParts = end.split(":").map(n => parseInt(n, 10));
    if (startParts.length >= 2 && endParts.length >= 2 && startParts.every(Number.isFinite) && endParts.every(Number.isFinite)) {
        const startSec = startParts[0] * 3600 + startParts[1] * 60 + (startParts[2] || 0);
        const endSec = endParts[0] * 3600 + endParts[1] * 60 + (endParts[2] || 0);
        const diff = endSec - startSec;
        if (diff > 0) return diff;
    }
    return 60 * 60;
}

function getRemainingSessionSeconds(s) {
    const sessionDate = String(s?.sessionDate || "");
    const end = String(s?.endTime || "");
    const dateParts = sessionDate.split("-").map(n => parseInt(n, 10));
    const endParts = end.split(":").map(n => parseInt(n, 10));
    if (dateParts.length >= 3 && endParts.length >= 2 && dateParts.every(Number.isFinite) && endParts.every(Number.isFinite)) {
        const endDate = new Date(dateParts[0], dateParts[1] - 1, dateParts[2], endParts[0], endParts[1], endParts[2] || 0);
        const remaining = Math.floor((endDate.getTime() - Date.now()) / 1000);
        if (Number.isFinite(remaining)) return Math.max(0, remaining);
    }
    return getSessionDurationSeconds(s);
}

function getSessionEndDate(s) {
    const sessionDate = String(s?.sessionDate || "");
    const end = String(s?.endTime || "");
    const dateParts = sessionDate.split("-").map(n => parseInt(n, 10));
    const endParts = end.split(":").map(n => parseInt(n, 10));
    if (dateParts.length >= 3 && endParts.length >= 2 && dateParts.every(Number.isFinite) && endParts.every(Number.isFinite)) {
        return new Date(dateParts[0], dateParts[1] - 1, dateParts[2], endParts[0], endParts[1], endParts[2] || 0);
    }
    return null;
}

async function startSession(s) {
    if (!await confirmDialog("Bắt đầu học?", "Bạn đã sẵn sàng bắt đầu môn " + s.courseName + "?")) return;

    // Call API to start
    const res = await apiRequest("/api/sessions/" + s.id + "/start", { method: "PUT" });
    if (!res) return;

    currentSession = s;

    // Show Timer View
    const view = document.getElementById("view-session-timer");
    document.getElementById("timer-course-name").textContent = s.courseName;
    document.getElementById("timer-task-title").textContent = s.taskTitle || "Tự học";
    view.classList.remove("hidden");
    view.style.display = "flex";

    // Setup Buttons
    const btnSkip = document.getElementById("btn-timer-skip");
    const btnComplete = document.getElementById("btn-timer-complete");

    // Reset buttons logic (remove old listeners)
    const newSkip = btnSkip.cloneNode(true);
    btnSkip.parentNode.replaceChild(newSkip, btnSkip);
    const newComplete = btnComplete.cloneNode(true);
    btnComplete.parentNode.replaceChild(newComplete, btnComplete);

    newSkip.onclick = async () => {
        clearInterval(timerInterval);
        view.classList.add("hidden");
        view.style.display = "none";
        await loadTimetable();
        showToast("Đã dừng phiên học giữa chừng.", "info");
    };

    newComplete.onclick = async () => {
        const hours = parseFloat((s.durationMinutes / 60.0).toFixed(2));
        const result = await runReflectionFlow(s.id, hours);
        if (result === null) return; // Backend validation failed

        clearInterval(timerInterval);
        view.classList.add("hidden");
        view.style.display = "none";
        await loadTimetable();
        await loadDashboard();
        showToast("Đã hoàn thành session!", "success");
    };

    // Enable Complete button immediately
    newComplete.disabled = false;
    newComplete.style.opacity = "1";
    newComplete.style.cursor = "pointer";
    document.getElementById("timer-hint").textContent = "Nhấn Hoàn thành khi xong.";

    // Start Countdown
    let duration = getRemainingSessionSeconds(s);
    updateTimerDisplay(duration);

    if (timerInterval) clearInterval(timerInterval);
    timerInterval = setInterval(() => {
        duration--;
        updateTimerDisplay(duration);
        if (duration <= 0) {
            clearInterval(timerInterval);
            document.getElementById("timer-hint").textContent = "Đã hết thời gian dự kiến.";
        }
    }, 1000);
}

function updateTimerDisplay(seconds) {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    document.getElementById("timer-countdown").textContent =
        String(h).padStart(2, '0') + ":" +
        String(m).padStart(2, '0') + ":" +
        String(s).padStart(2, '0');
}

function resumeSession(s) {
    // Show Timer View
    const view = document.getElementById("view-session-timer");
    document.getElementById("timer-course-name").textContent = s.courseName;
    document.getElementById("timer-task-title").textContent = s.taskTitle || "Tự học";
    view.classList.remove("hidden");
    view.style.display = "flex";

    // Setup Buttons
    const btnSkip = document.getElementById("btn-timer-skip");
    const btnComplete = document.getElementById("btn-timer-complete");

    // Reset buttons logic (remove old listeners)
    const newSkip = btnSkip.cloneNode(true);
    btnSkip.parentNode.replaceChild(newSkip, btnSkip);
    const newComplete = btnComplete.cloneNode(true);
    btnComplete.parentNode.replaceChild(newComplete, btnComplete);

    newSkip.onclick = async () => {
        clearInterval(timerInterval);
        view.classList.add("hidden");
        view.style.display = "none";
        await loadTimetable();
        showToast("Đã dừng phiên học giữa chừng.", "info");
    };

    newComplete.onclick = async () => {
        const hours = parseFloat((s.durationMinutes / 60.0).toFixed(2));
        const result = await runReflectionFlow(s.id, hours);
        if (result === null) return;

        clearInterval(timerInterval);
        view.classList.add("hidden");
        view.style.display = "none";
        await loadTimetable();
        await loadDashboard();
        showToast("Đã hoàn thành session!", "success");
    };

    // Enable Complete button immediately
    newComplete.disabled = false;
    newComplete.style.opacity = "1";
    newComplete.style.cursor = "pointer";
    document.getElementById("timer-hint").textContent = "Nhấn Hoàn thành khi xong.";

    let duration = getRemainingSessionSeconds(s);
    updateTimerDisplay(duration);

    if (timerInterval) clearInterval(timerInterval);
    timerInterval = setInterval(() => {
        duration--;
        updateTimerDisplay(duration);
        if (duration <= 0) {
            clearInterval(timerInterval);
            document.getElementById("timer-hint").textContent = "Đã hết thời gian dự kiến.";
        }
    }, 1000);
}

async function openSessionAction(s) {
    if (!s) return;
    const status = (s.status || "").toUpperCase();

    if (status === "IN_PROGRESS") { resumeSession(s); return; }
    if (status === "COMPLETED" || status.startsWith("HOÀN THÀNH")) { await openReflectionViewer(s); return; }
    if (status === "SKIPPED") { showToast("Session này đã bị bỏ qua.", "info"); return; }

    // Time-aware check for all other statuses
    const now = new Date();
    const todayStr = now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, "0") + "-" + String(now.getDate()).padStart(2, "0");
    const nowMins = now.getHours() * 60 + now.getMinutes();
    const sp = (s.startTime || "00:00").split(":").map(Number);
    const ep = (s.endTime || "00:00").split(":").map(Number);
    const sMins = sp[0] * 60 + sp[1];
    const eMins = ep[0] * 60 + ep[1];
    const dateStr = s.sessionDate || "";

    if (status === "EXPIRED" || dateStr < todayStr || (dateStr === todayStr && nowMins >= eMins)) {
        showToast("Slot này đã qua — không thể bắt đầu.", "warning"); return;
    }
    if (dateStr > todayStr || (dateStr === todayStr && nowMins < sMins)) {
        showToast("Chưa đến giờ học cho slot này.", "info"); return;
    }
    // Active window: dateStr === todayStr && nowMins >= sMins && nowMins < eMins
    startSession(s);
}

async function generateTimetable() {
    const pref = await apiRequest("/api/settings/preference", { method: "GET", showLoading: false });
    let blockMinutes = pref && pref.blockMinutes ? parseInt(pref.blockMinutes, 10) : 60;
    if (![30, 60, 90, 120].includes(blockMinutes)) blockMinutes = 60;

    const err = await validateBeforeGenerate();
    if (err) {
        showToast(err, "error");
        return;
    }

    if (!confirm("Bạn có muốn tạo lịch cho TOÀN BỘ lộ trình (đến ngày kết thúc kế hoạch) không?\n\nChọn OK để tạo tất cả các tuần.\nChọn Cancel để chỉ tạo tuần hiện tại.")) {
        // Cancel -> Generate only current week
        await apiRequest("/api/timetable/generate?weekOffset=" + currentWeekOffset + "&blockMinutes=" + blockMinutes, {
            method: "POST"
        });
    } else {
        // OK -> Generate all weeks
        showToast("Đang tạo lịch cho toàn bộ lộ trình, vui lòng chờ...", "info");
        await apiRequest("/api/timetable/generate-all?blockMinutes=" + blockMinutes, {
            method: "POST"
        });
        showToast("Đã tạo xong lịch cho toàn bộ lộ trình!", "success");
    }
    await loadTimetable();
}

async function validateBeforeGenerate() {
    const [courses, tasks, avail] = await Promise.all([
        apiRequest("/api/courses", { method: "GET", showLoading: false }),
        apiRequest("/api/tasks", { method: "GET", showLoading: false }),
        apiRequest("/api/settings/availability", { method: "GET", showLoading: false })
    ]);
    if (!courses || courses.length === 0) return "Thêm ít nhất 1 môn học trước";
    if (!tasks || tasks.filter(t => (t.status || "").toUpperCase() === "OPEN").length === 0) return "Thêm ít nhất 1 công việc cần học (trạng thái OPEN)";
    if (!avail || avail.length === 0) return "Thiết lập giờ rảnh trước khi generate";

    const pref = await apiRequest("/api/settings/preference", { method: "GET", showLoading: false });
    if (!pref || !pref.planStartDate || !pref.planEndDate) return "Cài đặt thời gian học (start/end date) trong Settings";

    return null;
}

async function exportTimetablePdf() {
    console.log("PDF Export: Calendar Grid Flow Started");
    showToast("Đang kết xuất PDF...", "info");

    const template = document.getElementById("pdf-export-template");
    if (!template) {
        showToast("Lỗi: Không tìm thấy mẫu PDF", "error");
        return;
    }

    try {
        const data = await apiRequest("/api/timetable?weekOffset=" + currentWeekOffset, { method: "GET" });
        if (!data || !data.sessions || data.sessions.length === 0) {
            showToast("Không có lịch học để xuất.", "warning");
            return;
        }

        // 1. Header Info
        const start = data.weekStartDate || "N/A";
        const end = data.weekEndDate || "N/A";
        const labelEl = document.getElementById("pdf-week-label");
        const dateEl = document.getElementById("pdf-export-date");
        if (labelEl) labelEl.textContent = `Tuần: ${start} - ${end}`;
        if (dateEl) dateEl.textContent = new Date().toLocaleDateString('vi-VN');

        // 2. Clear and Populate Grid Columns
        const sessions = data.sessions || [];
        // Clear all 7 columns (index 0 to 6)
        for (let i = 0; i < 7; i++) {
            const col = document.getElementById(`pdf-col-${i}`);
            if (col) col.innerHTML = "";
        }

        // Grouping: Backend sends sessionDate. We need to map it to Day of Week.
        // Assuming sessionDate is YYYY-MM-DD
        sessions.forEach(s => {
            if (!s.sessionDate) return;
            const dateObj = new Date(s.sessionDate);
            let dayIndex = dateObj.getDay(); // 0 is Sunday, 1 is Monday...
            // Map 1-6 to 0-5 (Mon-Sat), and 0 to 6 (Sun) to match UI template
            let gridIndex = dayIndex === 0 ? 6 : dayIndex - 1;

            const col = document.getElementById(`pdf-col-${gridIndex}`);
            if (col) {
                const card = document.createElement("div");

                let bgColor = "bg-blue-50 border-blue-200";
                let textColor = "text-blue-800";
                let dotColor = "bg-blue-500";

                if (s.status === "COMPLETED") {
                    bgColor = "bg-emerald-50 border-emerald-200";
                    textColor = "text-emerald-800";
                    dotColor = "bg-emerald-500";
                } else if (s.status === "SKIPPED") {
                    bgColor = "bg-rose-50 border-rose-200";
                    textColor = "text-rose-800";
                    dotColor = "bg-rose-400";
                }

                card.className = `${bgColor} border rounded-lg p-2.5 shadow-sm mb-1`;
                card.innerHTML = `
                    <div class="flex items-center gap-1.5 mb-1.5">
                        <div class="w-1.5 h-1.5 rounded-full ${dotColor}"></div>
                        <span class="text-[9px] font-black uppercase tracking-wider ${textColor}">${(s.startTime || "").slice(0, 5)} - ${(s.endTime || "").slice(0, 5)}</span>
                    </div>
                    <div class="text-[11px] font-bold text-slate-800 leading-tight mb-1 truncate">${s.courseName || "N/A"}</div>
                    <div class="text-[9px] text-slate-500 leading-snug line-clamp-2">${s.taskTitle || ""}</div>
                `;
                col.appendChild(card);
            }
        });

        // 3. Convert to PDF using html2pdf
        template.classList.remove("hidden");
        const element = template.querySelector('div'); // The inner content div

        const opt = {
            margin: 0,
            filename: `SmartPlanner_Calendar_${start.replace(/\//g, '-')}.pdf`,
            image: { type: 'jpeg', quality: 0.98 },
            html2canvas: { scale: 2, useCORS: true, letterRendering: true, width: 1080 },
            jsPDF: { unit: 'mm', format: 'a4', orientation: 'landscape' }
        };

        if (window.html2pdf) {
            await html2pdf().set(opt).from(element).save();
        } else {
            console.error("html2pdf library not found");
            showToast("Lỗi: Thư viện tạo PDF chưa sẵn sàng", "error");
        }

        // 4. Cleanup
        template.classList.add("hidden");
        showToast("Đã tải xuống bản PDF thành công!", "success");
        console.log("PDF Export: Finished");
    } catch (err) {
        console.error("PDF Export Crash:", err);
        if (template) template.classList.add("hidden");
        showToast("Lỗi khi tạo PDF: " + err.message, "error");
    }
}



async function loadCourses() {
    const list = document.getElementById("courses-list");
    if (!list) return;

    // Clear existing content immediately
    while (list.firstChild) {
        list.removeChild(list.firstChild);
    }

    const data = await apiRequest("/api/courses", { method: "GET" });
    if (!Array.isArray(data)) return;

    // Double check clearing before appending
    while (list.firstChild) {
        list.removeChild(list.firstChild);
    }

    const emptyEl = document.getElementById("courses-empty");
    if (emptyEl) emptyEl.classList.toggle("is-visible", data.length === 0);

    // Use a document fragment for better performance and atomic update
    const fragment = document.createDocumentFragment();
    data.forEach(c => {
        const item = document.createElement("div");
        item.className = "bg-white border text-sm border-slate-200 rounded-xl p-3 flex justify-between items-center hover:shadow-md transition gap-4 mb-2";

        const content = document.createElement("div");
        content.className = "flex-1";

        const title = document.createElement("div");
        title.className = "font-semibold text-slate-800";
        title.textContent = c.name + " [" + c.priority + "]";

        const meta = document.createElement("div");
        meta.className = "text-xs text-slate-500 mt-1";
        meta.textContent = "Trạng thái: " + c.status;

        content.append(title, meta);

        const actions = document.createElement("div");
        actions.className = "flex gap-2 shrink-0";

        const btnEdit = document.createElement("button");
        btnEdit.className = "px-2 py-1 text-xs font-semibold text-blue-600 bg-blue-50 hover:bg-blue-100 rounded";
        btnEdit.textContent = "Sửa";
        btnEdit.onclick = () => openCourseForm(c);

        const btnDelete = document.createElement("button");
        btnDelete.className = "px-2 py-1 text-xs font-semibold text-rose-600 bg-rose-50 hover:bg-rose-100 rounded";
        btnDelete.textContent = "Xóa";
        btnDelete.onclick = () => deleteCourse(c.id);

        actions.append(btnEdit, btnDelete);

        item.append(content, actions);
        fragment.appendChild(item);
    });
    list.appendChild(fragment);
}

async function loadTaskCourseOptions(selectedCourseId) {
    const courseEl = document.getElementById("task-course-id");
    if (!courseEl) return;

    const courses = await apiRequest("/api/courses", { method: "GET", showLoading: false });
    if (!Array.isArray(courses)) return;

    courses.sort((a, b) => (a.name || "").localeCompare((b.name || ""), "vi"));

    courseEl.innerHTML = "";

    const optPlaceholder = document.createElement("option");
    optPlaceholder.value = "";
    optPlaceholder.textContent = "(Chọn môn)";
    courseEl.appendChild(optPlaceholder);

    courses.forEach(c => {
        const opt = document.createElement("option");
        opt.value = String(c.id);
        const statusSuffix = c.status && c.status !== "ACTIVE" ? " (" + c.status + ")" : "";
        opt.textContent = (c.name ? c.name : ("#" + c.id)) + statusSuffix;
        courseEl.appendChild(opt);
    });

    if (selectedCourseId !== undefined && selectedCourseId !== null && String(selectedCourseId) !== "") {
        courseEl.value = String(selectedCourseId);
    } else {
        courseEl.value = "";
    }
}

async function loadTasks() {
    const list = document.getElementById("tasks-list");
    if (!list) return;

    // Clear DOM immediately
    while (list.firstChild) {
        list.removeChild(list.firstChild);
    }

    const [data, courses] = await Promise.all([
        apiRequest("/api/tasks", { method: "GET" }),
        apiRequest("/api/courses", { method: "GET", showLoading: false })
    ]);
    if (!Array.isArray(data)) return;

    // Clear again before appending to avoid race conditions
    while (list.firstChild) {
        list.removeChild(list.firstChild);
    }

    const courseMap = new Map();
    if (Array.isArray(courses)) {
        courses.forEach(c => courseMap.set(c.id, c.name || ("#" + c.id)));
    }
    const emptyEl = document.getElementById("tasks-empty");

    // Filter
    let filtered = data;
    if (currentTaskFilter === "ALL") {
        filtered = data.filter(t => t.status !== "CANCELED" && t.status !== "INACTIVE");
    } else if (currentTaskFilter === "IN_PROGRESS") {
        filtered = data.filter(t => t.status === "OPEN" || t.status === "IN_PROGRESS");
    } else if (currentTaskFilter === "DONE") {
        filtered = data.filter(t => t.status === "DONE" || t.status === "COMPLETED");
    } else if (currentTaskFilter === "INACTIVE") {
        filtered = data.filter(t => t.status === "CANCELED" || t.status === "INACTIVE");
    }

    if (emptyEl) emptyEl.classList.toggle("is-visible", filtered.length === 0);

    const fragment = document.createDocumentFragment();
    filtered.forEach(t => {
        const item = document.createElement("div");
        item.className = "bg-white border text-sm border-slate-200 rounded-xl p-3 flex justify-between items-center hover:shadow-md transition gap-4 mb-2";

        const isDone = t.status === "DONE" || t.status === "COMPLETED";
        const isInactive = t.status === "CANCELED" || t.status === "INACTIVE";
        const isChecked = isDone || isInactive;

        // Soft-delete / check mark toggle
        const checkWrap = document.createElement("div");
        checkWrap.innerHTML = `
            <input type="checkbox" class="w-5 h-5 rounded border-slate-300 text-sp-green focus:ring-sp-green cursor-pointer" ${isChecked ? 'checked' : ''}>
        `;
        checkWrap.querySelector("input").onchange = async (e) => {
            const nextStatus = e.target.checked ? "DONE" : "OPEN";
            await apiRequest("/api/tasks/" + t.id, {
                method: "PUT",
                body: JSON.stringify({ ...t, status: nextStatus }),
                showLoading: false
            });
            await loadTasks();
        };

        const content = document.createElement("div");
        content.className = "flex-1";
        const title = document.createElement("div");
        title.className = `font-semibold text-slate-800 ${isChecked ? 'line-through text-slate-400' : ''}`;
        const courseName = t.courseName || courseMap.get(t.courseId) || "(Không rõ môn)";
        title.textContent = t.title + " [" + t.priority + "] — " + courseName;
        const meta = document.createElement("div");
        meta.className = "text-xs text-slate-500 mt-1";
        meta.textContent = "Deadline: " + (t.deadlineDate || "—") + " • Trạng thái: " + t.status;
        content.append(title, meta);

        if (!isChecked) {
            const actions = document.createElement("div");
            actions.className = "flex gap-2 shrink-0";
            actions.innerHTML = `
                <button class="px-2 py-1 text-xs font-semibold text-blue-600 bg-blue-50 hover:bg-blue-100 rounded btn-edit-task">Sửa</button>
                <button class="px-2 py-1 text-xs font-semibold text-rose-600 bg-rose-50 hover:bg-rose-100 rounded btn-delete-task">Xóa</button>
            `;
            actions.querySelector('.btn-edit-task').onclick = () => openTaskForm(t);
            actions.querySelector('.btn-delete-task').onclick = () => deleteTask(t.id);
            item.append(checkWrap, content, actions);
        } else {
            item.append(checkWrap, content);
        }
        fragment.appendChild(item);
    });
    list.appendChild(fragment);
}



function dayOfWeekLabel(d) {
    switch (d) {
        case 1: return "Thứ 2";
        case 2: return "Thứ 3";
        case 3: return "Thứ 4";
        case 4: return "Thứ 5";
        case 5: return "Thứ 6";
        case 6: return "Thứ 7";
        case 7: return "Chủ nhật";
        default: return "Thứ ?";
    }
}

async function loadAvailability() {
    const list = document.getElementById("avail-list");
    if (!list) return;
    list.innerHTML = "";
    const data = await apiRequest("/api/settings/availability", { method: "GET" });
    if (!Array.isArray(data)) return;
    data.forEach(slot => {
        const item = document.createElement("div");
        item.className = "sp-list-item";
        const left = document.createElement("div");
        const label = dayOfWeekLabel(slot.dayOfWeek) + " " + slot.startTime + " - " + slot.endTime;
        const status = slot.active ? "Đang dùng" : "Tạm tắt";
        left.textContent = label + " • " + status;
        const actions = document.createElement("div");
        actions.style.display = "flex";
        actions.style.gap = "4px";
        const btnEdit = document.createElement("button");
        btnEdit.textContent = "Sửa";
        btnEdit.className = "sp-link-button";
        btnEdit.onclick = () => openAvailForm(slot);
        const btnDelete = document.createElement("button");
        btnDelete.textContent = "Xóa";
        btnDelete.className = "sp-link-button";
        btnDelete.onclick = () => deleteAvailability(slot.id);
        actions.append(btnEdit, btnDelete);
        item.append(left, actions);
        list.appendChild(item);
    });
}

async function logout() {
    await apiRequest("/api/auth/logout", { method: "POST", showLoading: false });
    redirectToAuthLogin();
}


// ── Course Management ──────────────────────────────────────────────────

// New Availability Functions for Modal
async function loadAvailabilityModal() {
    const list = document.getElementById("avail-list-modal");
    if (!list) return;
    list.innerHTML = "";
    const data = await apiRequest("/api/settings/availability", { method: "GET" });
    if (!Array.isArray(data)) return;

    // Sort by dayOfWeek
    data.sort((a, b) => a.dayOfWeek - b.dayOfWeek);

    data.forEach(slot => {
        const item = document.createElement("div");
        item.className = "sp-list-item";
        item.style.padding = "12px";
        item.style.borderBottom = "1px solid #eee";

        const left = document.createElement("div");
        const label = dayOfWeekLabel(slot.dayOfWeek) + " • " + slot.startTime.slice(0, 5) + " - " + slot.endTime.slice(0, 5);
        const status = slot.active ? "<span style='color:green;font-size:12px;margin-left:8px'>● Đang dùng</span>" : "<span style='color:gray;font-size:12px;margin-left:8px'>○ Tạm tắt</span>";
        left.innerHTML = "<strong>" + label + "</strong>" + status;

        const actions = document.createElement("div");
        actions.style.display = "flex";
        actions.style.gap = "8px";
        const btnEdit = document.createElement("button");
        btnEdit.textContent = "Sửa";
        btnEdit.className = "sp-link-button";
        btnEdit.onclick = () => openAvailFormModal(slot);
        const btnDelete = document.createElement("button");
        btnDelete.textContent = "Xóa";
        btnDelete.className = "sp-link-button";
        btnDelete.onclick = () => deleteAvailabilityModal(slot.id);
        actions.append(btnEdit, btnDelete);

        item.append(left, actions);
        list.appendChild(item);
    });
}

function openAvailFormModal(slot) {
    const container = document.getElementById("avail-form-container");
    container.style.display = "block";

    const idEl = document.getElementById("avail-id-modal");
    const dayEl = document.getElementById("avail-day-modal");
    const startEl = document.getElementById("avail-start-modal");
    const endEl = document.getElementById("avail-end-modal");
    const activeEl = document.getElementById("avail-active-modal");

    if (slot) {
        idEl.value = slot.id;
        dayEl.value = String(slot.dayOfWeek);
        startEl.value = slot.startTime?.slice(0, 5) || "";
        endEl.value = slot.endTime?.slice(0, 5) || "";
        activeEl.value = slot.active ? "true" : "false";
    } else {
        idEl.value = "";
        dayEl.value = "1";
        startEl.value = "19:00";
        endEl.value = "21:00";
        activeEl.value = "true";
    }
}

async function handleAvailSubmitModal(e) {
    e.preventDefault();
    const id = document.getElementById("avail-id-modal");
    const day = parseInt(document.getElementById("avail-day-modal").value || "1", 10);
    const start = document.getElementById("avail-start-modal").value;
    const end = document.getElementById("avail-end-modal").value;
    const active = document.getElementById("avail-active-modal").value === "true";

    // Validate start < end
    if (start >= end) {
        showToast("Giờ bắt đầu phải nhỏ hơn giờ kết thúc", "error");
        return;
    }

    const body = {
        dayOfWeek: day,
        startTime: start,
        endTime: end,
        active: active
    };

    let path = "/api/settings/availability";
    let method = "POST";
    if (id.value) {
        path = "/api/settings/availability/" + id.value;
        method = "PUT";
    }

    const res = await apiRequest(path, { method, body: JSON.stringify(body) });
    if (res) {
        showToast("Đã lưu giờ rảnh", "success");
        document.getElementById("avail-form-container").style.display = "none";
        await loadAvailabilityModal();
    }
}

async function deleteAvailabilityModal(id) {
    if (!await confirmDialog("Xóa giờ rảnh", "Bạn có chắc muốn xóa?")) return;
    await apiRequest("/api/settings/availability/" + id, { method: "DELETE" });
    await loadAvailabilityModal();
}

function toggleCourseForm(show) {
    const card = document.getElementById("course-form-card");
    const errorEl = document.getElementById("course-form-error");
    if (!card) return;
    card.style.display = show ? "block" : "none";
    if (!show && errorEl) errorEl.textContent = "";
}

function openCourseForm(course) {
    const title = document.getElementById("course-form-title");
    const idEl = document.getElementById("course-id");
    const nameEl = document.getElementById("course-name");
    const priEl = document.getElementById("course-priority");
    const statusWrap = document.getElementById("course-status-wrapper");
    const statusEl = document.getElementById("course-status");
    if (!nameEl || !priEl) return;
    if (course) {
        title.textContent = "Sửa môn học";
        idEl.value = course.id;
        nameEl.value = course.name;
        priEl.value = course.priority || "MEDIUM";
        statusWrap.style.display = "block";
        statusEl.value = course.status || "ACTIVE";
    } else {
        title.textContent = "Thêm môn học";
        idEl.value = "";
        nameEl.value = "";
        priEl.value = "MEDIUM";
        statusWrap.style.display = "none";
    }
    toggleCourseForm(true);
}

async function handleCourseSubmit(e) {
    e.preventDefault();
    if (e.target.dataset.submitting === "true") return;
    e.target.dataset.submitting = "true";

    const btnSubmit = e.target.querySelector("button[type='submit']");
    if (btnSubmit) btnSubmit.disabled = true;

    try {
        const id = document.getElementById("course-id").value;
        const name = document.getElementById("course-name").value.trim();
        const priority = document.getElementById("course-priority").value;
        const statusWrap = document.getElementById("course-status-wrapper");
        const status = statusWrap.style.display === "block"
            ? document.getElementById("course-status").value
            : "ACTIVE";
        const errorEl = document.getElementById("course-form-error");
        errorEl.textContent = "";

        // Default empty hours/deadline
        const body = {
            name,
            priority,
            deadlineDate: null,
            totalHours: 0
        };
        let path = "/api/courses";
        let method = "POST";
        if (id) {
            path = "/api/courses/" + id;
            method = "PUT";
            body.status = status;
        }
        const res = await apiRequest(path, { method, body: JSON.stringify(body) });
        if (!res) {
            errorEl.innerHTML = renderAlert("Không lưu được môn học", "error");
            return;
        }
        toggleCourseForm(false);
        await loadCourses();
        await loadDashboard();
    } finally {
        if (btnSubmit) btnSubmit.disabled = false;
        e.target.dataset.submitting = "false";
    }
}

async function deleteCourse(id) {
    if (!await confirmDialog("Xóa môn học", "Bạn có chắc muốn xóa môn học #" + id + "?")) return;
    await apiRequest("/api/courses/" + id, { method: "DELETE" });
    await loadCourses();
    await loadDashboard();
}

function toggleTaskForm(show) {
    const card = document.getElementById("task-form-card");
    const errorEl = document.getElementById("task-form-error");
    if (!card) return;
    card.style.display = show ? "block" : "none";
    if (!show && errorEl) errorEl.textContent = "";
}

async function openTaskForm(task) {
    const title = document.getElementById("task-form-title");
    const idEl = document.getElementById("task-id");
    const courseEl = document.getElementById("task-course-id");
    const titleEl = document.getElementById("task-title");
    const typeEl = document.getElementById("task-type");
    const priEl = document.getElementById("task-priority");
    const deadlineEl = document.getElementById("task-deadline");
    const estEl = document.getElementById("task-estimated-hours");
    const remWrap = document.getElementById("task-remaining-wrapper");
    const remEl = document.getElementById("task-remaining-hours");
    const statusWrap = document.getElementById("task-status-wrapper");
    const statusEl = document.getElementById("task-status");
    const descEl = document.getElementById("task-description");
    if (!courseEl || !titleEl) return;

    await loadTaskCourseOptions(task ? task.courseId : null);
    if (task) {
        title.textContent = "Sửa task";
        idEl.value = task.id;
        courseEl.value = task.courseId;
        titleEl.value = task.title;
        typeEl.value = task.type || "OTHER";
        priEl.value = task.priority || "MEDIUM";
        deadlineEl.value = task.deadlineDate || "";
        estEl.value = task.estimatedHours ?? 0;
        remWrap.style.display = "block";
        statusWrap.style.display = "block";
        remEl.value = task.remainingHours ?? task.estimatedHours ?? 0;
        statusEl.value = task.status || "OPEN";
        descEl.value = task.description || "";
    } else {
        title.textContent = "Thêm task";
        idEl.value = "";
        courseEl.value = "";
        titleEl.value = "";
        typeEl.value = "OTHER";
        priEl.value = "MEDIUM";
        deadlineEl.value = "";
        estEl.value = "";
        remWrap.style.display = "none";
        statusWrap.style.display = "none";
        remEl.value = "";
        statusEl.value = "OPEN";
        descEl.value = "";
    }
    toggleTaskForm(true);
}

async function handleTaskSubmit(e) {
    e.preventDefault();
    if (e.target.dataset.submitting === "true") return;
    e.target.dataset.submitting = "true";

    const btnSubmit = e.target.querySelector("button[type='submit']");
    if (btnSubmit) btnSubmit.disabled = true;

    try {
        const id = document.getElementById("task-id").value;
        const courseId = parseInt(document.getElementById("task-course-id").value || "0", 10);
        const title = document.getElementById("task-title").value.trim();
        const type = document.getElementById("task-type").value;
        const priority = document.getElementById("task-priority").value;
        const deadline = document.getElementById("task-deadline").value;
        const est = parseFloat(document.getElementById("task-estimated-hours").value || "0");
        const remWrap = document.getElementById("task-remaining-wrapper");
        const rem = remWrap.style.display === "block"
            ? parseFloat(document.getElementById("task-remaining-hours").value || "0")
            : est;
        const statusWrap = document.getElementById("task-status-wrapper");
        const status = statusWrap.style.display === "block"
            ? document.getElementById("task-status").value
            : "OPEN";
        const desc = document.getElementById("task-description").value;
        const errorEl = document.getElementById("task-form-error");
        errorEl.innerHTML = "";

        if (!courseId || courseId <= 0) {
            errorEl.innerHTML = renderAlert("Vui lòng chọn môn học", "error");
            return;
        }

        // Validate deadline
        if (deadline) {
            const d = new Date(deadline);
            const now = new Date();
            now.setHours(0, 0, 0, 0);
            if (d < now) {
                errorEl.innerHTML = renderAlert("Hạn chót phải từ ngày hôm nay trở đi", "error");
                return;
            }

            // Validate if hours can fit (Feature 7 logic preview, simplistic check)
            /* 
            const daysLeft = Math.ceil((d.getTime() - new Date().getTime()) / (1000 * 3600 * 24));
            if (daysLeft > 0 && (est / daysLeft) > 24) {
                 errorEl.textContent = "Hạn chót quá gấp so với số giờ dự kiến!";
                 return;
            }
            */
        }

        const base = {
            courseId,
            title,
            description: desc,
            type,
            priority,
            deadlineDate: deadline,
            estimatedHours: est
        };
        let path = "/api/tasks";
        let method = "POST";
        let body = base;
        if (id) {
            path = "/api/tasks/" + id;
            method = "PUT";
            body = {
                ...base,
                remainingHours: rem,
                status
            };
        }
        const res = await apiRequest(path, { method, body: JSON.stringify(body) });
        if (!res) {
            errorEl.innerHTML = renderAlert("Không lưu được task", "error");
            return;
        }
        toggleTaskForm(false);
        await loadTasks();
        await loadDashboard();
    } finally {
        if (btnSubmit) btnSubmit.disabled = false;
        e.target.dataset.submitting = "false";
    }
}

async function deleteTask(id) {
    if (!await confirmDialog("Xóa công việc", "Bạn có chắc muốn xóa task #" + id + "?")) return;
    await apiRequest("/api/tasks/" + id, { method: "DELETE" });
    await loadTasks();
    await loadDashboard();
}

function toggleAvailForm(show) {
    const card = document.getElementById("avail-form-card");
    if (!card) return;
    card.style.display = show ? "block" : "none";
}

function openAvailForm(slot) {
    const title = document.getElementById("avail-form-title");
    const idEl = document.getElementById("avail-id");
    const dayEl = document.getElementById("avail-day");
    const startEl = document.getElementById("avail-start");
    const endEl = document.getElementById("avail-end");
    const activeWrap = document.getElementById("avail-active-wrapper");
    const activeEl = document.getElementById("avail-active");
    if (!dayEl || !startEl || !endEl) return;
    if (slot) {
        title.textContent = "Sửa giờ rảnh";
        idEl.value = slot.id;
        dayEl.value = String(slot.dayOfWeek);
        startEl.value = slot.startTime?.slice(0, 5) || "";
        endEl.value = slot.endTime?.slice(0, 5) || "";
        activeWrap.style.display = "block";
        activeEl.value = slot.active ? "true" : "false";
    } else {
        title.textContent = "Thêm giờ rảnh";
        idEl.value = "";
        dayEl.value = "1";
        startEl.value = "19:00";
        endEl.value = "21:00";
        activeWrap.style.display = "none";
        activeEl.value = "true";
    }
    const err = document.getElementById("avail-form-error");
    if (err) err.textContent = "";
    toggleAvailForm(true);
}

async function handleAvailSubmit(e) {
    e.preventDefault();
    const id = document.getElementById("avail-id").value;
    const day = parseInt(document.getElementById("avail-day").value || "1", 10);
    const start = document.getElementById("avail-start").value;
    const end = document.getElementById("avail-end").value;
    const activeWrap = document.getElementById("avail-active-wrapper");
    const activeVal = activeWrap.style.display === "block"
        ? document.getElementById("avail-active").value
        : "true";
    const active = activeVal === "true";
    const errorEl = document.getElementById("avail-form-error");
    if (errorEl) errorEl.textContent = "";
    const bodyBase = {
        dayOfWeek: day,
        startTime: start,
        endTime: end
    };
    let path = "/api/settings/availability";
    let method = "POST";
    let body = bodyBase;
    if (id) {
        path = "/api/settings/availability/" + id;
        method = "PUT";
        body = {
            ...bodyBase,
            active
        };
    }
    const res = await apiRequest(path, { method, body: JSON.stringify(body) });
    if (!res) {
        if (errorEl) errorEl.innerHTML = renderAlert("Không lưu được giờ rảnh", "error");
        return;
    }
    toggleAvailForm(false);
    await loadAvailability();
}

async function deleteAvailability(id) {
    if (!await confirmDialog("Xóa giờ rảnh", "Bạn có chắc muốn xóa giờ rảnh #" + id + "?")) return;
    await apiRequest("/api/settings/availability/" + id, { method: "DELETE" });
    await loadAvailability();
}

async function loadPreferences() {
    const data = await apiRequest("/api/settings/preference", { method: "GET" });
    if (!data) return;
    const startEl = document.getElementById("pref-start-date");
    const endEl = document.getElementById("pref-end-date");
    const blockEl = document.getElementById("pref-block-minutes");
    if (startEl) startEl.value = data.planStartDate || "";
    if (endEl) endEl.value = data.planEndDate || "";
    if (blockEl) blockEl.value = String(data.blockMinutes || 60);


    const allowedDays = (data.allowedDays || "").split(",").filter(Boolean);
    const allDays = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
    const daysOff = allDays.filter(d => !allowedDays.includes(d));
    document.querySelectorAll("input[name='pref-days']").forEach(cb => {
        cb.checked = daysOff.includes(cb.value);
    });
}

async function savePreferences() {
    const startEl = document.getElementById("pref-start-date");
    const endEl = document.getElementById("pref-end-date");
    const blockEl = document.getElementById("pref-block-minutes");


    if (!startEl) return;

    const start = startEl.value || null;
    const end = endEl ? endEl.value : null;
    const blockMinutes = blockEl ? (parseInt(blockEl.value, 10) || 60) : 60;


    const allDays = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
    const daysOff = [];
    document.querySelectorAll("input[name='pref-days']:checked").forEach(cb => {
        daysOff.push(cb.value);
    });
    const allowedDays = allDays.filter(d => !daysOff.includes(d));
    if (allowedDays.length === 0) {
        showToast("Vui lòng chừa ít nhất 1 ngày học.", "error");
        return;
    }

    if (!start) {
        showToast("Vui lòng chọn ngày bắt đầu", "error");
        return;
    }

    // Validate dates
    const startDate = new Date(start);
    const now = new Date();
    now.setHours(0, 0, 0, 0);

    if (startDate < now) {
        showToast("Ngày bắt đầu phải từ hôm nay trở đi", "error");
        return;
    }

    if (end) {
        const endDate = new Date(end);
        if (endDate < startDate) {
            showToast("Ngày kết thúc phải sau ngày bắt đầu", "error");
            return;
        }
    }

    const body = {
        planStartDate: start,
        planEndDate: end,
        allowedDays: allowedDays.join(","),
        blockMinutes: blockMinutes
    };

    const res = await apiRequest("/api/settings/preference", {
        method: "PUT",
        body: JSON.stringify(body)
    });

    if (res) {
        showToast("Đã lưu cài đặt lộ trình!", "success");
    }
}

document.addEventListener("DOMContentLoaded", async () => {
    console.log("App initializing...");
    // Prevent double initialization
    if (window.appInitialized) return;
    window.appInitialized = true;

    const sidebar = document.getElementById("app-sidebar");

    // ── Global Event Delegation ─────────────────────────────────────────────
    document.addEventListener("click", async (e) => {
        const target = e.target;
        if (!(target instanceof HTMLElement)) return;

        // Account Pill Toggle
        const pill = target.closest("#sidebar-account-pill");
        if (pill) {
            e.stopPropagation();
            const popover = document.getElementById("account-popover");
            if (popover) {
                const isHidden = popover.classList.toggle("hidden");
                popover.classList.toggle("show", !isHidden);
            }
            return;
        }

        // Close Popover on outside click
        const popover = document.getElementById("account-popover");
        if (popover && !popover.classList.contains("hidden") && !target.closest("#account-popover")) {
            popover.classList.add("hidden");
            popover.classList.remove("show");
        }

        const btn = target.closest("button, a[data-action], .sp-sidebar-item");
        if (!btn) return;

        const id = btn.id;
        const view = btn.getAttribute("data-view");

        // View Navigation
        if (view) {
            document.querySelectorAll(".sp-sidebar-item").forEach(si => si.classList.remove("active"));
            btn.classList.add("active");
            await window.appNavigate(view);
            return;
        }

        // Core Sidebar Toggle
        if (id === "btn-toggle-sidebar" && sidebar) {
            sidebar.classList.toggle("sidebar-collapsed");
            if (window.lucide) window.lucide.createIcons();
            return;
        }

        // Command Button Actions
        if (id === "btn-logout") { logout(); return; }
        if (id === "btn-course-new") { openCourseForm(null); return; }
        if (id === "btn-task-new") { openTaskForm(null); return; }
        if (id === "course-form-cancel") { toggleCourseForm(false); return; }
        if (id === "task-form-cancel") { toggleTaskForm(false); return; }
        if (id === "btn-generate-timetable") { generateTimetable(); return; }
        if (id === "btn-export-pdf") { exportTimetablePdf(); return; }

        if (id === "btn-week-prev") { navWeek(-1); return; }
        if (id === "btn-week-next") { navWeek(1); return; }
        if (id === "btn-settings-reset") {
            if (!confirm("Bạn có muốn reset cài đặt về mặc định?")) return;
            document.getElementById("pref-start-date").value = "";
            document.getElementById("pref-end-date").value = "";
            const blockEl = document.getElementById("pref-block-minutes");
            if (blockEl) blockEl.value = "60";

            document.querySelectorAll("input[name='pref-days']").forEach(cb => {
                cb.checked = false;
            });
            return;
        }
        if (id === "btn-avail-new") { openAvailForm(null); return; }
        if (id === "avail-form-cancel") { toggleAvailForm(false); return; }
        if (id === "btn-settings-save") { savePreferences(); return; }
        if (id === "btn-avail-add-new") { openAvailFormModal(null); return; }
        if (id === "btn-avail-cancel-modal") {
            const container = document.getElementById("avail-form-container");
            if (container) container.style.display = "none";
            return;
        }
        if (id === "btn-prompt-close" || id === "btn-prompt-dismiss") {
            document.getElementById("availability-prompt")?.classList.add("translate-y-full");
            if (id === "btn-prompt-close") {
                appNavigate("settings");
            }
            return;
        }
        if (id === "btn-settings-close" || id === "btn-open-settings") {
            await appNavigate(id === "btn-settings-close" ? "dashboard" : "settings");
            return;
        }
        if (id === "btn-week-prev") { currentWeekOffset -= 1; await loadTimetable(); return; }
        if (id === "btn-week-next") { currentWeekOffset += 1; await loadTimetable(); return; }
        if (id === "btn-view-day") { window.currentCalendarMode = "day"; updateViewToggle("day"); await loadTimetable(); return; }
        if (id === "btn-view-week") { window.currentCalendarMode = "week"; updateViewToggle("week"); await loadTimetable(); return; }
        if (id === "btn-view-month") { window.currentCalendarMode = "month"; updateViewToggle("month"); await loadTimetable(); return; }
    });

    // Form submits
    document.addEventListener("submit", async (e) => {
        const form = e.target;
        if (form.id === "course-form") { e.preventDefault(); await handleCourseSubmit(e); }
        else if (form.id === "task-form") { e.preventDefault(); await handleTaskSubmit(e); }
        else if (form.id === "avail-form") { e.preventDefault(); await handleAvailSubmit(e); }
        else if (form.id === "avail-form-modal") { e.preventDefault(); await handleAvailSubmitModal(e); }
    });

    // Tasks Tabs
    document.querySelectorAll(".sp-task-tab").forEach(tab => {
        tab.onclick = () => {
            document.querySelectorAll(".sp-task-tab").forEach(t => {
                t.classList.remove("active", "bg-emerald-600", "shadow-sm", "text-white", "font-semibold");
                t.classList.add("text-slate-500", "font-medium", "hover:text-slate-700", "hover:bg-slate-200/50");
            });
            tab.classList.add("active", "bg-emerald-600", "shadow-sm", "text-white", "font-semibold");
            tab.classList.remove("text-slate-500", "font-medium", "hover:text-slate-700", "hover:bg-slate-200/50");
            currentTaskFilter = tab.getAttribute("data-status");
            loadTasks();
        };
    });

    // Empty state actions
    document.querySelectorAll("[data-empty-action]").forEach(btn => {
        btn.onclick = async () => {
            const action = btn.getAttribute("data-empty-action");
            if (action === "tasks") {
                await appNavigate("tasks");
                openTaskForm(null);
            } else if (action === "courses") {
                await appNavigate("courses");
                openCourseForm(null);
            }
        };
    });

    // Init Lucide icons
    if (window.lucide) window.lucide.createIcons();

    // Populate user profile info in sidebar/popover
    await loadUserProfile();

    if (window.lucide) window.lucide.createIcons();

    if (await checkSession()) {
        const dashboardBtn = document.querySelector('.sp-sidebar-item[data-view="dashboard"]');
        if (dashboardBtn) dashboardBtn.classList.add("active");
        await appNavigate("dashboard");
    }
});

async function loadUserProfile() {
    try {
        const user = await apiRequest("/api/auth/me", { showLoading: false });
        if (!user || user.status === 401) return;
        window.currentUser = user;

        const avatar = document.getElementById("user-avatar");
        const nameEl = document.getElementById("user-name");
        const emailEl = document.getElementById("user-email");

        if (nameEl) nameEl.textContent = user.fullName || "User";
        if (emailEl) emailEl.textContent = user.email || "";
        if (avatar) {
            const parts = (user.fullName || "User").split(" ").filter(Boolean);
            avatar.textContent = parts.length > 1
                ? (parts[0][0] + (parts[parts.length - 1][0] || "")).toUpperCase()
                : (parts[0][0] || "?").toUpperCase();
        }

        const setVal = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = val || ""; };
        setVal("profile-email", user.email);
        setVal("profile-name", user.fullName);

        const createdEl = document.getElementById("profile-created");
        if (createdEl && user.createdAt) {
            const d = Array.isArray(user.createdAt)
                ? new Date(user.createdAt[0], user.createdAt[1] - 1, user.createdAt[2])
                : new Date(user.createdAt);
            createdEl.textContent = isNaN(d.getTime()) ? "N/A" : d.toLocaleDateString('vi-VN');
        }

        const st = document.getElementById("profile-status");
        if (st) {
            st.textContent = user.status || "ACTIVE";
            st.className = (user.status || "ACTIVE") === "ACTIVE"
                ? "px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 text-[10px] font-bold"
                : "px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 text-[10px] font-bold";
        }
    } catch (e) {
        console.error("Profile load err:", e);
    }
}




function updateViewToggle(activeMode) {
    const modes = { "day": "btn-view-day", "week": "btn-view-week" };
    Object.entries(modes).forEach(([mode, btnId]) => {
        const b = document.getElementById(btnId);
        if (!b) return;
        if (mode === activeMode) {
            b.classList.add("bg-white", "text-slate-800", "shadow-sm");
            b.classList.remove("text-slate-500");
        } else {
            b.classList.remove("bg-white", "text-slate-800", "shadow-sm");
            b.classList.add("text-slate-500");
        }
    });
}



