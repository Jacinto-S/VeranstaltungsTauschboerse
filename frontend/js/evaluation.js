// evaluation.js - Handles evaluation form logic with local caching

const CACHE_KEY = 'evaluation_draft';
const API_BASE = '/api/evaluation';

let isSubmitted = false;
let isLoggedIn = false;
let hasChanges = false;

// Initialize on page load
document.addEventListener('DOMContentLoaded', async () => {
    await checkAuthStatus();

    if (!isLoggedIn) {
        showLoginRequired();
        return;
    }

    await loadExistingResponse();
    setupFormHandlers();
    loadCachedData();
    setupAutoSave();
});

// Check if user is authenticated
async function checkAuthStatus() {
    try {
        const response = await fetch('/whoami', {
            credentials: 'include'
        });

        if (response.ok) {
            const data = await response.text();
            if (data && data == "User not logged in") {
                isLoggedIn = false;
            } else {
                isLoggedIn = true;
            }
        } else {
            isLoggedIn = false;
        }
    } catch (error) {
        console.error('Error checking auth status:', error);
        isLoggedIn = false;
    }
}

// Show login required message
function showLoginRequired() {
    document.getElementById('loginRequired').style.display = 'block';
    document.getElementById('evaluationForm').style.display = 'none';
    document.getElementById('redirectToLogin').addEventListener('click', navigateToLogin);
}

// Navigate to login page with redirect flag
function navigateToLogin() {
    localStorage.setItem('evaluation_login_redirect', 'true');
    window.location.href = '/';
}

// Load existing response from server
async function loadExistingResponse() {
    try {
        const response = await fetch(`${API_BASE}/my-response`, {
            credentials: 'include'
        });

        if (response.ok) {
            const data = await response.json();
            if (data && data.submitted) {
                isSubmitted = true;
                populateFormData(data.answers);
                showSuccessMessage();
            } else if (data && data.answers) {
                populateFormData(data.answers);
            }
        }
    } catch (error) {
        console.error('Error loading existing response:', error);
    }
}

// Populate form with data
function populateFormData(data) {
    if (!data) return;

    // Text inputs and selects
    for (const [key, value] of Object.entries(data)) {
        const element = document.querySelector(`[name="${key}"]`);

        if (element) {
            if (element.type === 'radio') {
                const radio = document.querySelector(`[name="${key}"][value="${value}"]`);
                if (radio) radio.checked = true;
            } else if (element.type === 'checkbox') {
                // Handle checkbox arrays
                if (Array.isArray(value)) {
                    value.forEach(val => {
                        const checkbox = document.querySelector(`[name="${key}"][value="${val}"]`);
                        if (checkbox) checkbox.checked = true;
                    });
                }
            } else {
                element.value = value;
            }
        }
    }

    updateCharCounters();
}

function setupFormHandlers() {
    const form = document.getElementById('evalForm');

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        await submitEvaluation();
    });

    form.addEventListener('change', () => {
        hasChanges = true;
        showUnsavedAlert();
    });

    form.addEventListener('input', () => {
        hasChanges = true;
        showUnsavedAlert();
    });

    document.getElementById('frustrations').addEventListener('input', (e) => {
        updateCharCounter('frustrations', 'frustrationsCounter');
    });

    document.getElementById('suggestions').addEventListener('input', (e) => {
        updateCharCounter('suggestions', 'suggestionsCounter');
    });

    document.getElementById('editResponsesBtn').addEventListener('click', () => {
        hideSuccessMessage();
        isSubmitted = false;
    });
}

function updateCharCounter(textareaId, counterId) {
    const textarea = document.getElementById(textareaId);
    const counter = document.getElementById(counterId);
    counter.textContent = textarea.value.length;
}

function updateCharCounters() {
    updateCharCounter('frustrations', 'frustrationsCounter');
    updateCharCounter('suggestions', 'suggestionsCounter');
}

function showUnsavedAlert() {
    document.getElementById('unsavedAlert').style.display = 'block';
}

function hideUnsavedAlert() {
    document.getElementById('unsavedAlert').style.display = 'none';
    hasChanges = false;
}

function showSuccessMessage() {
    document.getElementById('evaluationForm').style.display = 'none';
    document.getElementById('successMessage').style.display = 'block';
    window.scrollTo(0, 0);
}

function hideSuccessMessage() {
    document.getElementById('evaluationForm').style.display = 'block';
    document.getElementById('successMessage').style.display = 'none';
}

function getFormData() {
    const form = document.getElementById('evalForm');
    const formData = new FormData(form);
    const data = {};

    const hearAbout = [];
    formData.getAll('hearAbout').forEach(val => hearAbout.push(val));
    if (hearAbout.length > 0) {
        data.hearAbout = hearAbout;
    }

    for (const [key, value] of formData.entries()) {
        if (key !== 'hearAbout') {
            data[key] = value;
        }
    }

    return data;
}

function saveToCache() {
    const data = getFormData();
    try {
        localStorage.setItem(CACHE_KEY, JSON.stringify(data));
    } catch (error) {
        console.error('Error saving to cache:', error);
    }
}

function loadCachedData() {
    try {
        const cached = localStorage.getItem(CACHE_KEY);
        if (cached && !isSubmitted) {
            const data = JSON.parse(cached);
            populateFormData(data);
        }
    } catch (error) {
        console.error('Error loading cached data:', error);
    }
}

function clearCache() {
    try {
        localStorage.removeItem(CACHE_KEY);
    } catch (error) {
        console.error('Error clearing cache:', error);
    }
}

function setupAutoSave() {
    setInterval(() => {
        if (hasChanges && !isSubmitted) {
            saveToCache();
            console.log('Auto-saved to cache');
        }
    }, 15000);

    window.addEventListener('beforeunload', (e) => {
        if (hasChanges && !isSubmitted) {
            saveToCache();
        }
    });
}

function validateForm() {
    const form = document.getElementById('evalForm');

    if (!form.checkValidity()) {
        form.reportValidity();
        return false;
    }

    const hearAboutChecked = document.querySelectorAll('input[name="hearAbout"]:checked').length > 0;
    if (!hearAboutChecked) {
        alert('Bitte wähle mindestens eine Option aus, wie du von der Tauschbörse erfahren hast.');
        return false;
    }

    return true;
}

async function submitEvaluation() {
    if (!validateForm()) {
        return;
    }

    const data = getFormData();

    try {
        const response = await fetch(`${API_BASE}/submit`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(data)
        });

        if (response.ok) {
            isSubmitted = true;
            clearCache();
            hideUnsavedAlert();
            showSuccessMessage();
            showToast('Erfolg', 'Ihre Evaluation wurde erfolgreich übermittelt. Vielen Dank!', 'success');
        } else {
            const error = await response.json();
            showToast('Fehler', error.message || 'Fehler beim Absenden der Evaluation', 'error');
        }
    } catch (error) {
        console.error('Error submitting evaluation:', error);
        showToast('Fehler', 'Netzwerkfehler beim Absenden der Evaluation', 'error');
    }
}

function showToast(title, message, type = 'info') {
    const toastContainer = document.createElement('div');
    toastContainer.style.position = 'fixed';
    toastContainer.style.top = '20px';
    toastContainer.style.right = '20px';
    toastContainer.style.zIndex = '9999';

    const bgColor = type === 'success' ? '#28a745' : type === 'error' ? '#dc3545' : '#17a2b8';

    toastContainer.innerHTML = `
        <div class="toast show" role="alert" style="background-color: ${bgColor}; color: white;">
            <div class="toast-header" style="background-color: ${bgColor}; color: white; border-bottom: 1px solid rgba(255,255,255,0.2);">
                <strong class="me-auto">${title}</strong>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast"></button>
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

    toastContainer.querySelector('.btn-close').addEventListener('click', () => {
        toastContainer.remove();
    });
}
