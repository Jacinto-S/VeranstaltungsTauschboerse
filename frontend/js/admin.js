import { Grid, html } from "gridjs";
import Chart from 'chart.js/auto';
import 'chartjs-adapter-date-fns';
import bootstrap from 'bootstrap/dist/js/bootstrap.bundle.min.js';

import "gridjs/dist/theme/mermaid.css";

const API_BASE = '/api/admin';
const pageSize = 25;

let charts = {};
let usersGrid = null;
let auditGrid = null;
let timelineDefaultInitialized = false;

let auditStreamInterval = null;


function buildUsersUrl(page = 0, size = pageSize) {
    const semesterFilter = document.getElementById('usersSemesterSelect')?.value || '';
    const q = document.getElementById('userSearch')?.value || '';
    let url = `${API_BASE}/users?page=${page}&size=${size}`;
    if (semesterFilter) url += `&semester=${encodeURIComponent(semesterFilter)}`;
    if (q) url += `&q=${encodeURIComponent(q)}`;
    return url;
}

function buildAuditUrl(page = 0, size = pageSize) {
    const hsMail = document.getElementById('auditUserId')?.value || '';
    const eventType = document.getElementById('auditEventType')?.value || '';
    const semester = document.getElementById('auditSemester')?.value || '';
    const includeDebug = document.getElementById('auditIncludeDebug')?.checked || false;
    let url = `${API_BASE}/auditlogs?page=${page}&size=${size}`;
    if (hsMail) url += `&hsMail=${encodeURIComponent(hsMail)}`;
    if (eventType) url += `&eventType=${encodeURIComponent(eventType)}`;
    if (semester) url += `&semester=${encodeURIComponent(semester)}`;
    if (includeDebug) url += `&includeDebug=true`;
    return url;
}

function preloadTabContent(tabName) {
    if (tabName === 'overview') loadOverview();
    else if (tabName === 'users') loadUsers();
    else if (tabName === 'statistics') loadSemesterStats();
    else if (tabName === 'auditlog') loadAuditLogs();
    else if (tabName === 'flags') loadFlagStatistics();
    else if (tabName === 'feedback') loadFeedbacks();
    else if (tabName === 'evaluation') loadEvaluation();
    else if (tabName === 'settings') loadSettings();
    else if (tabName === 'studiengaenge') loadStudiengaenge();
}

function switchTabUI(tabName) {
    document.querySelectorAll('.sidebar a, .offcanvas-body a').forEach(l => l.classList.remove('active'));
    document.querySelectorAll(`[data-tab="${tabName}"]`).forEach(l => l.classList.add('active'));

    document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));
    document.getElementById(tabName).classList.add('active');

    history.pushState({ tab: tabName }, '', `?tab=${tabName}`);
}

function closeOffcanvasIfOpen() {
    const offcanvasEl = document.getElementById('sidebarOffcanvas');
    if (offcanvasEl) {
        const offcanvas = bootstrap.Offcanvas.getInstance(offcanvasEl);
        if (offcanvas) {
            offcanvas.hide();
        }
    }
}

document.querySelectorAll('.sidebar a, .offcanvas-body a').forEach(link => {

    if (link.href.indexOf('#') == -1) return;

    link.addEventListener('pointerdown', function (e) {
        e.preventDefault();
        const tabName = this.dataset.tab;
        preloadTabContent(tabName);
    });

    link.addEventListener('pointerup', function (e) {
        e.preventDefault();
        const tabName = this.dataset.tab;
        switchTabUI(tabName);
        closeOffcanvasIfOpen();
    });

    link.addEventListener('click', function (e) {
        e.preventDefault();
    });
});

window.addEventListener('popstate', (e) => {
    if (e.state && e.state.tab) {
        switchTabUI(e.state.tab);
        preloadTabContent(e.state.tab);
    }
});

window.addEventListener('DOMContentLoaded', async () => {
    await initializeSemesters();
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get('tab');

    if (tabParam && document.getElementById(tabParam)) {
        switchTabUI(tabParam);
        preloadTabContent(tabParam);
    } else {
        loadOverview();
    }
});

async function initializeSemesters() {
    try {
        const response = await fetch(`${API_BASE}/statistics/overview`);
        const data = await response.json();
        updateSemesterSelects(data.usersBySemester);
    } catch (error) {
        console.error('Error initializing semesters:', error);
    }
}
async function loadOverview() {
    try {
        const response = await fetch(`${API_BASE}/statistics/overview`);
        const data = await response.json();

        const statsContainer = document.getElementById('statsContainer');
        statsContainer.innerHTML = `
            <div class="col-md-3">
                <div class="stat-card" style="border-left-color: #3498db;">
                    <h5>Gesamtbenutzer</h5>
                    <div class="number">${data.totalUsers}</div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="stat-card" style="border-left-color: #2ecc71;">
                    <h5>Mit Passkeys</h5>
                    <div class="number">${data.usersWithPasskeys}</div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="stat-card" style="border-left-color: #f39c12;">
                    <h5>Kalender hochgeladen</h5>
                    <div class="number">${data.usersWithCalendar}</div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="stat-card" style="border-left-color: #e74c3c;">
                    <h5>Erfolgreich vermittelt</h5>
                    <div class="number">${data.successfulMatches}</div>
                </div>
            </div>
        `;
        updateSemesterSelects(data.usersBySemester);
        createUsersBySemesterChart(data.usersBySemester);
    } catch (error) {
        console.error('Error loading overview:', error);
    }
}

function updateSemesterSelects(semesters) {
    const semesterSelect = document.getElementById('semesterSelect');
    const timelineSemesterSelect = document.getElementById('timelineSemesterSelect');
    const auditSemesterSelect = document.getElementById('auditSemester');
    const usersSemesterSelect = document.getElementById('usersSemesterSelect');
    const flagsSemesterSelect = document.getElementById('flagsSemesterSelect');
    const evalSemesterSelect = document.getElementById('evalSemesterSelect');

    const sortedSemesters = [...semesters].sort((a, b) => {
        const semA = a[0];
        const semB = b[0];
        return semB.localeCompare(semA);
    });

    let optionsAll = '<option value="">-- Alle --</option>';
    let optionsStats = '<option value="">-- Semester auswählen --</option>';
    let optionsFlags = '<option value="">-- Alle Semester --</option>';
    let optionsEval = '<option value="">Semester auswählen...</option>';

    sortedSemesters.forEach(([semester]) => {
        optionsAll += `<option value="${semester}">${semester}</option>`;
        optionsStats += `<option value="${semester}">${semester}</option>`;
        optionsFlags += `<option value="${semester}">${semester}</option>`;
        optionsEval += `<option value="${semester}">${semester}</option>`;
    });

    if (auditSemesterSelect) auditSemesterSelect.innerHTML = optionsAll;
    if (usersSemesterSelect) usersSemesterSelect.innerHTML = optionsAll;
    if (semesterSelect) semesterSelect.innerHTML = optionsStats;
    if (timelineSemesterSelect) timelineSemesterSelect.innerHTML = optionsStats;
    if (flagsSemesterSelect) flagsSemesterSelect.innerHTML = optionsFlags;
    if (evalSemesterSelect) evalSemesterSelect.innerHTML = optionsEval;

    if (sortedSemesters.length > 0) {
        const newestSemester = sortedSemesters[0][0];
        if (semesterSelect) semesterSelect.value = newestSemester;
        if (timelineSemesterSelect) timelineSemesterSelect.value = newestSemester;
        if (evalSemesterSelect) {
            evalSemesterSelect.value = newestSemester;
            evalSemesterSelect.dataset.newestSemester = newestSemester;
        }
    }
}

function createUsersBySemesterChart(data) {
    const canvas = document.getElementById('usersBySemesterChart');
    if (!canvas) return;

    const labels = data.map(item => item[0]);
    const values = data.map(item => item[1]);

    if (charts.usersBySemester) {
        charts.usersBySemester.reset();
        charts.usersBySemester.update("show");

        return;
    }
    charts.usersBySemester = new Chart(canvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'Benutzer pro Semester',
                data: values,
                backgroundColor: '#3498db',
                borderColor: '#2980b9',
                borderWidth: 1
            }]
        },
        options: {
            maintainAspectRatio: false,
            responsive: true,
            animation: {
                duration: 500,
            },
            plugins: {
                legend: {
                    display: true
                }
            },
            scales: {
                y: {
                    beginAtZero: true
                }
            },

        }
    });

}


async function loadUsers() {
    try {
        const container = document.getElementById('usersGrid');

        if (usersGrid) {
            usersGrid.updateConfig({
                server: {
                    url: buildUsersUrl(0, pageSize),
                    then: data => data.content.map(user => [
                        (user.hsMail ? String(user.hsMail ? user.hsMail.split("@")[0] : "").substring(0, 30) + (String(user.hsMail ? user.hsMail.split("@")[0] : "").length > 30 ? '…' : '') : '-'),
                        (user.studiengangShortCode ? String(user.studiengangShortCode) : '-'),
                        user.isAdmin ? '✓' : '-',
                        user.isBanned ? '✓' : '-',
                        html(`
                                                        <div class="d-flex gap-1 flex-wrap">
                                                            <button class="btn btn-sm btn-info btn-sm-compact" data-action="metrics" data-user="${user.id}">Metriken</button>
                                                            <button class="btn btn-sm btn-secondary btn-sm-compact" data-action="impersonate" data-user="${user.id}">Übernehmen</button>
                                                            ${user.isBanned ?
                                `<button class=\"btn btn-sm btn-success btn-sm-compact\" data-action=\"unban\" data-user=\"${user.id}\">Entsperren</button>` :
                                `<button class=\"btn btn-sm btn-danger btn-sm-compact\" data-action=\"ban\" data-user=\"${user.id}\">Sperren</button>`}
                                                        </div>
                                                `)
                    ]),
                    total: data => data.totalElements
                }
            }).forceRender();
            return;
        }

        const truncate = (v, n = 60) => {
            if (v == null) return '-';
            const s = String(v);
            return s.length > n ? s.substring(0, n) + '…' : s;
        };

        usersGrid = new Grid({
            columns: [
                { name: 'Email (HS)', width: '140px' },
                { name: 'SG', width: '80px', sort: false },
                { name: 'Admin', width: '80px', sort: false },
                { name: 'Geblockt', width: '80px', sort: false },
                { name: 'Aktionen', width: '160px', sort: false }
            ],
            server: {
                url: buildUsersUrl(0, pageSize),
                then: data => data.content.map(user => [
                    truncate(user.hsMail ? user.hsMail.split("@")[0] : "", 30),
                    (user.studiengangShortCode ? String(user.studiengangShortCode) : '-'),
                    user.isAdmin ? '✓' : '-',
                    user.isBanned ? '✓' : '-',
                    html(`
                                                <div class="d-flex gap-1 flex-wrap">
                                                    <button class="btn btn-sm btn-info btn-sm-compact" data-action="metrics" data-user="${user.id}">Metriken</button>
                                                    <button class="btn btn-sm btn-secondary btn-sm-compact" data-action="impersonate" data-user="${user.id}">Übernehmen</button>
                                                    ${user.isBanned ?
                            `<button class="btn btn-sm btn-success btn-sm-compact" data-action="unban" data-user="${user.id}">Entsperren</button>` :
                            `<button class="btn btn-sm btn-danger btn-sm-compact" data-action="ban" data-user="${user.id}">Sperren</button>`}
                                                </div>
                                        `)
                ]),
                total: data => data.totalElements
            },
            search: false,
            pagination: {
                enabled: true,
                limit: pageSize,
                server: {
                    url: (prev, page, limit) => {
                        try {
                            const u = new URL(prev, window.location.origin);
                            u.searchParams.set('page', page);
                            u.searchParams.set('size', limit || pageSize);
                            return u.toString();
                        } catch (_) {
                            return `${prev}${prev.includes('?') ? '&' : '?'}page=${page}&size=${limit || pageSize}`;
                        }
                    }
                }
            }
        }).render(container);

        if (!container.dataset.listenerAttached) {
            container.addEventListener('click', (ev) => {
                const btn = ev.target.closest('button[data-action]');
                if (!btn) return;
                const action = btn.getAttribute('data-action');
                const userId = btn.getAttribute('data-user');
                if (action === 'metrics') viewUserMetrics(userId);
                else if (action === 'ban') showBanDialog(userId);
                else if (action === 'unban') unbanUser(userId);
                else if (action === 'impersonate') impersonateUser(userId);
            });
            container.dataset.listenerAttached = 'true';
        }
    } catch (error) {
        console.error('Error loading users:', error);
    }
}

function impersonateUser(userId) {
    if (!confirm('Möchten Sie sich als dieser Benutzer anmelden?')) return;
    fetch(`/api/admin/users/${userId}/impersonate`).then(response => {
        if (response.ok) {
            window.location.href = '/';
        } else {
            alert('Fehler beim Übernehmen des Benutzers');
        }
    }).catch(error => {
        console.error('Error impersonating user:', error);
        alert('Fehler beim Übernehmen des Benutzers');
    });

}

async function viewUserMetrics(userId) {
    try {
        const response = await fetch(`${API_BASE}/users/${userId}/metrics`);
        const metrics = await response.json();
        const flags = [
            { k: 'Passkeys', v: metrics.usesPasskeys },
            { k: 'Kalender hochgeladen', v: metrics.hasUploadedCalendar },
            { k: 'Angebote erstellt', v: metrics.hasCreatedOffer },
            { k: 'Erfolgreich vermittelt', v: metrics.wasSuccessfullyMatched }
        ];

        const counts = [
            { k: 'Logins', v: metrics.totalLogins },
            { k: 'Login-Versuche', v: metrics.totalLoginAttempts },
            { k: 'Kalender-Uploads', v: metrics.totalCalendarUploads },
            { k: 'Angebote erstellt', v: metrics.totalOffersCreated },
            { k: 'Angebote akzeptiert', v: metrics.totalOffersAccepted },
            { k: 'Vermittlungen', v: metrics.totalMatches }
        ];

        const firsts = [
            { k: 'Erste Passkey-Nutzung', v: metrics.firstPasskeyUse },
            { k: 'Erster Kalender-Upload', v: metrics.firstCalendarUpload },
            { k: 'Erstes Angebot', v: metrics.firstOfferCreated },
            { k: 'Erste Vermittlung', v: metrics.firstSuccessfulMatch }
        ];

        const lasts = [
            { k: 'Letzter Login', v: metrics.lastLogin },
            { k: 'Letzte Aktivität', v: metrics.lastActivity },
            { k: 'Registrierungs-Semester', v: metrics.registrationSemester },
            { k: 'Erstellt', v: metrics.createdAt },
            { k: 'Geändert', v: metrics.updatedAt }
        ];

        document.getElementById('metricsUserId').textContent = metrics.user ? metrics.user.hsMail : userId;

        const flagsList = document.getElementById('metricsFlags');
        flagsList.innerHTML = '';
        flags.forEach(f => {
            const li = document.createElement('li');
            li.textContent = `${f.v ? '✓' : '✗'} ${f.k}`;
            flagsList.appendChild(li);
        });

        const countsList = document.getElementById('metricsCounts');
        countsList.innerHTML = '';
        counts.forEach(c => {
            const li = document.createElement('li');
            li.textContent = `${c.k}: ${c.v != null ? c.v : 0}`;
            countsList.appendChild(li);
        });

        const firstsList = document.getElementById('metricsFirsts');
        firstsList.innerHTML = '';
        firsts.forEach(f => {
            const li = document.createElement('li');
            li.textContent = `${f.k}: ${f.v ? new Date(f.v).toLocaleString('de-DE') : '—'}`;
            firstsList.appendChild(li);
        });

        const lastsList = document.getElementById('metricsLasts');
        lastsList.innerHTML = '';
        lasts.forEach(l => {
            const li = document.createElement('li');
            li.textContent = `${l.k}: ${l.v ? (typeof l.v === 'string' ? l.v : new Date(l.v).toLocaleString('de-DE')) : '—'}`;
            lastsList.appendChild(li);
        });

        const modalEl = document.getElementById('userMetricsModal');
        const modal = new bootstrap.Modal(modalEl, {});
        modal.show();
    } catch (error) {
        console.error('Error loading user metrics:', error);
        const modalEl = document.getElementById('userMetricsModal');
        const modal = new bootstrap.Modal(modalEl, {});
        document.getElementById('metricsContent').innerHTML = `<div class="alert alert-danger">Fehler beim Laden der Metriken</div>`;
        modal.show();
    }
}

function showBanDialog(userId) {
    const reason = prompt('Grund für Sperrung:');
    if (reason !== null) {
        banUser(userId, reason);
    }
}

async function banUser(userId, reason) {
    try {
        const response = await fetch(`${API_BASE}/users/${userId}/ban`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reason: reason })
        });

        if (response.ok) {
            alert('Benutzer erfolgreich gesperrt');
            loadUsers();
        } else {
            alert('Fehler beim Sperren des Benutzers');
        }
    } catch (error) {
        console.error('Error banning user:', error);
    }
}

async function unbanUser(userId) {
    if (confirm('Benutzer wirklich entsperren?')) {
        try {
            const response = await fetch(`${API_BASE}/users/${userId}/unban`, {
                method: 'POST'
            });

            if (response.ok) {
                alert('Benutzer erfolgreich entsperrt');
                loadUsers();
            } else {
                alert('Fehler beim Entsperren des Benutzers');
            }
        } catch (error) {
            console.error('Error unbanning user:', error);
        }
    }
}

async function loadAuditLogs() {
    try {
        const container = document.getElementById('auditLogsGrid');

        if (auditGrid) {
            auditGrid.updateConfig({
                server: {
                    url: buildAuditUrl(0, pageSize),
                    then: data => data.content.map(log => [
                        new Date(log.timestamp).toLocaleString('de-DE'),
                        (log.hsMail ? String(log.hsMail.split("@")[0]).substring(0, 30) + (String(log.hsMail.split("@")[0]).length > 30 ? '…' : '') : '-'),
                        (log.eventType ? String(log.eventType).substring(0, 30) + (String(log.eventType).length > 30 ? '…' : '') : '-'),
                        (log.eventDetails ? String(log.eventDetails).substring(0, 120) + (String(log.eventDetails).length > 120 ? '…' : '') : '-')
                    ]),
                    total: data => data.totalElements
                }
            }).forceRender();
            return;
        }

        const truncate = (v, n = 60) => {
            if (v == null) return '-';
            const s = String(v);
            return s.length > n ? s.substring(0, n) + '…' : s;
        };

        auditGrid = new Grid({
            columns: [
                { name: 'Timestamp', width: '120px', sort: false },
                { name: 'HS-Mail', width: '100px' },
                { name: 'Event', width: '120px' },
                { name: 'Details', width: '400px', sort: false }
            ],
            server: {
                url: buildAuditUrl(0, pageSize),
                then: data => data.content.map(log => [
                    new Date(log.timestamp).toLocaleString('de-DE'),
                    truncate(log.hsMail ? log.hsMail.split("@")[0] : "", 30),
                    truncate(log.eventType, 30),
                    truncate(log.eventDetails, 120)
                ]),
                total: data => data.totalElements
            },
            search: false,
            pagination: {
                enabled: true,
                limit: pageSize,
                server: {
                    url: (prev, page, limit) => {
                        try {
                            const u = new URL(prev, window.location.origin);
                            u.searchParams.set('page', page);
                            u.searchParams.set('size', limit || pageSize);
                            return u.toString();
                        } catch (_) {
                            return `${prev}${prev.includes('?') ? '&' : '?'}page=${page}&size=${limit || pageSize}`;
                        }
                    }
                }
            }
        }).render(container);
    } catch (error) {
        console.error('Error loading audit logs:', error);
    }
}

async function loadAuditCategories(includeDebug = false) {
    try {
        const resp = await fetch(`${API_BASE}/auditlogs/categories?includeDebug=${includeDebug}`);
        const cats = await resp.json();
        const sel = document.getElementById('auditEventType');
        if (!sel) return;
        let opts = '<option value="">-- Alle --</option>';
        cats.forEach(c => {
            opts += `<option value="${c.name}">${c.displayName}</option>`;
        });
        sel.innerHTML = opts;
    } catch (err) {
        console.error('Failed to load audit categories', err);
    }
}

function stopAuditStream() {
    if (auditStreamInterval) {
        clearInterval(auditStreamInterval);
        auditStreamInterval = null;
    }
    const statusBtn = document.getElementById('auditStreamStatusBtn');
    if (statusBtn) statusBtn.style.display = 'none';
}

function startAuditStream(intervalMs) {
    stopAuditStream();

    if (!intervalMs || intervalMs === '') {
        return;
    }

    const statusBtn = document.getElementById('auditStreamStatusBtn');
    if (statusBtn) statusBtn.style.display = 'inline-block';
    refreshAuditGrid();
    auditStreamInterval = setInterval(() => {
        refreshAuditGrid();
    }, parseInt(intervalMs));
}

function refreshAuditGrid() {
    if (auditGrid) {
        auditGrid.forceRender();
    }
}



async function loadSemesterStats() {
    const semester = document.getElementById('semesterSelect').value;
    if (!semester) {
        const container = document.getElementById('semesterStatsContainer');
        container.innerHTML = '<div class="alert alert-info">Bitte wählen Sie ein Semester aus dem Dropdown oben.</div>';
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/statistics/semester/${semester}`);
        const data = await response.json();

        const container = document.getElementById('semesterStatsContainer');
        container.innerHTML = `
            <div class="row">
                <div class="col-md-3">
                    <div class="stat-card">
                        <h5>Gesamtbenutzer</h5>
                        <div class="number">${data.totalUsers}</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stat-card">
                        <h5>Mit Passkeys</h5>
                        <div class="number">${data.passkeyUsers}</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stat-card">
                        <h5>Kalender hochgeladen</h5>
                        <div class="number">${data.calendarUploads}</div>
                    </div>
                </div>
                <div class="col-md-3">
                    <div class="stat-card">
                        <h5>Vermittelt</h5>
                        <div class="number">${data.successfulMatches}</div>
                    </div>
                </div>
            </div>
        `;
    } catch (error) {
        console.error('Error loading semester statistics:', error);
    }
}

async function loadTimelineStats() {
    const semester = document.getElementById('timelineSemesterSelect').value;
    const startEl = document.getElementById('timelineStart');
    const endEl = document.getElementById('timelineEnd');
    const intervalEl = document.getElementById('timelineInterval');
    const rangeLabel = document.getElementById('timelineRangeLabel');
    const includeSeries = {
        pageViews: document.getElementById('seriesPageViews')?.checked !== false,
        logins: document.getElementById('seriesLogins')?.checked !== false,
        calendarUploads: document.getElementById('seriesCalendarUploads')?.checked !== false,
        offersCreated: document.getElementById('seriesOffers')?.checked !== false,
        matches: document.getElementById('seriesMatches')?.checked !== false,
    };

    if (!semester) {
        if (charts.timeline) { charts.timeline.destroy(); charts.timeline = null; }
        const canvas = document.getElementById('timelineChart');
        if (canvas) {
            const ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.font = '16px Arial';
            ctx.fillStyle = '#666';
            ctx.textAlign = 'center';
            ctx.fillText('Bitte Semester auswählen.', canvas.width / 2, canvas.height / 2);
        }
        return;
    }

    try {
        const params = new URLSearchParams({ semester });
        const interval = intervalEl?.value || 'day';
        if (rangeLabel) rangeLabel.textContent = interval === 'hour' ? 'Tag' : 'Zeitraum';
        if (endEl) endEl.style.display = interval === 'hour' ? 'none' : '';
        params.set('interval', interval);
        const startVal = startEl?.value;
        const endVal = endEl?.value;
        if (startVal) {
            if (interval === 'hour') {
                const sd = new Date(startVal);
                const dayStart = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate(), 0, 0, 0, 0);
                const dayEnd = new Date(sd.getFullYear(), sd.getMonth(), sd.getDate(), 23, 59, 59, 999);
                params.set('start', String(dayStart.getTime()));
                params.set('end', String(dayEnd.getTime()));
            } else {
                params.set('start', String(new Date(startVal).getTime()));
                if (endVal) {
                    const d = new Date(endVal);
                    d.setHours(23, 59, 59, 999);
                    params.set('end', String(d.getTime()));
                }
            }
        } else if (interval === 'hour' && endVal) {
            const ed = new Date(endVal);
            const dayStart = new Date(ed.getFullYear(), ed.getMonth(), ed.getDate(), 0, 0, 0, 0);
            const dayEnd = new Date(ed.getFullYear(), ed.getMonth(), ed.getDate(), 23, 59, 59, 999);
            params.set('start', String(dayStart.getTime()));
            params.set('end', String(dayEnd.getTime()));
        }

        const response = await fetch(`${API_BASE}/statistics/timeline?${params.toString()}`);
        const data = await response.json();

        const isHourly = interval === 'hour';
        const normalizeToHour = (ts) => { const d = new Date(ts); d.setMinutes(0, 0, 0); return d.getTime(); };
        const normalizeToDay = (ts) => { const d = new Date(ts); return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime(); };
        let sortedXValues = [];
        if (data.start && data.end) {
            const startNum = Number(data.start);
            const endNum = Number(data.end);
            if (isNaN(startNum) || isNaN(endNum)) {
                const all = (data.series || []).flatMap(s => (s.points || []).map(p => Number(p[0]))).filter(v => !isNaN(v));
                if (all.length) {
                    const min = Math.min(...all);
                    const max = Math.max(...all);
                    if (isHourly) {
                        let t = normalizeToHour(min);
                        const endT = normalizeToHour(max);
                        while (t <= endT) { sortedXValues.push(t); t += 3600000; }
                    } else {
                        let d = new Date(normalizeToDay(min));
                        const endD = normalizeToDay(max);
                        while (d.getTime() <= endD) { sortedXValues.push(d.getTime()); d.setDate(d.getDate() + 1); d.setHours(0, 0, 0, 0); }
                    }
                }
            } else if (isHourly) {
                const startHour = normalizeToHour(startNum);
                const endHour = normalizeToHour(endNum);
                for (let t = startHour; t <= endHour; t += 3600000) {
                    sortedXValues.push(t);
                }
            } else {
                const d = new Date(normalizeToDay(startNum));
                const endDay = normalizeToDay(endNum);
                while (d.getTime() <= endDay) {
                    sortedXValues.push(d.getTime());
                    d.setDate(d.getDate() + 1);
                    d.setHours(0, 0, 0, 0);
                }
            }
        } else {
            const allXValues = new Set();
            (data.series || []).forEach(s => {
                (s.points || []).forEach(p => {
                    const raw = p[0];
                    const ts = typeof raw === 'number' ? raw : (typeof raw === 'string' && /^\d+$/.test(raw) ? Number(raw) : Date.parse(raw));
                    if (!isFinite(ts)) return;
                    allXValues.add(isHourly ? normalizeToHour(ts) : normalizeToDay(ts));
                });
            });
            sortedXValues = Array.from(allXValues).sort((a, b) => a - b);
        }

        const palette = {
            pageViews: '#3498db',
            logins: '#9b59b6',
            calendarUploads: '#2ecc71',
            offersCreated: '#f39c12',
            matches: '#e74c3c'
        };

        const datasets = (data.series || [])
            .filter(s => includeSeries[s.key] !== false)
            .map(s => {
                const pointMap = new Map((s.points || []).map(p => {
                    const raw = p[0];
                    const ts = typeof raw === 'number' ? raw : (typeof raw === 'string' && /^\d+$/.test(raw) ? Number(raw) : Date.parse(raw));
                    const normalizedX = isHourly ? normalizeToHour(ts) : normalizeToDay(ts);
                    return [normalizedX, p[1]];
                }));

                const filledData = sortedXValues.map(x => ({
                    x: x,
                    y: pointMap.has(x) ? pointMap.get(x) : 0
                }));
                return {
                    label: s.label,
                    data: filledData,
                    borderColor: palette[s.key] || '#3498db',
                    backgroundColor: (palette[s.key] || '#3498db') + '33',
                    borderWidth: 2,
                    tension: 0.3,
                    pointRadius: 1,
                    fill: false
                };
            });

        const canvas = document.getElementById('timelineChart');
        if (charts.timeline) charts.timeline.destroy();

        charts.timeline = new Chart(canvas.getContext('2d'), {
            type: 'line',
            data: { datasets },
            options: {
                maintainAspectRatio: false,
                responsive: true,
                parsing: true,
                normalized: true,
                interaction: { mode: 'nearest', axis: 'x', intersect: false },
                animation: { duration: 500, easing: 'easeOutCubic' },
                plugins: {
                    legend: { display: true },
                    tooltip: {
                        callbacks: {
                            title: items => {
                                if (!items?.length) return '';
                                const ts = items[0].parsed.x;
                                const d = new Date(ts);
                                return interval === 'hour' ? d.toLocaleString('de-DE') : d.toLocaleDateString('de-DE');
                            }
                        }
                    },
                },
                scales: {
                    x: {
                        type: 'time',
                        time: {
                            unit: interval === 'hour' ? 'hour' : 'day',
                            displayFormats: { hour: 'HH', day: 'dd.MM' }
                        },
                        ticks: {
                            maxRotation: 0,
                            autoSkip: true,
                            callback: (val) => {
                                if (interval === 'hour') {
                                    const h = new Date(val).getHours();
                                    return String(h).padStart(2, '0');
                                }
                                return undefined;
                            },
                        },
                    },
                    y: { beginAtZero: true }
                }
            }
        });
        adjustTimelineChartHeight();
        if (!window.__timelineResizeBound) {
            window.addEventListener('resize', adjustTimelineChartHeight);
            window.__timelineResizeBound = true;
        }


    } catch (error) {
        console.error('Error loading timeline statistics:', error);
    }
}

async function loadFlagStatistics() {
    try {
        const semester = document.getElementById('flagsSemesterSelect')?.value || '';
        const response = await fetch(`${API_BASE}/statistics/flags${semester ? `?semester=${encodeURIComponent(semester)}` : ''}`);
        const data = await response.json();

        const totalUsersBadge = document.getElementById('flagsTotalUsers');
        if (totalUsersBadge) {
            if (semester && data.selectedSemesterStats) totalUsersBadge.textContent = data.selectedSemesterStats.totalUsers;
            else if (data.totalUsersBySemester) {
                const sum = Object.values(data.totalUsersBySemester).reduce((a, b) => a + b, 0);
                totalUsersBadge.textContent = sum;
            } else totalUsersBadge.textContent = '-';
        }

        if (semester && data.selectedSemesterStats) {
            const sdata = {};
            sdata[semester] = data.selectedSemesterStats;
            createFlagsBySemesterChart(sdata);
        } else {
            createFlagsBySemesterChart(data.bySemester);
        }
        createFlagsDetailsGrid(data.bySemester);
    } catch (error) {
        console.error('Error loading flag statistics:', error);
    }
}

function createFlagsBySemesterChart(semesterData) {
    const canvas = document.getElementById('flagsBySemesterChart');
    if (!canvas) return;

    if (charts.flagsBySemester) charts.flagsBySemester.destroy();

    const semesters = Object.keys(semesterData).sort().reverse();
    const passkeyData = semesters.map(s => semesterData[s].usesPasskeys);
    const calendarData = semesters.map(s => semesterData[s].hasCalendar);
    const offersData = semesters.map(s => semesterData[s].hasOffers);
    const matchedData = semesters.map(s => semesterData[s].wasMatched);

    charts.flagsBySemester = new Chart(canvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels: semesters,
            datasets: [
                {
                    label: 'Passkeys',
                    data: passkeyData,
                    backgroundColor: '#3498db'
                },
                {
                    label: 'Kalender',
                    data: calendarData,
                    backgroundColor: '#2ecc71'
                },
                {
                    label: 'Angebote',
                    data: offersData,
                    backgroundColor: '#f39c12'
                },
                {
                    label: 'Vermittelt',
                    data: matchedData,
                    backgroundColor: '#e74c3c'
                }
            ]
        },
        options: {
            responsive: true,
            scales: {
                x: { stacked: false },
                y: { beginAtZero: true }
            }
        }
    });
}

function createFlagsDetailsGrid(semesterData) {
    const gridContainer = document.getElementById('flagsDetailsGrid');
    const semesters = Object.keys(semesterData).sort().reverse();

    const data = semesters.map(semester => [
        semester,
        semesterData[semester].usesPasskeys,
        semesterData[semester].hasCalendar,
        semesterData[semester].hasOffers,
        semesterData[semester].wasMatched
    ]);

    const container = document.getElementById('flagsDetailsGrid');
    container.innerHTML = '';
    const grid = new Grid({
        columns: [
            { name: 'Semester', width: '140px' },
            { name: 'Passkeys', width: '110px' },
            { name: 'Kalender', width: '110px' },
            { name: 'Angebote', width: '110px' },
            { name: 'Vermittelt', width: '110px' }
        ],
        data: data,
        pagination: { limit: 10 }
    }).render(container);
}

let feedbackGrid = null;

async function loadFeedbacks() {
    try {
        const container = document.getElementById('feedbackGrid');

        if (feedbackGrid) {
            feedbackGrid.updateConfig({
                server: {
                    url: `${API_BASE}/feedback?page=0&size=${pageSize}`,
                    then: data => data.content.map(feedback => [
                        feedback.id,
                        (feedback.creatorMail ? String(feedback.creatorMail).substring(0, 50) + (String(feedback.creatorMail).length > 50 ? '…' : '') : '-'),
                        feedback.rating || '-',
                        feedback.isPublic ? '✓ Public' : '✗ Private',
                        (feedback.feedback ? String(feedback.feedback).substring(0, 120) + (String(feedback.feedback).length > 120 ? '…' : '') : '-'),
                        new Date(feedback.createDate).toLocaleString('de-DE'),
                        html(`
                            <div class="d-flex gap-1">
                              <button class="btn btn-sm btn-warning btn-sm-compact" data-action="toggle-visibility" data-feedback="${feedback.id}" data-is-public="${feedback.isPublic}">
                                ${feedback.isPublic ? 'Sperren' : 'Freigeben'}
                              </button>
                              <button class="btn btn-sm btn-danger btn-sm-compact" data-action="delete-feedback" data-feedback="${feedback.id}">Löschen</button>
                            </div>
                        `)
                    ]),
                    total: data => data.totalElements
                }
            }).forceRender();
            return;
        }

        const truncate = (v, n = 60) => {
            if (v == null) return '-';
            const s = String(v);
            return s.length > n ? s.substring(0, n) + '…' : s;
        };

        feedbackGrid = new Grid({
            columns: [
                { name: 'ID', width: '80px', sort: false },
                { name: 'Ersteller (HS-Mail)', width: '120px' },
                { name: 'Rating', width: '80px', sort: false },
                { name: 'Sichtbarkeit', width: '100px', sort: false },
                { name: 'Feedback-Text', width: '300px', sort: false },
                { name: 'Erstellt am', width: '120px', sort: false },
                { name: 'Aktionen', width: '180px', sort: false }
            ],
            server: {
                url: `${API_BASE}/feedback?page=0&size=${pageSize}`,
                then: data => data.content.map(feedback => [
                    feedback.id,
                    truncate(feedback.creatorMail ? feedback.creatorMail.split("@")[0] : "", 30),
                    feedback.rating || '-',
                    feedback.isPublic ? '✓ Public' : '✗ Private',
                    truncate(feedback.feedback, 120),
                    new Date(feedback.createDate).toLocaleString('de-DE'),
                    html(`
                        <div class="d-flex gap-1">
                          <button class="btn btn-sm btn-warning btn-sm-compact" data-action="toggle-visibility" data-feedback="${feedback.id}" data-is-public="${feedback.isPublic}">
                            ${feedback.isPublic ? 'Sperren' : 'Freigeben'}
                          </button>
                          <button class="btn btn-sm btn-danger btn-sm-compact" data-action="delete-feedback" data-feedback="${feedback.id}">Löschen</button>
                        </div>
                    `)
                ]),
                total: data => data.totalElements
            },
            search: false,
            pagination: {
                enabled: true,
                limit: pageSize,
                server: {
                    url: (prev, page, limit) => {
                        try {
                            const u = new URL(prev, window.location.origin);
                            u.searchParams.set('page', page);
                            u.searchParams.set('size', limit || pageSize);
                            return u.toString();
                        } catch (_) {
                            return `${prev}${prev.includes('?') ? '&' : '?'}page=${page}&size=${limit || pageSize}`;
                        }
                    }
                }
            }
        }).render(container);

        if (!container.dataset.listenerAttached) {
            container.addEventListener('click', (ev) => {
                const btn = ev.target.closest('button[data-action]');
                if (!btn) return;
                const action = btn.getAttribute('data-action');
                const feedbackId = btn.getAttribute('data-feedback');
                if (action === 'toggle-visibility') toggleFeedbackVisibility(feedbackId);
                else if (action === 'delete-feedback') deleteFeedback(feedbackId);
            });
            container.dataset.listenerAttached = 'true';
        }
    } catch (error) {
        console.error('Error loading feedbacks:', error);
    }
}

async function toggleFeedbackVisibility(feedbackId) {
    try {
        const btn = document.querySelector(`button[data-feedback="${feedbackId}"][data-action="toggle-visibility"]`);
        const isCurrentlyPublic = btn.getAttribute('data-is-public') === 'true';
        const newIsPublic = !isCurrentlyPublic;

        const response = await fetch(`${API_BASE}/feedback/${feedbackId}/visibility`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ isPublic: newIsPublic })
        });

        if (response.ok) {
            alert('Feedback-Sichtbarkeit erfolgreich aktualisiert');
            loadFeedbacks();
        } else {
            alert('Fehler beim Aktualisieren der Feedback-Sichtbarkeit');
        }
    } catch (error) {
        console.error('Error toggling feedback visibility:', error);
    }
}

async function deleteFeedback(feedbackId) {
    if (confirm('Wollen Sie dieses Feedback wirklich löschen?')) {
        try {
            const response = await fetch(`${API_BASE}/feedback/${feedbackId}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                alert('Feedback erfolgreich gelöscht');
                loadFeedbacks();
            } else {
                alert('Fehler beim Löschen des Feedbacks');
            }
        } catch (error) {
            console.error('Error deleting feedback:', error);
        }
    }
}

function loadSettings() {
    loadSystemSettings();
    attachSettingsEventListeners();
}

async function loadSystemSettings() {
    try {
        const response = await fetch('/api/admin/settings', {
            method: 'GET',
            credentials: 'include'
        });

        if (response.ok) {
            const settings = await response.json();
            populateSystemSettings(settings);
            updateSystemTime();
            setInterval(updateSystemTime, 1000);
        }
    } catch (error) {
        console.error('Error loading system settings:', error);
        showSettingsAlert('Fehler beim Laden der Einstellungen', 'danger');
    }
}

function populateSystemSettings(settings) {
    const systemMode = document.getElementById('systemMode');
    const scheduleType = document.getElementById('scheduleType');
    const intervalHours = document.getElementById('intervalHours');
    const dailyTime = document.getElementById('dailyTime');
    const systemTimezone = document.getElementById('systemTimezone');
    const runMatchingBtn = document.getElementById('runMatchingBtn');

    if (systemMode) systemMode.value = settings.mode || 'DIRECT';
    if (systemTimezone) systemTimezone.value = settings.timezoneInfo || 'UTC';

    if (scheduleType) scheduleType.value = settings.scheduleType || '';
    if (intervalHours) intervalHours.value = settings.intervalHours || '';
    if (dailyTime) dailyTime.value = settings.dailyTime || '';

    toggleScheduleFields();

    if (runMatchingBtn) {
        runMatchingBtn.disabled = settings.mode !== 'POOLED_3CYCLE';
    }
}

function updateSystemTime() {
    const systemTimeEl = document.getElementById('systemTime');
    if (systemTimeEl) {
        const now = new Date();
        systemTimeEl.textContent = now.toLocaleTimeString('de-DE');
    }
}

function toggleScheduleFields() {
    const systemMode = document.getElementById('systemMode');
    const scheduleSettings = document.getElementById('scheduleSettings');
    const scheduleType = document.getElementById('scheduleType');
    const intervalHoursField = document.getElementById('intervalHoursField');
    const dailyTimeField = document.getElementById('dailyTimeField');
    const runMatchingBtn = document.getElementById('runMatchingBtn');

    if (!systemMode || !scheduleSettings) return;

    const isPooled = systemMode.value === 'POOLED_3CYCLE';
    scheduleSettings.style.display = isPooled ? 'block' : 'none';

    if (runMatchingBtn) {
        runMatchingBtn.disabled = !isPooled;
    }

    if (scheduleType) {
        const type = scheduleType.value;
        if (intervalHoursField) intervalHoursField.style.display = type === 'INTERVAL_HOURS' ? 'block' : 'none';
        if (dailyTimeField) dailyTimeField.style.display = type === 'DAILY_FIXED' ? 'block' : 'none';
    }
}

async function saveSystemSettings() {
    const systemMode = document.getElementById('systemMode');
    const scheduleType = document.getElementById('scheduleType');
    const intervalHours = document.getElementById('intervalHours');
    const dailyTime = document.getElementById('dailyTime');
    const saveBtn = document.getElementById('saveSettingsBtn');

    const data = {
        mode: systemMode.value,
        scheduleType: scheduleType.value || null,
        intervalHours: intervalHours.value ? parseInt(intervalHours.value) : null,
        dailyTime: dailyTime.value || null
    };

    if (data.mode === 'POOLED_3CYCLE') {
        if (!data.scheduleType) {
            showSettingsAlert('Bitte wählen Sie einen Zeitplan-Typ für den 3er-Modus', 'warning');
            return;
        }
        if (data.scheduleType === 'INTERVAL_HOURS' && !data.intervalHours) {
            showSettingsAlert('Bitte geben Sie ein Intervall in Stunden an', 'warning');
            return;
        }
        if (data.scheduleType === 'DAILY_FIXED' && !data.dailyTime) {
            showSettingsAlert('Bitte geben Sie eine Uhrzeit an', 'warning');
            return;
        }
    }

    try {
        if (saveBtn) {
            saveBtn.disabled = true;
            saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Wird gespeichert...';
        }

        const response = await fetch('/api/admin/settings', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            const result = await response.json();
            showSettingsAlert('Einstellungen erfolgreich gespeichert', 'success');
            populateSystemSettings(result);
        } else {
            showSettingsAlert('Fehler beim Speichern der Einstellungen', 'danger');
        }
    } catch (error) {
        console.error('Error saving settings:', error);
        showSettingsAlert(`Fehler: ${error.message}`, 'danger');
    } finally {
        if (saveBtn) {
            saveBtn.disabled = false;
            saveBtn.innerHTML = '<i class="bi bi-save me-2"></i>Einstellungen speichern';
        }
    }
}

async function calculateWhatIf() {
    const whatIfBtn = document.getElementById('whatIfBtn');
    const matchingResult = document.getElementById('matchingResult');

    try {
        if (whatIfBtn) {
            whatIfBtn.disabled = true;
            whatIfBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Berechne...';
        }

        const response = await fetch('/api/admin/matching/what-if', {
            method: 'GET',
            credentials: 'include'
        });

        if (response.ok) {
            const result = await response.json();
            displayMatchingResult(result);
        } else {
            showSettingsAlert('Fehler bei der What-If-Berechnung', 'danger');
        }
    } catch (error) {
        console.error('Error calculating what-if:', error);
        showSettingsAlert(`Fehler: ${error.message}`, 'danger');
    } finally {
        if (whatIfBtn) {
            whatIfBtn.disabled = false;
            whatIfBtn.innerHTML = '<i class="bi bi-calculator me-2"></i>What-If berechnen';
        }
    }
}

async function runMatching() {
    const runBtn = document.getElementById('runMatchingBtn');

    if (!confirm('Möchten Sie das Matching jetzt wirklich durchführen? Dies wird Tauschpartner zuweisen und E-Mails versenden.')) {
        return;
    }

    try {
        if (runBtn) {
            runBtn.disabled = true;
            runBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Läuft...';
        }

        const response = await fetch('/api/admin/matching/run', {
            method: 'POST',
            credentials: 'include'
        });

        if (response.ok) {
            const result = await response.json();
            displayMatchingResult(result);
            showSettingsAlert('Matching erfolgreich durchgeführt', 'success');
        } else {
            showSettingsAlert('Fehler beim Ausführen des Matchings', 'danger');
        }
    } catch (error) {
        console.error('Error running matching:', error);
        showSettingsAlert(`Fehler: ${error.message}`, 'danger');
    } finally {
        if (runBtn) {
            runBtn.disabled = false;
            runBtn.innerHTML = '<i class="bi bi-play-fill me-2"></i>Matching jetzt starten';
        }
    }
}

function displayMatchingResult(result) {
    const matchingResult = document.getElementById('matchingResult');
    const twoWayCount = document.getElementById('twoWayCount');
    const threeWayCount = document.getElementById('threeWayCount');
    const totalPersons = document.getElementById('totalPersons');
    const computationTime = document.getElementById('computationTime');


    if (twoWayCount) twoWayCount.textContent = result.counts.pairs2 || 0;
    if (threeWayCount) threeWayCount.textContent = result.counts.cycles3 || 0;
    if (totalPersons) totalPersons.textContent = result.counts.persons || 0;
    if (computationTime) computationTime.textContent = `${result.computationTimeMs || 0} ms`;

    if (matchingResult) {
        matchingResult.style.display = 'block';
    }
}

function attachSettingsEventListeners() {
    const systemMode = document.getElementById('systemMode');
    const scheduleType = document.getElementById('scheduleType');
    const saveSettingsBtn = document.getElementById('saveSettingsBtn');
    const whatIfBtn = document.getElementById('whatIfBtn');
    const runMatchingBtn = document.getElementById('runMatchingBtn');

    if (systemMode && !systemMode.dataset.listenerAttached) {
        systemMode.addEventListener('change', toggleScheduleFields);
        systemMode.dataset.listenerAttached = 'true';
    }

    if (scheduleType && !scheduleType.dataset.listenerAttached) {
        scheduleType.addEventListener('change', toggleScheduleFields);
        scheduleType.dataset.listenerAttached = 'true';
    }

    if (saveSettingsBtn && !saveSettingsBtn.dataset.listenerAttached) {
        saveSettingsBtn.addEventListener('click', saveSystemSettings);
        saveSettingsBtn.dataset.listenerAttached = 'true';
    }

    if (whatIfBtn && !whatIfBtn.dataset.listenerAttached) {
        whatIfBtn.addEventListener('click', calculateWhatIf);
        whatIfBtn.dataset.listenerAttached = 'true';
    }

    if (runMatchingBtn && !runMatchingBtn.dataset.listenerAttached) {
        runMatchingBtn.addEventListener('click', runMatching);
        runMatchingBtn.dataset.listenerAttached = 'true';
    }

    const deleteCalendarsBtn = document.getElementById('deleteAllCalendarsBtn');
    if (deleteCalendarsBtn && !deleteCalendarsBtn.dataset.listenerAttached) {
        deleteCalendarsBtn.addEventListener('click', showDeleteCalendarsConfirmation);
        deleteCalendarsBtn.dataset.listenerAttached = 'true';
    }

    const resetFlagsBtn = document.getElementById('resetAllFlagsBtn');
    if (resetFlagsBtn && !resetFlagsBtn.dataset.listenerAttached) {
        resetFlagsBtn.addEventListener('click', showResetFlagsConfirmation);
        resetFlagsBtn.dataset.listenerAttached = 'true';
    }
}

function showDeleteCalendarsConfirmation() {
    const confirmMsg = 'ACHTUNG: Dies wird alle Kalender aller Benutzer DAUERHAFT löschen.\n\nBitte geben Sie "LÖSCHEN" ein, um fortzufahren:';
    const userInput = prompt(confirmMsg);

    if (userInput === 'LÖSCHEN') {
        deleteAllCalendars();
    } else if (userInput !== null) {
        showSettingsAlert('Eingabe war nicht korrekt. Aktion abgebrochen.', 'warning');
    }
}

function showResetFlagsConfirmation() {
    const confirmMsg = 'ACHTUNG: Dies wird alle Flags (Kalender & Angebote) aller Benutzer DAUERHAFT zurücksetzen.\n\nBitte geben Sie "ZURÜCKSETZEN" ein, um fortzufahren:';
    const userInput = prompt(confirmMsg);

    if (userInput === 'ZURÜCKSETZEN') {
        resetAllFlags();
    } else if (userInput !== null) {
        showSettingsAlert('Eingabe war nicht korrekt. Aktion abgebrochen.', 'warning');
    }
}

async function deleteAllCalendars() {
    try {
        const deleteBtn = document.getElementById('deleteAllCalendarsBtn');
        if (deleteBtn) {
            deleteBtn.disabled = true;
            deleteBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Wird gelöscht...';
        }

        const response = await fetch(`${API_BASE}/settings/calendars/delete-all`, {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            const result = await response.json();
            showSettingsAlert(`Erfolgreich! ${result.count || 'Alle'} Kalender wurden gelöscht.`, 'success');
        } else {
            const error = await response.json();
            showSettingsAlert(`Fehler: ${error.message || 'Kalender konnten nicht gelöscht werden'}`, 'danger');
        }
    } catch (error) {
        console.error('Error deleting calendars:', error);
        showSettingsAlert(`Fehler: ${error.message}`, 'danger');
    } finally {
        const deleteBtn = document.getElementById('deleteAllCalendarsBtn');
        if (deleteBtn) {
            deleteBtn.disabled = false;
            deleteBtn.innerHTML = '<i class="bi bi-trash me-2"></i>Alle Kalender löschen';
        }
    }
}

async function resetAllFlags() {
    try {
        const resetBtn = document.getElementById('resetAllFlagsBtn');
        if (resetBtn) {
            resetBtn.disabled = true;
            resetBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Wird zurückgesetzt...';
        }

        const response = await fetch(`${API_BASE}/settings/flags/reset-all`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.ok) {
            const result = await response.json();
            showSettingsAlert(`Erfolgreich! Flags für ${result.count || 'alle'} Benutzer wurden zurückgesetzt.`, 'success');
        } else {
            const error = await response.json();
            showSettingsAlert(`Fehler: ${error.message || 'Flags konnten nicht zurückgesetzt werden'}`, 'danger');
        }
    } catch (error) {
        console.error('Error resetting flags:', error);
        showSettingsAlert(`Fehler: ${error.message}`, 'danger');
    } finally {
        const resetBtn = document.getElementById('resetAllFlagsBtn');
        if (resetBtn) {
            resetBtn.disabled = false;
            resetBtn.innerHTML = '<i class="bi bi-arrow-counterclockwise me-2"></i>Alle Flags zurücksetzen';
        }
    }
}

function showSettingsAlert(message, type = 'info') {
    const alertDiv = document.getElementById('settingsAlert');
    if (alertDiv) {
        const alertClass = `alert-${type}`;
        alertDiv.className = `alert ${alertClass}`;
        alertDiv.innerHTML = `
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            ${message}
        `;
        alertDiv.style.display = 'block';

        if (type === 'success') {
            setTimeout(() => {
                alertDiv.style.display = 'none';
            }, 5000);
        }
    }
}

window.addEventListener('load', () => {
    attachSettingsEventListeners();
    const semSel = document.getElementById('semesterSelect');
    if (semSel) semSel.addEventListener('change', () => {
        loadSemesterStats();
    });
    const timelineSel = document.getElementById('timelineSemesterSelect');
    if (timelineSel) timelineSel.addEventListener('change', async () => {
        const intervalEl = document.getElementById('timelineInterval');
        if (intervalEl) intervalEl.value = 'day';
        const startEl = document.getElementById('timelineStart');
        const endEl = document.getElementById('timelineEnd');
        const rangeLabel = document.getElementById('timelineRangeLabel');
        if (rangeLabel) rangeLabel.textContent = 'Zeitraum';
        if (endEl) endEl.style.display = '';
        try {
            const resp = await fetch(`${API_BASE}/statistics/timeline?semester=${encodeURIComponent(timelineSel.value)}`);
            const data = await resp.json();
            if (startEl && data.start) startEl.value = formatDateForInput(new Date(data.start));
            if (endEl && data.end) endEl.value = formatDateForInput(new Date(data.end));
        } catch (e) {
            console.warn('Semester range fetch failed', e);
        }
        loadTimelineStats();
    });

    const auditFilterBtn = document.getElementById('auditFilterBtn');
    if (auditFilterBtn) auditFilterBtn.addEventListener('click', (e) => {
        e.preventDefault();
        loadAuditLogs();
    });
    const auditSemSel = document.getElementById('auditSemester');
    if (auditSemSel) auditSemSel.addEventListener('change', loadAuditLogs);
    const auditEvtSel = document.getElementById('auditEventType');
    if (auditEvtSel) auditEvtSel.addEventListener('change', loadAuditLogs);
    const auditDebug = document.getElementById('auditIncludeDebug');
    if (auditDebug) auditDebug.addEventListener('change', loadAuditLogs);
    const auditUserInput = document.getElementById('auditUserId');
    if (auditUserInput) auditUserInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') loadAuditLogs();
    });

    const auditStreamIntervalSelect = document.getElementById('auditStreamInterval');
    if (auditStreamIntervalSelect) {
        auditStreamIntervalSelect.addEventListener('change', (e) => {
            const interval = e.target.value;
            if (interval) {
                startAuditStream(interval);
            } else {
                stopAuditStream();
            }
        });
    }

    const userSearchBtn = document.getElementById('userSearchBtn');
    if (userSearchBtn) userSearchBtn.addEventListener('click', () => {
        loadUsers();
    });
    const userSearch = document.getElementById('userSearch');
    if (userSearch) userSearch.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') loadUsers();
    });
    const usersSemesterSelect = document.getElementById('usersSemesterSelect');
    if (usersSemesterSelect) usersSemesterSelect.addEventListener('change', loadUsers);

    const semesterLoadBtn = document.getElementById('semesterLoadBtn');
    if (semesterLoadBtn) semesterLoadBtn.addEventListener('click', loadSemesterStats);
    const timelineLoadBtn = document.getElementById('timelineLoadBtn');
    if (timelineLoadBtn) timelineLoadBtn.addEventListener('click', loadTimelineStats);
    const timelineStart = document.getElementById('timelineStart');
    if (timelineStart) timelineStart.addEventListener('change', loadTimelineStats);
    const timelineEnd = document.getElementById('timelineEnd');
    if (timelineEnd) timelineEnd.addEventListener('change', loadTimelineStats);
    const timelineInterval = document.getElementById('timelineInterval');
    if (timelineInterval) timelineInterval.addEventListener('change', () => {
        const startEl = document.getElementById('timelineStart');
        const endEl = document.getElementById('timelineEnd');
        const rangeLabel = document.getElementById('timelineRangeLabel');
        if (timelineInterval.value === 'hour') {
            if (rangeLabel) rangeLabel.textContent = 'Tag';
            if (endEl) endEl.style.display = 'none';
            if (!startEl.value) {
                const semSel = document.getElementById('timelineSemesterSelect');
                fetch(`${API_BASE}/statistics/timeline?semester=${encodeURIComponent(semSel.value)}`)
                    .then(r => r.json()).then(data => {
                        if (data.start) startEl.value = formatDateForInput(new Date(data.start));
                        loadTimelineStats();
                    }).catch(() => loadTimelineStats());
                return;
            }
        } else {
            if (rangeLabel) rangeLabel.textContent = 'Zeitraum';
            if (endEl) endEl.style.display = '';
        }
        loadTimelineStats();
    });
    ['seriesPageViews', 'seriesLogins', 'seriesCalendarUploads', 'seriesOffers', 'seriesMatches'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('change', loadTimelineStats);
    });
    const flagsSemesterSelect = document.getElementById('flagsSemesterSelect');
    if (flagsSemesterSelect) flagsSemesterSelect.addEventListener('change', loadFlagStatistics);

    document.querySelectorAll('a[data-bs-toggle="tab"]').forEach(el => {
        el.addEventListener('shown.bs.tab', (e) => {
            const target = e.target.getAttribute('href');
            if (target === '#semesterStats') {
                const semSel = document.getElementById('semesterSelect');
                if (semSel && !semSel.value && semSel.options.length > 1) {
                    semSel.value = semSel.options[1].value;
                }
                if (semSel && semSel.value) {
                    loadSemesterStats();
                }
            }
            else if (target === '#timelineStats') {
                const timelineSel = document.getElementById('timelineSemesterSelect');
                const intervalEl = document.getElementById('timelineInterval');
                const startEl = document.getElementById('timelineStart');
                const endEl = document.getElementById('timelineEnd');
                const rangeLabel = document.getElementById('timelineRangeLabel');

                if (!timelineDefaultInitialized) {
                    if (intervalEl) intervalEl.value = 'hour';
                    if (rangeLabel) rangeLabel.textContent = 'Tag';
                    if (endEl) endEl.style.display = 'none';
                    const today = new Date();
                    if (startEl) startEl.value = formatDateForInput(today);
                    timelineDefaultInitialized = true;
                }

                if (timelineSel && timelineSel.value) {
                    loadTimelineStats();
                } else {
                    loadTimelineStats();
                }
            }
            if (target === '#users') loadUsers();
            if (target === '#auditlog') {
                const auditDebugCheckbox = document.getElementById('auditIncludeDebug');
                const includeDebug = auditDebugCheckbox ? auditDebugCheckbox.checked : false;
                loadAuditCategories(includeDebug);
                loadAuditLogs();
            }
            if (target === '#studiengaenge') {
                loadStudiengaenge();
            }
        });

        el.addEventListener('hidden.bs.tab', (e) => {
            const target = e.target.getAttribute('href');
            if (target === '#auditlog') {
                stopAuditStream();
            }
        });
    });
});

window.addEventListener('load', () => {
    const auditDebugCheckbox = document.getElementById('auditIncludeDebug');
    const includeDebug = auditDebugCheckbox ? auditDebugCheckbox.checked : false;
    loadAuditCategories(includeDebug);
    if (auditDebugCheckbox) {
        auditDebugCheckbox.addEventListener('change', (e) => {
            loadAuditCategories(e.target.checked);
        });
    }
});

function formatDateForInput(date) {
    if (!date) return '';
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

function adjustTimelineChartHeight() {
    const canvas = document.getElementById('timelineChart');
    const cardBody = document.getElementById('timelineChartBody');
    if (!canvas || !cardBody) return;
    const bodyTop = cardBody.getBoundingClientRect().top;
    const bodyStyles = window.getComputedStyle(cardBody);
    const paddingY = parseFloat(bodyStyles.paddingTop || '0') + parseFloat(bodyStyles.paddingBottom || '0');

    const mainContent = document.querySelector('.main-content');
    const mcStyles = mainContent ? window.getComputedStyle(mainContent) : null;
    const mainBottomPadding = mcStyles ? parseFloat(mcStyles.paddingBottom || '0') : 0;

    const safety = 24;

    let availableBody = Math.floor(window.innerHeight - bodyTop - mainBottomPadding - safety);
    if (availableBody < 260) availableBody = 260;

    let canvasHeight = availableBody - paddingY - 2;
    if (canvasHeight < 220) canvasHeight = 220;

    cardBody.style.height = availableBody + 'px';
    canvas.style.height = canvasHeight + 'px';
    canvas.style.width = '100%';

    if (charts.timeline && typeof charts.timeline.resize === 'function') {
        charts.timeline.resize();
    }

}

// ============================================================================
// STUDIENGÄNGE TAB
// ============================================================================

let studiengaengeGrid = null;
let cachedStudiengaenge = [];

async function loadStudiengaenge() {
    try {
        await renderStudiengaengeGrid();
        await refreshStudiengaengeList();
        attachStudiengaengeHandlers();
    } catch (e) {
        console.error('Error loading studiengaenge', e);
    }
}

async function refreshStudiengaengeList() {
    const resp = await fetch('/api/admin/studiengaenge');
    if (!resp.ok) return;
    cachedStudiengaenge = await resp.json();
    // Populate select
    const sel = document.getElementById('sgSelect');
    if (sel) {
        const v = sel.value;
        sel.innerHTML = '<option value="">– auswählen –</option>' + cachedStudiengaenge.map(s => `<option value="${s.id}">${escapeHtml(s.shortCode || '')} – ${escapeHtml(s.name || '')}</option>`).join('');
        if (v) sel.value = v;
    }
    // Update grid
    if (studiengaengeGrid) {
        studiengaengeGrid.updateConfig({ data: cachedStudiengaenge.map(s => [s.shortCode, s.name, s.id]) }).forceRender();
    }
    // If a SG is selected, refresh NOT_SHARED
    const selEl = document.getElementById('sgSelect');
    if (selEl && selEl.value) loadAndDisplayKurzelAnalysis(parseInt(selEl.value));
}

async function renderStudiengaengeGrid() {
    const container = document.getElementById('studiengaengeGrid');
    if (!container) return;
    container.innerHTML = '';
    studiengaengeGrid = new Grid({
        columns: [
            { name: 'Kürzel', width: '100px' },
            { name: 'Name', width: '260px' },
            {
                name: 'Aktionen', width: '180px', formatter: (_, row) => {
                    const id = row.cells[2].data;
                    return html(`<div class="d-flex gap-1">
                    <button class="btn btn-sm btn-secondary" data-action="sg-edit" data-id="${id}">Bearbeiten</button>
                    <button class="btn btn-sm btn-danger" data-action="sg-delete" data-id="${id}">Löschen</button>
                </div>`);
                }, sort: false
            }
        ],
        data: [],
        search: false,
        pagination: { enabled: false }
    }).render(container);

    container.addEventListener('click', async (e) => {
        const btn = e.target.closest('button[data-action]');
        if (!btn) return;
        const id = parseInt(btn.getAttribute('data-id'));
        const action = btn.getAttribute('data-action');
        if (action === 'sg-edit') {
            const sg = cachedStudiengaenge.find(x => x.id === id);
            if (!sg) return;
            const name = prompt('Name', sg.name || '');
            if (name === null) return;
            const shortCode = prompt('Kürzel', sg.shortCode || '');
            if (shortCode === null) return;
            const resp = await fetch(`/api/admin/studiengaenge/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, shortCode }) });
            if (resp.ok) await refreshStudiengaengeList(); else alert('Fehler beim Speichern');
        } else if (action === 'sg-delete') {
            if (!confirm('Studiengang wirklich löschen? (NOT_SHARED-Einträge werden entfernt)')) return;
            const resp = await fetch(`/api/admin/studiengaenge/${id}`, { method: 'DELETE' });
            if (resp.ok) await refreshStudiengaengeList(); else alert('Fehler beim Löschen');
        }
    });
}

function attachStudiengaengeHandlers() {
    const addBtn = document.getElementById('sgAddBtn');
    if (addBtn && !addBtn.dataset.listenerAttached) {
        addBtn.addEventListener('click', async () => {
            const name = prompt('Name');
            if (!name) return;
            const shortCode = prompt('Kürzel (z.B. WI)');
            if (!shortCode) return;
            const resp = await fetch('/api/admin/studiengaenge', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, shortCode }) });
            if (resp.ok) await refreshStudiengaengeList(); else alert('Fehler beim Anlegen');
        });
        addBtn.dataset.listenerAttached = 'true';
    }

    const sel = document.getElementById('sgSelect');
    if (sel && !sel.dataset.listenerAttached) {
        sel.addEventListener('change', async () => {
            const v = sel.value;
            if (!v) {
                document.getElementById('kurzelAnalysisContainer').style.display = 'none';
                return;
            }
            const sgId = parseInt(v);
            await loadAndDisplayKurzelAnalysis(sgId);
        });
        sel.dataset.listenerAttached = 'true';
    }

    const addNs = document.getElementById('notSharedAddBtn');
    if (addNs && !addNs.dataset.listenerAttached) {
        addNs.addEventListener('click', async () => {
            const sel = document.getElementById('sgSelect');
            const input = document.getElementById('notSharedAddInput');
            const sgId = sel && sel.value ? parseInt(sel.value) : null;
            const baseName = (input?.value || '').trim();
            if (!sgId) { alert('Bitte Studiengang wählen'); return; }
            if (!baseName) { alert('Basisname eingeben'); return; }
            const resp = await fetch('/api/admin/notshared', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ studiengangId: sgId, baseName }) });
            if (resp.ok) { input.value = ''; await loadAndDisplayKurzelAnalysis(sgId); } else alert('Fehler beim Hinzufügen');
        });
        addNs.dataset.listenerAttached = 'true';
    }
}

/**
 * Lädt die Kürzel-Analyse und zeigt sie in zwei Reihen Badges an
 */
async function loadAndDisplayKurzelAnalysis(sgId) {
    if (!sgId) {
        return;
    }

    try {
        const resp = await fetch(`/api/admin/notshared/${sgId}/available-kurzel`);
        if (!resp.ok) {
            console.error('Fehler beim Laden der Kürzel-Analyse');
            return;
        }

        const data = await resp.json();
        const allKurzel = data.allKurzel || [];
        const notSharedKurzel = data.notSharedKurzel || [];

        // Container vorbereiten
        const container = document.getElementById('kurzelAnalysisContainer');
        const allBadgesDiv = document.getElementById('allKurzelBadges');
        const notSharedBadgesDiv = document.getElementById('notSharedKurzelBadges');

        if (!container || !allBadgesDiv || !notSharedBadgesDiv) {
            return;
        }

        // Alle Kürzel anzeigen
        allBadgesDiv.innerHTML = '';
        if (allKurzel.length === 0) {
            allBadgesDiv.innerHTML = '<span class="text-muted">Keine Kürzel verfügbar</span>';
        } else {
            allKurzel.forEach(kurzel => {
                const badge = document.createElement('span');
                badge.className = 'badge rounded-pill text-bg-primary';
                badge.style.cursor = 'pointer';
                badge.title = 'Klicken zum als NOT_SHARED hinzufügen';
                badge.textContent = kurzel;

                badge.addEventListener('click', async () => {
                    // Prüfe, ob dieses Kürzel bereits NOT_SHARED ist
                    if (notSharedKurzel.includes(kurzel)) {
                        alert(`"${kurzel}" ist bereits NOT_SHARED für diesen Studiengang`);
                        return;
                    }

                    // Füge als NOT_SHARED hinzu
                    if (!confirm(`"${kurzel}" als NOT_SHARED für diesen Studiengang hinzufügen?`)) {
                        return;
                    }

                    try {
                        const addResp = await fetch('/api/admin/notshared', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ studiengangId: sgId, baseName: kurzel })
                        });

                        if (addResp.ok) {
                            // Erfolg - Analyse aktualisieren
                            await loadAndDisplayKurzelAnalysis(sgId);
                        } else {
                            alert('Fehler beim Hinzufügen als NOT_SHARED');
                        }
                    } catch (error) {
                        console.error('Fehler beim Hinzufügen:', error);
                        alert('Fehler beim Hinzufügen als NOT_SHARED');
                    }
                });

                allBadgesDiv.appendChild(badge);
            });
        }

        // NOT_SHARED Kürzel anzeigen (mit Lösch-Funktion)
        notSharedBadgesDiv.innerHTML = '';
        if (notSharedKurzel.length === 0) {
            notSharedBadgesDiv.innerHTML = '<span class="text-muted">Keine NOT_SHARED Kürzel konfiguriert</span>';
        } else {
            // Laden wir die IDs der NOT_SHARED Einträge
            const resp2 = await fetch(`/api/admin/notshared/${sgId}`);
            if (resp2.ok) {
                const items = await resp2.json();
                const itemMap = {};
                items.forEach(item => {
                    itemMap[item.baseName] = item.id;
                });

                notSharedKurzel.forEach(kurzel => {
                    const chip = document.createElement('span');
                    chip.className = 'badge rounded-pill text-bg-success';
                    chip.textContent = kurzel;
                    chip.style.cursor = 'pointer';
                    chip.title = 'Klicken zum Löschen';

                    chip.addEventListener('click', async () => {
                        if (!confirm(`NOT_SHARED-Eintrag "${kurzel}" entfernen?`)) return;
                        const itemId = itemMap[kurzel];
                        if (!itemId) { alert('Fehler: ID nicht gefunden'); return; }
                        const delResp = await fetch(`/api/admin/notshared/${itemId}`, { method: 'DELETE' });
                        if (delResp.ok) {
                            await loadAndDisplayKurzelAnalysis(sgId);
                        } else {
                            alert('Fehler beim Löschen');
                        }
                    });

                    notSharedBadgesDiv.appendChild(chip);
                });
            }
        }

        // Container anzeigen
        container.style.display = 'block';
    } catch (error) {
        console.error('Fehler beim Abrufen der Kürzel-Analyse:', error);
    }
}

// ============================================================================
// EVALUATION TAB
// ============================================================================

let evaluationSemesters = [];
let currentEvalSemester = null;
let evaluationQuestions = [];

async function loadEvaluation() {
    // Semester-Select wird bereits durch updateSemesterSelects befüllt
    setupEvaluationHandlers();

    // Automatisch das neueste Semester laden, wenn vorhanden
    const evalSemesterSelect = document.getElementById('evalSemesterSelect');
    if (evalSemesterSelect && evalSemesterSelect.value) {
        const newestSemester = evalSemesterSelect.value;
        currentEvalSemester = {
            id: newestSemester,
            name: newestSemester
        };
        await loadEvaluationResults(newestSemester);
    }
}

function setupEvaluationHandlers() {
    const semesterSelect = document.getElementById('evalSemesterSelect');

    semesterSelect.addEventListener('change', async (e) => {
        const semesterId = e.target.value;
        if (!semesterId) {
            showEvalNoSelection();
            return;
        }

        currentEvalSemester = {
            id: semesterId,
            name: semesterId
        };

        await loadEvaluationResults(semesterId);
    });
}


function showEvalNoSelection() {
    document.getElementById('evalStatsCard').style.display = 'none';
    document.getElementById('evalResultsView').style.display = 'none';
    document.getElementById('evalNoSelection').style.display = 'block';
}

async function loadEvaluationResults(semesterId) {
    try {
        // Hide no selection message, show results
        document.getElementById('evalNoSelection').style.display = 'none';
        document.getElementById('evalStatsCard').style.display = 'block';
        document.getElementById('evalResultsView').style.display = 'block';

        // Load statistics
        const statsResponse = await fetch(`${API_BASE}/evaluation/statistics/${semesterId}`);
        if (!statsResponse.ok) throw new Error('Failed to load statistics');

        const stats = await statsResponse.json();

        document.getElementById('evalTotalResponses').textContent = stats.totalResponses || 0;
        document.getElementById('evalCompleteResponses').textContent = stats.completeResponses || 0;
        document.getElementById('evalPartialResponses').textContent = stats.partialResponses || 0;

        // Load results for each category
        await loadEvaluationResultsByCategory(semesterId);

    } catch (error) {
        console.error('Error loading evaluation results:', error);
        showToast('Fehler', 'Evaluationsergebnisse konnten nicht geladen werden', 'error');
    }
}

async function loadEvaluationResultsByCategory(semesterId) {
    try {
        const response = await fetch(`${API_BASE}/evaluation/results/${semesterId}`);
        if (!response.ok) throw new Error('Failed to load results');

        const results = await response.json();

        // Render results for each category
        renderCategoryResults('general', results.general || [], 'generalResults');
        renderCategoryResults('usability', results.usability || [], 'usabilityResults');
        renderCategoryResults('roundexchange', results.roundexchange || [], 'roundexchangeResults');
        renderCategoryResults('satisfaction', results.satisfaction || [], 'satisfactionResults');
        renderFeedbackText(results.feedback || [], 'feedbacktextResults');

    } catch (error) {
        console.error('Error loading results by category:', error);
    }
}

function renderCategoryResults(category, results, containerId) {
    const container = document.getElementById(containerId);

    if (!results || results.length === 0) {
        container.innerHTML = '<p class="text-muted">Keine Daten verfügbar</p>';
        return;
    }

    let html = '';

    results.forEach(question => {
        html += `
            <div class="mb-4 pb-4 border-bottom">
                <h5 class="mb-3">${question.questionText}</h5>
        `;

        if (question.type === 'scale_1_5') {
            // Render scale results as bar chart
            html += renderScaleResults(question);
        } else if (question.type === 'checkbox' || question.type === 'radio') {
            // Render multiple choice results
            html += renderChoiceResults(question);
        } else if (question.type === 'select') {
            // Render dropdown results
            html += renderSelectResults(question);
        }

        html += `
                <div class="text-muted mt-2">
                    <small>Antworten: ${question.totalAnswers || 0}</small>
                </div>
            </div>
        `;
    });

    container.innerHTML = html;
}

function renderScaleResults(question) {
    const answers = question.answers || {};
    const total = question.totalAnswers || 1;
    const average = question.average || 0;

    let html = `
        <div class="mb-3">
            <strong>Durchschnitt: ${average.toFixed(2)}</strong>
        </div>
        <div class="scale-results">
    `;

    for (let i = 1; i <= 5; i++) {
        const count = answers[i] || 0;
        const percentage = ((count / total) * 100).toFixed(1);

        html += `
            <div class="align-items-center mb-2">
                <span class="me-2" style="min-width: 30px;">${i}</span>
                <div class="progress flex-grow-1" style="height: 25px;">
                    <div class="progress-bar bg-primary" role="progressbar" 
                         style="width: ${percentage}%" 
                         aria-valuenow="${percentage}" aria-valuemin="0" aria-valuemax="100">
                        ${count} (${percentage}%)
                    </div>
                </div>
            </div>
        `;
    }

    // Handle "not_used" if present
    if (answers.not_used) {
        const count = answers.not_used;
        const percentage = ((count / total) * 100).toFixed(1);
        html += `
            <div class="d-flex align-items-center mb-2">
                <span class="me-2" style="min-width: 30px;">N/A</span>
                <div class="progress flex-grow-1" style="height: 25px;">
                    <div class="progress-bar bg-secondary" role="progressbar" 
                         style="width: ${percentage}%" 
                         aria-valuenow="${percentage}" aria-valuemin="0" aria-valuemax="100">
                        ${count} (${percentage}%)
                    </div>
                </div>
            </div>
        `;
    }

    html += '</div>';
    return html;
}

function renderChoiceResults(question) {
    const answers = question.answers || {};
    const total = question.totalAnswers || 1;

    let html = '<div class="choice-results">';

    Object.entries(answers).forEach(([choice, count]) => {
        const percentage = ((count / total) * 100).toFixed(1);

        html += `
            <div class="align-items-center mb-2">
                <span class="me-2 flex-grow-1">${choice}</span>
                <div class="progress flex-grow-1" style="height: 25px;">
                    <div class="progress-bar bg-info" role="progressbar" 
                         style="width: ${percentage}%" 
                         aria-valuenow="${percentage}" aria-valuemin="0" aria-valuemax="100">
                        ${count} (${percentage}%)
                    </div>
                </div>
            </div>
        `;
    });

    html += '</div>';
    return html;
}

function renderSelectResults(question) {
    return renderChoiceResults(question);
}

function renderFeedbackText(feedbackList, containerId) {
    const container = document.getElementById(containerId);

    if (!feedbackList || feedbackList.length === 0) {
        container.innerHTML = '<p class="text-muted">Keine Freitextantworten vorhanden</p>';
        return;
    }

    let html = '';

    feedbackList.forEach(item => {
        html += `
            <div class="card mb-3">
                <div class="card-header">
                    <strong>${item.questionText}</strong>
                </div>
                <div class="card-body">
        `;

        if (item.responses && item.responses.length > 0) {
            item.responses.forEach((response, idx) => {
                if (response.trim()) {
                    html += `
                        <div class="alert alert-light mb-2">
                            <small class="text-muted">Antwort ${idx + 1}:</small><br>
                            ${escapeHtml(response)}
                        </div>
                    `;
                }
            });
        } else {
            html += '<p class="text-muted">Keine Antworten</p>';
        }

        html += `
                </div>
            </div>
        `;
    });

    container.innerHTML = html;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showToast(title, message, type = 'info') {
    // Toast implementation (reuse from evaluation.js or implement bootstrap toast)
    const bgColor = type === 'success' ? '#28a745' : type === 'error' ? '#dc3545' : '#17a2b8';

    const toastContainer = document.createElement('div');
    toastContainer.style.position = 'fixed';
    toastContainer.style.top = '20px';
    toastContainer.style.right = '20px';
    toastContainer.style.zIndex = '9999';

    toastContainer.innerHTML = `
        <div class="toast show" role="alert" style="background-color: ${bgColor}; color: white; min-width: 300px;">
            <div class="toast-header" style="background-color: ${bgColor}; color: white; border-bottom: 1px solid rgba(255,255,255,0.2);">
                <strong class="me-auto">${title}</strong>
                <button type="button" class="btn-close btn-close-white" onclick="this.closest('.toast').parentElement.remove()"></button>
            </div>
            <div class="toast-body">
                ${message}
            </div>
        </div>
    `;

    document.body.appendChild(toastContainer);

    setTimeout(() => {
        toastContainer.remove();
    }, 5000);
}
