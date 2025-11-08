import sanitizeHtml from 'sanitize-html';
import 'altcha';


var aorurl = document.getElementById('aorurl');
var aorurl2 = document.getElementById('aorurl2');
var aorurl3 = document.getElementById('aorurl3');
var powverified = false;
var powpayload = "";

const passkeyManager = document.getElementById('passkeyManager');
const passkeyList = document.getElementById('passkeyList');
const passkeyEmptyState = document.getElementById('passkeyEmptyState');
const passkeyLabelInput = document.getElementById('passkey-label-input');
const passkeyManageModalEl = document.getElementById('passkey-manage-modal');
const manageAddPasskeyBtn = document.getElementById('manage-add-passkey');
const MAX_PASSKEY_LABEL_LENGTH = 120;
let isLoadingPasskeys = false;

let studiengangSelect = document.getElementById('studiengangSelect');
let saveStudiengangBtn = document.getElementById('saveStudiengangBtn');
let studiengangSettingsSelect = document.getElementById('studiengangSettingsSelect');
let saveStudiengangSettingsBtn = document.getElementById('saveStudiengangSettings');
let sgSettingsBox = document.getElementById('sgSettings');

let cachedStudiengang = null;
let cachedStudiengaengeListe = null;
let studiengaengeListPromise = null; // Promise-Cache für Race Condition Prevention

function copy(event) {
    event.preventDefault();
    var range = document.createRange();
    window.getSelection().removeAllRanges();
    window.getSelection().addRange(range);
    document.execCommand('copy');
    window.getSelection().removeAllRanges();
    showMessage("Link kopiert", "Link wurde in die Zwischenablage kopiert");
};
aorurl.addEventListener('click', copy);
aorurl2.addEventListener('click', copy);
aorurl3.addEventListener('click', copy);


var uploadHint = document.getElementById('uploadHint');
var myKalendar = document.getElementById('weekCalendar');
var file = document.getElementById('fileupload');

myKalendar.addEventListener('dragover', function (event) {
    uploadHint.style.border = "2px dashed black";
    uploadHint.style.filter = "brightness(1.5)";
    event.preventDefault();
});
myKalendar.addEventListener('dragenter', function (event) {
    event.preventDefault();
});
myKalendar.addEventListener('dragleave', function (event) {
    uploadHint.style.border = "none";
    uploadHint.style.filter = "brightness(1)";
    event.preventDefault();
});
file.addEventListener('change', function () {
    showUploadedCalendar();
});


myKalendar.addEventListener('drop', function (event) {
    event.preventDefault();
    uploadHint.style.border = "none";
    uploadHint.style.filter = "brightness(1)";
    if (event.dataTransfer.files.length != 1) {
        alert("Bitte nur eine Datei hochladen");
        return;
    }

    var file = event.dataTransfer.files[0];
    document.getElementById('fileupload').files = event.dataTransfer.files;
    showUploadedCalendar();

});


// Share Features
function copyToClipboard(text) {
    navigator.clipboard.writeText(text).then(function () {
        showMessage("Link kopiert", "Link wurde in die Zwischenablage kopiert");
    }, function (err) {
        alert('Fehler beim Kopieren des Links');
    });
}

function sharePage() {
    var url = window.location.href;
    if (navigator.share) {
        navigator.share({
            title: 'Wochenkalender',
            text: 'Schau dir mal die Termintauschbörse an um bessere Lehrveranstaltungstermine zu finden',
            url: url
        }).then(() => {
            console.log('Thanks for sharing!');
        })
            .catch(console.error);
    } else {
        copyToClipboard(url);
    }
}

document.getElementById('copylink').addEventListener('click', function () {
    copyToClipboard(window.location.href);
});
document.getElementById('sharebtn').addEventListener('click', function () {
    sharePage();
});

var email = document.getElementById('email');
var submitemail = document.getElementById('submitemail');
var state = 0;
var offer = null;
var gesucht = [];

if (email) {
    email.addEventListener('focus', function () {
        ensureConditionalPasskeyAutofill().catch(error => {
            console.error('Fehler beim Starten der Passkey-Autofill-Anfrage:', error);
        });
    });
}

var urlparams = new URLSearchParams(window.location.search);
var logintoken = urlparams.get('otttoken');
if (logintoken != null) {
    fetch("/login/ott", {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: `token=${encodeURIComponent(logintoken)}`

    }).then(response => {
        // if is redirect, then ok
        if (response.status == 201) {
            localStorage.setItem('uploadLocalCalendar', "true");
        } else if (response.status == 200) {
            localStorage.removeItem('uploadLocalCalendar');
        }
        if (response.status == 201 || response.status == 200) {
            // Nach erfolgreichem Login Kennzeichen setzen, damit SG-Abfrage direkt danach erfolgen kann
            localStorage.setItem('justLoggedIn', 'true');
            localStorage.setItem('loggedIn', "true");
            isLoggedIn = true;
            window.history.replaceState({}, document.title, "/");
            window.location.href = "/?pk";
        } else {
            window.history.replaceState({}, document.title, "/");
            setTimeout(() => {
                showMessage("Anmeldung fehlgeschlagen", "Der Anmeldungslink ist abgelaufen. Bitte versuche es erneut");
            }, 1000);
        }
    }).catch(error => {
        console.error('Error:', error);
        window.history.replaceState({}, document.title, "/");
        setTimeout(() => {
            showMessage("Anmeldung fehlgeschlagen", "Der Anmeldungslink ist abgelaufen. Bitte versuche es erneut");
        }, 1000);
    });

} else {

}




var countRequests = 0;

submitemail.addEventListener('mousedown', function (e) {
    e.preventDefault();



    if (email.value.includes("@student.hs-rm.de")) {
        requestLoginMail();
    } else {
        showMessage("Ungültige Addresse", "Bitte geben Sie eine gültige Studenten-E-Mail ein");
    }
    e.preventDefault();
    return false;
});
submitemail.addEventListener('click', function (e) {
    e.preventDefault();
    submitemail.dispatchEvent(new MouseEvent('mousedown'));
});



function requestLoginMail(notifyUser = true, toemail = email.value) {

    var textfrom = submitemail.innerText;

    if (email) {
        email.blur();
    }

    var firstpart = email.value.split("@")[0];
    if (!powverified) {
        alert("Bitte bestätigen Sie, dass Sie kein Roboter sind");
        return;
    }

    if (firstpart.includes(".")) {
        if (notifyUser) {
            submitemail.innerHTML = "<span class='spinner-border spinner-border-sm' role='status' aria-hidden='true'></span>";
            submitemail.disabled = true;
            showMessage("Login-Mail wird versendet", "Die Anmeldemail wird gerade versendet. Dies kann einen Moment dauern...");

            setTimeout(function () {
                submitemail.disabled = false;
            }, 30000);
        }

    } else {
        alert("Bitte geben Sie eine gültige Studenten-E-Mail ein");
        return;
    }

    fetch("/ott/generate", {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: `username=${encodeURIComponent(email.value)}&pow=${encodeURIComponent(powpayload)}`

    }).then(async response => {
        if (response.ok) {
            powpayload = "";
            powverified = false;
            if (notifyUser) {
                setTimeout(function () {
                    showMessage("Anmeldelink erfolgreich angefordert", "Bitte bestätige deine Anmeldung mit dem Link in der E-Mail. <a href='https://webmail.hs-rm.de/owa/#path=/mail/inbox'>HSRM E-Mail Client öffnen</a>");
                    submitemail.innerText = textfrom;
                }, 4000);
            }

        } else {
            if (response.status == 400) {
                var text = await response.text();
                if (text.indexOf("Invalid captcha") != -1) {
                    alert("Captcha-Eingabe wurde abgelehnt. Bitte versuche es erneut");
                    window.location.reload();

                    return;
                }
            }

            if (countRequests >= 2) {
                submitemail.disabled = false;
                submitemail.innerText = "Absenden";
                if (notifyUser) {
                    alert("Es ist ein Fehler aufgetreten. Bitte versuche es später erneut");
                }
                return;
            } else {
                countRequests++;
                setTimeout(requestLoginMail(notifyUser, toemail), 500);
            }
        }
    });
}


// Feedback
function showFeedback() {
    var ratings = document.getElementById('ratings');
    var feedbackSend = document.getElementById('feedbackSend');
    fetch("/randomFeedback?count=3", {
        method: 'GET',
        credentials: 'include'
    }).then(response => response.json()).then(data => {
        ratings.innerHTML = "";


        data.forEach(element => {
            var rating = document.createElement('div');
            rating.classList.add('card');
            rating.classList.add('col');
            rating.style.padding = "0px";

            var star = "⭐";
            var stars = "";
            for (let i = 0; i < element.rating; i++) {
                stars += star;
            }
            var timestring = new Date(element.createDate).toLocaleDateString();

            rating.innerHTML = `<div class="card-header">${sanitizeHtml((element.creator)) + " am " + timestring}</div>
        <div class="card-body">
            <h5 class="card-title"><span style='font-size:small'>${stars}</span></h5>
            <p class="card-text" style="max-height:95px;overflow:auto">${sanitizeHtml(element.feedback)}</p>
        </div>`;
            ratings.appendChild(rating);

        });
    });
}
showFeedback();
var newFeedback = document.getElementById('newFeedback');
newFeedback.addEventListener('click', function () {
    newFeedback.disabled = true;
    setTimeout(function () {
        newFeedback.disabled = false;
    }, 500);


    showFeedback();
});

feedbackSend.addEventListener('click', function () {
    var feedback = document.getElementById('feedbackTextarea').value;
    var stars = document.getElementById('feedbackStars').value;
    var isPrivateFeedback = document.getElementById('isPrivateFeedback').checked;
    var isPublic = isPrivateFeedback;
    if (stars < 3) {
        isPublic = false;
    } else {
        isPublic = isPublic == false;
    }

    fetch("/feedback", {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            isPublic: isPublic,
            feedback: feedback,
            rating: stars
        }),
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            var instance = Modal.getOrCreateInstance(document.getElementById('feedbackModal'));
            instance.hide();
            showMessage("Feedback erfolgreich gesendet", "Bitte beachte, dass es einige Minuten dauern kann, bis das Feedback sichtbar wird.");
            showFeedback();
        } else {
            alert("Fehler beim Senden des Feedbacks");
        }
    });
});

import { Modal, Toast } from 'bootstrap';
import * as ical from 'ical';

var removeAllOvers = document.getElementById('removeAllOvers');

removeAllOvers.addEventListener('click', function () {
    fetch("/removeMyOffers", {
        method: 'GET',
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            getMyCalendar();
            window.scrollTo(0, 0);
            gesucht = [];

            showMessage("Angebote gelöscht", "Alle Angebote wurden gelöscht.");
        } else {
            alert("Fehler beim Löschen der Angebote");
        }
    });
});
const toastElList = document.querySelectorAll('.toast')
const toastList = [];

toastList.push(new Toast(document.getElementById('MessageToast'), {}));

function showMessage(title, message) {
    var modalElement = document.getElementById('MessageToast');
    var modalInstance = toastList[0];
    var modalTitle = document.getElementById('MessageTitle');
    var modalBody = document.getElementById('MessageBody');

    modalTitle.textContent = title || 'Nachricht'
    modalBody.innerHTML = sanitizeHtml(message) || 'Nachricht'

    modalInstance.show();
}



function clearPasskeyList() {
    if (passkeyList) {
        passkeyList.innerHTML = "";
    }
    if (passkeyEmptyState) {
        passkeyEmptyState.style.display = "";
        passkeyEmptyState.textContent = "Du hast noch keinen Passkey erstellt.";
    }
}

function hidePasskeyManager() {
    if (passkeyManager) {
        passkeyManager.style.display = "none";
    }
    clearPasskeyList();
}

function showPasskeyManager(forceReload = false) {
    if (!passkeyManager) {
        return;
    }
    passkeyManager.style.display = "block";
    loadPasskeys(forceReload);
}

function formatPasskeyDate(value) {
    if (!value) {
        return "-";
    }
    try {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return "-";
        }
        return date.toLocaleString('de-DE', {
            dateStyle: 'medium',
            timeStyle: 'short'
        });
    } catch (error) {
        console.debug('Konnte Passkey-Datum nicht formatieren', error);
        return "-";
    }
}

function humanizeTransports(transports) {
    if (!Array.isArray(transports) || transports.length === 0) {
        return "";
    }
    return transports.map((transport) => {
        switch (transport) {
            case 'INTERNAL':
                return 'Gerät';
            case 'USB':
                return 'USB';
            case 'NFC':
                return 'NFC';
            case 'BLE':
                return 'Bluetooth';
            default:
                return transport;
        }
    }).join(', ');
}

function renderPasskeys(passkeys) {
    // In der aktuellen UI wird die Liste im Modal angezeigt, ein separater passkeyManager-Container ist nicht erforderlich
    if (!passkeyList || !passkeyEmptyState) {
        return;
    }

    passkeyList.innerHTML = "";

    if (!Array.isArray(passkeys) || passkeys.length === 0) {
        passkeyEmptyState.style.display = "";
        passkeyEmptyState.textContent = "Du hast noch keinen Passkey erstellt.";
        return;
    }

    passkeyEmptyState.style.display = "none";

    passkeys.forEach((passkey) => {
        const item = document.createElement('div');
        item.className = 'list-group-item passkey-entry';
        item.dataset.credentialId = passkey.credentialId;
        item.dataset.currentLabel = passkey.label ? passkey.label.trim() : "";

        const contentWrapper = document.createElement('div');
        contentWrapper.className = 'd-flex flex-column flex-md-row gap-3 align-items-md-start';

        const infoColumn = document.createElement('div');
        infoColumn.className = 'flex-grow-1';

        const labelGroup = document.createElement('div');
        labelGroup.className = 'input-group input-group-sm mb-2';

        const labelInput = document.createElement('input');
        labelInput.type = 'text';
        labelInput.maxLength = MAX_PASSKEY_LABEL_LENGTH;
        labelInput.className = 'form-control';
        labelInput.placeholder = 'Passkey benennen';
        labelInput.value = passkey.label || "";
        labelInput.dataset.role = 'label-input';

        const saveLabelButton = document.createElement('button');
        saveLabelButton.className = 'btn btn-outline-primary';
        saveLabelButton.textContent = 'Speichern';
        saveLabelButton.dataset.action = 'save-label';
        saveLabelButton.disabled = true;

        labelGroup.appendChild(labelInput);
        labelGroup.appendChild(saveLabelButton);

        infoColumn.appendChild(labelGroup);

        const metadata = document.createElement('div');
        metadata.className = 'small text-secondary';
        metadata.textContent = `Erstellt: ${formatPasskeyDate(passkey.created)} • Zuletzt genutzt: ${formatPasskeyDate(passkey.lastUsed)}`;

        const transportInfo = humanizeTransports(passkey.transports);
        if (transportInfo) {
            const transportLine = document.createElement('div');
            transportLine.className = 'small text-secondary';
            transportLine.textContent = `Authentifikator: ${transportInfo}`;
            infoColumn.appendChild(transportLine);
        }

        const deleteButton = document.createElement('button');
        deleteButton.className = 'btn btn-outline-danger btn-sm ms-md-auto';
        deleteButton.textContent = 'Löschen';
        deleteButton.dataset.action = 'delete-passkey';

        infoColumn.appendChild(metadata);

        contentWrapper.appendChild(infoColumn);
        contentWrapper.appendChild(deleteButton);

        item.appendChild(contentWrapper);
        passkeyList.appendChild(item);
    });
}

async function loadPasskeys(force = false) {
    if (!passkeyList) {
        return false;
    }
    if (localStorage.getItem('loggedIn') !== 'true') {
        hidePasskeyManager();
        return false;
    }
    if (isLoadingPasskeys) {
        return false;
    }

    isLoadingPasskeys = true;
    if (passkeyEmptyState) {
        passkeyEmptyState.style.display = "";
        passkeyEmptyState.textContent = "Passkeys werden geladen...";
    }

    let success = false;
    try {
        const response = await fetch("/webauthn/credentials", {
            method: 'GET',
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Fehler beim Laden der Passkeys');
        }

        const data = await response.json();
        renderPasskeys(Array.isArray(data) ? data : []);
        success = true;
    } catch (error) {
        console.error('Fehler beim Laden der Passkeys:', error);
        clearPasskeyList();
        if (passkeyEmptyState) {
            passkeyEmptyState.textContent = "Passkeys konnten nicht geladen werden.";
        }
    } finally {
        isLoadingPasskeys = false;
    }

    return success;
}
if (managePasskeysBtn) {
    managePasskeysBtn.addEventListener('click', async function () {
        const originalHtml = managePasskeysBtn.innerHTML;
        managePasskeysBtn.innerHTML = "<span class='spinner-border spinner-border-sm' role='status' aria-hidden='true'></span>";
        managePasskeysBtn.disabled = true;

        try {
            const ok = await loadPasskeys();
            if (!ok) {
                showMessage('Fehler', 'Passkeys konnten nicht geladen werden. Bitte versuche es erneut.');
                return;
            }
            const modal = Modal.getOrCreateInstance(document.getElementById('passkey-manage-modal'));
            modal.show();
        } finally {
            managePasskeysBtn.disabled = false;
            managePasskeysBtn.innerHTML = originalHtml;
        }
    });
}


document.addEventListener('show.bs.modal', function (e) {
    const openModals = document.querySelectorAll('.modal.show').length;
    const zIndex = 1250 + (10 * openModals);
    e.target.style.zIndex = zIndex + 5;
    setTimeout(() => {
        const backdrops = document.querySelectorAll('.modal-backdrop');
        if (backdrops.length > 0) {
            backdrops[backdrops.length - 1].style.zIndex = String(zIndex + 4);
        }
    }, 0);
});

document.addEventListener('hidden.bs.modal', function () {
    if (document.querySelectorAll('.modal.show').length > 0) {
        document.body.classList.add('modal-open');
    }
});

if (manageAddPasskeyBtn) {
    manageAddPasskeyBtn.addEventListener('click', function () {
        showPasskeyModal();
    });
}

async function deletePasskey(credentialId) {
    const csrf = await getCsrfToken();
    const response = await fetch("/webauthn/credentials/" + encodeURIComponent(credentialId), {
        method: 'DELETE',
        credentials: 'include',
        headers: {
            [csrf.headerName]: csrf.token
        }
    });

    if (!response.ok && response.status !== 204) {
        throw new Error('Fehler beim Löschen des Passkeys');
    }
}

async function updatePasskeyLabel(credentialId, label) {
    const csrf = await getCsrfToken();
    const normalized = label && label.trim().length > 0 ? label.trim() : null;

    const response = await fetch("/webauthn/credentials/" + encodeURIComponent(credentialId), {
        method: 'PATCH',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token
        },
        body: JSON.stringify({
            label: normalized
        })
    });

    if (!response.ok) {
        throw new Error('Fehler beim Speichern der Passkey-Bezeichnung');
    }

    return response.json();
}

if (passkeyList) {
    passkeyList.addEventListener('click', async function (event) {
        const actionButton = event.target.closest('button[data-action]');
        if (!actionButton) {
            return;
        }

        const row = actionButton.closest('.passkey-entry');
        if (!row) {
            return;
        }

        const credentialId = row.dataset.credentialId;
        if (!credentialId) {
            return;
        }

        if (actionButton.dataset.action === 'delete-passkey') {
            const confirmed = window.confirm('Möchtest du diesen Passkey wirklich löschen?');
            if (!confirmed) {
                return;
            }
            try {
                await deletePasskey(credentialId);
                showMessage('Passkey entfernt', 'Der Passkey wurde erfolgreich gelöscht.');
                await loadPasskeys();
            } catch (error) {
                console.error('Fehler beim Löschen des Passkeys:', error);
                showMessage('Fehler', 'Passkey konnte nicht gelöscht werden. Bitte versuche es erneut.');
            }
            return;
        }

        if (actionButton.dataset.action === 'save-label') {
            const input = row.querySelector('[data-role="label-input"]');
            if (!input) {
                return;
            }
            const trimmedValue = input.value.trim();
            const previousValue = row.dataset.currentLabel || "";

            if (trimmedValue === previousValue) {
                showMessage('Keine Änderungen', 'Die neue Bezeichnung entspricht der bisherigen.');
                actionButton.disabled = true;
                return;
            }

            if (trimmedValue.length > MAX_PASSKEY_LABEL_LENGTH) {
                showMessage('Eingabe zu lang', 'Die Bezeichnung darf maximal ' + MAX_PASSKEY_LABEL_LENGTH + ' Zeichen enthalten.');
                return;
            }

            try {
                await updatePasskeyLabel(credentialId, trimmedValue);
                showMessage('Passkey aktualisiert', 'Die Bezeichnung wurde gespeichert.');
                await loadPasskeys();
            } catch (error) {
                console.error('Fehler beim Aktualisieren der Passkey-Bezeichnung:', error);
                showMessage('Fehler', 'Die Bezeichnung konnte nicht gespeichert werden.');
            }
        }
    });

    passkeyList.addEventListener('input', function (event) {
        if (!event.target || event.target.dataset.role !== 'label-input') {
            return;
        }
        const row = event.target.closest('.passkey-entry');
        if (!row) {
            return;
        }
        const saveButton = row.querySelector('[data-action="save-label"]');
        if (!saveButton) {
            return;
        }
        const trimmedValue = event.target.value.trim();
        const previousValue = row.dataset.currentLabel || "";
        saveButton.disabled = trimmedValue === previousValue;
    });

    passkeyList.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter' || event.shiftKey) {
            return;
        }
        if (!event.target || event.target.dataset.role !== 'label-input') {
            return;
        }
        event.preventDefault();
        const row = event.target.closest('.passkey-entry');
        if (!row) {
            return;
        }
        const saveButton = row.querySelector('[data-action="save-label"]');
        if (saveButton && !saveButton.disabled) {
            saveButton.click();
        }
    });
}

async function showUploadedCalendar() {

    var filedata = file.files[0];
    if (!filedata) {
        return;
    }
    const ext = (filedata.name.split('.').pop() || '').toLowerCase();
    if (ext !== "ics") {
        alert("Bitte lade eine .ics-Kalenderdatei hoch (kein PDF).");
        // Input zurücksetzen, damit derselbe falsche Upload nicht hängen bleibt
        try { file.value = ""; } catch { }
        return;
    }

    var reader = new FileReader();
    reader.onload = async function (e) {
        var data = e.target.result;
        const trimmed = (data || "").replace(/^\uFEFF?/, '').trimStart();
        const looksLikePdf = trimmed.startsWith('%PDF');
        const beginsVCal = /^BEGIN:VCALENDAR/i.test(trimmed);
        if (looksLikePdf || !beginsVCal) {
            alert("Die gewählte Datei ist keine gültige iCalendar (.ics) Datei. Bitte exportiere die .ics aus AOR und lade diese hoch.");
            try { file.value = ""; } catch { }
            return;
        }

        let parsed;
        try {
            parsed = ical.parseICS(data);
        } catch (err) {
            alert("Die Datei konnte nicht gelesen werden. Bitte prüfe, ob es eine gültige .ics-Datei ist.");
            try { file.value = ""; } catch { }
            return;
        }
        if (localStorage.getItem('loggedIn') !== 'true') {
            localStorage.setItem('tempCalendar', data);
            showIcalCalendar(parsed);

            return;
        }

        try {
            const currentSg = await getMyStudiengang();
            if (!currentSg || !currentSg.id) {
                localStorage.setItem('tempCalendar', data);
                localStorage.setItem('uploadAfterStudiengang', 'true');
                try { await checkAndPromptStudiengang(); } catch { }
                showIcalCalendar(parsed);
                return;
            }
        } catch (e) {
            console.debug('Studiengang-Check vor Upload fehlgeschlagen', e);
        }

        fetch("/uploadKalender", {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain;charset=UTF-8'
            },
            body: data,
            credentials: 'include'
        }).then(response => {
            if (response.ok) {
                showMessage('Erfolg', 'Kalender erfolgreich hochgeladen');
                getMyCalendar();
            } else {
                showMessage('Fehler', 'Fehler beim Hochladen: Bitte lade eine gültige .ics-Datei hoch.');
            }
        });

        showIcalCalendar(parsed);
    };
    reader.readAsText(filedata);
}


document.getElementById("confirmOffer").addEventListener('click', function () {
    if (state == 0) {
        alert("Bitte wählen Sie ein Angebot aus");
        return;
    }
    if (gesucht.length == 0) {
        alert("Bitte wähle mindestens ein Angebot aus");
        return;
    }
    var angebot = {
        "angebot": offer,
        "gesucht": gesucht
    };
    console.log(angebot);
    fetch("/createOffer", {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(angebot),
        credentials: 'include'
    }).then(response => {
        if (response.ok) {


            var angebotErstelltModal = Modal.getOrCreateInstance(document.getElementById('angebotErstelltModal'));
            state = 0;
            gesucht = [];
            offer = null;
            getMyCalendar();
            angebotErstelltModal.show();

        } else {
            if (response.status == 403) {
                alert("Du darfst höchstens 5 Angebote gleichzeitig haben!");
            } else {
                alert("Fehler beim Erstellen des Angebots.");
            }

        }
    });
});


function getMyCalendar() {
    var stateInfo = document.getElementById('stateInfo');
    let url = "/myKalender";
    if (state == 0) {
        stateInfo.style.visibility = "visible";
        stateInfo.innerText = "Wähle einen Termin, um ein Tauschangebot zu Erstellen oder wähle ein Angebot aus.";
    }

    if (state == 1) {
        url += "?title=" + (offer.title.split("(")[0].trim()) + "&terminid=" + offer.offerid;
        stateInfo.style.visibility = "visible";
        stateInfo.innerText = "Wähle die Termine aus, die du gerne hättest.";
    }

    fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            return response.json().then(data => {
                if (data.calendar) {
                    showCalendar(data.calendar, data);
                } else {
                    showCalendar(data);
                }
                localStorage.removeItem('uploadLocalCalendarIfNotExist');
                // SG-Abfrage nur direkt nach Login (nicht bei bereits eingeloggten Nutzern ohne Upload)
                if (localStorage.getItem('loggedIn') === 'true' && localStorage.getItem('justLoggedIn') === 'true') {
                    setTimeout(async () => {
                        try {
                            await checkAndPromptStudiengang();
                        } catch (e) {
                            console.debug('SG prompt after login failed', e);
                        } finally {
                            localStorage.removeItem('justLoggedIn');
                        }
                    }, 0);
                }
            });
        } else {
            if (document.getElementById('uploadHint') != null) {
                document.getElementById('uploadHint').style.display = "block";
            }

            if (localStorage.getItem('uploadLocalCalendarIfNotExist') === 'true') {
                localStorage.removeItem('uploadLocalCalendarIfNotExist');
                if (localStorage.getItem('tempCalendar') != null) {
                    localStorage.setItem('uploadLocalCalendar', "true");
                    checkCalendarAutoUpload();
                }
            }
        }


    }).catch(error => {
        console.error('Error:', error);
    });
}
if (localStorage.getItem('loggedIn') === 'true') {
    getMyCalendar();

}



function showIcalCalendar(parsed) {
    let items = [
        [], [], [], [], []
    ];

    for (let element in parsed) {
        if (parsed.hasOwnProperty(element)) {
            var parsedElement = parsed[element];
            if (parsedElement.type != "VEVENT") {
                continue;
            }
            var startDate = new Date(parsedElement.start);
            var endDate = new Date(parsedElement.end);
            var parsedStartDate = startDate.getHours() + ":" + (startDate.getMinutes() < 10 ? '0' : '') + startDate.getMinutes();
            var parsedEndDate = endDate.getHours() + ":" + (endDate.getMinutes() < 10 ? '0' : '') + endDate.getMinutes();
            var title = parsedElement.summary;
            try {
                var eventtype = title.match(/\(([^)]+)\)/)[1].split("-")[0];
                var event = {
                    title: title,
                    subtext: '',
                    color: 'lightgreen',
                    start: parsedStartDate,
                    end: parsedEndDate,
                };
                switch (eventtype) {
                    case "V":
                        event.color = '#4961E1';
                        break;
                    case "P":
                        event.color = '#F4A460';
                        break;
                    case "Ü":
                        event.color = '#32CD32';
                        break;
                    case "S":
                        event.color = '#556B2F';
                        break;
                    case "SU":
                        event.color = '#556B2F';
                        break;
                    case "T":
                        event.color = '#bbbbbbff';
                        break;
                    default:
                        event.color = 'grey';
                }

                var selectDay = startDate.getDay() - 1;
                items[selectDay].push(event);
            } catch (error) {
                localStorage.removeItem('tempCalendar');
                alert("Fehler beim Parsen des Kalenders");
                return;
            }
        }
    }
    window.scrollTo(0, 0);
    showCalendar(items);
}
var lastclicked = -1;

function updateSystemModeInfo(systemData) {
    const infoBox = document.getElementById('system-mode-info');
    if (!infoBox) return;

    if (!systemData || systemData.systemMode !== 'POOLED_3CYCLE') {
        infoBox.style.display = 'none';
        return;
    }

    infoBox.style.display = 'block';

    let scheduleText = '';
    if (systemData.scheduleType === 'INTERVAL_HOURS' && systemData.intervalHours) {
        scheduleText = `alle ${systemData.intervalHours} Stunden`;
    } else if (systemData.scheduleType === 'DAILY_FIXED' && systemData.dailyTime) {
        scheduleText = `täglich um ${systemData.dailyTime} Uhr`;
    } else {
        scheduleText = 'nach Zeitplan';
    }

    let nextRunText = '';
    if (systemData.nextRunAt) {
        const nextRun = new Date(systemData.nextRunAt);
        nextRunText = `<br><strong>Nächste Ziehung:</strong> ${nextRun.toLocaleString('de-DE')}`;
    }

    const infoText = document.getElementById('system-mode-info-text');
    if (infoText) {
        infoText.innerHTML = `
            <strong>3er-Zirkeltausch aktiv</strong><br>
            Angebote werden nicht mehr live angezeigt. Das System sammelt Wünsche und vermittelt ${scheduleText}.${nextRunText}<br>
            Bei der Vermittlung werden optimale 2er- und 3er-Tauschzyklen gebildet. 
            Du wirst per E-Mail benachrichtigt, wenn ein Tausch für dich gefunden wurde.
        `;
    }
}

function showCalendar(items, systemData) {
    var dayNames = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag'];
    const days = [{ 'Montag': [] }, { 'Dienstag': [] }, { 'Mittwoch': [] }, { 'Donnerstag': [] }, { 'Freitag': [] }];
    const startHour = 8;  // 8:00 Uhr
    const endHour = 21.2;   // 21:00 Uhr
    updateSystemModeInfo(systemData);

    const calendarEl = document.getElementById('weekCalendar');
    calendarEl.innerHTML = '';
    function timeToPosition(time) {
        const [hours, minutes] = time.split(':').map(Number);
        const totalHours = (hours - startHour) + (minutes / 60);
        var dayHeight = 650;
        var percent = 25 * 100 / dayHeight;
        var perc = ((1 - (totalHours / (endHour - startHour)) * 1) * percent);
        return ((totalHours / (endHour - startHour)) * 100) + perc;
    }

    function baseTitleOf(title) {
        try {
            return (title || '').split('(')[0].trim();
        } catch { return title || ''; }
    }
    function groupFromTitle(title) {
        try {
            const m = title.match(/\(([^)]+)\)/);
            if (!m) return null;
            const inside = m[1];
            const parts = inside.split('-');
            if (parts.length > 1 && parts[1] && parts[1].trim() !== '?') {
                return parts[1].trim();
            }
        } catch { }
        return null;
    }
    function displayTitleWithGroup(title) {
        const base = baseTitleOf(title);
        const g = groupFromTitle(title);
        return g ? `${base} (${g})` : base;
    }


    const timeScaleEl = document.createElement('div');
    timeScaleEl.classList.add('time-scale');
    for (let hour = startHour; hour <= endHour; hour++) {
        for (let minute = 0; minute < 60; minute += 60) {
            const timeDiv = document.createElement('div');
            timeDiv.textContent = `${Math.floor(hour)}:${String(0).padStart(2, '0')}`;
            timeDiv.style.top = `${timeToPosition(`${hour}:${minute}`)}%`;
            if (hour == endHour) continue;
            timeScaleEl.appendChild(timeDiv);
        }
    }
    calendarEl.appendChild(timeScaleEl);

    var count = -1;
    days.forEach(day => {
        const dayEl = document.createElement('div');
        dayEl.classList.add('day');
        const dayHeader = document.createElement('div');
        dayHeader.classList.add('day-header');
        dayHeader.textContent = dayNames[1 + count++];
        dayEl.appendChild(dayHeader);
        var samestart = 0;
        var currentstart = 0;
        var lastEnd = new Date(0, 0, 0, 0, 0);
        const renderedPlaceholders = new Set();
        const currentDay = (count);
        items[count].sort((a, b) => {
            return a.start.localeCompare(b.start);
        });

        const angefragtSlots = new Set();
        (items[count] || []).forEach((it) => {
            if (it && typeof it.subtext === 'string' && it.subtext.indexOf('ANGEFRAGT') !== -1) {
                angefragtSlots.add(it.start + '|' + it.end);
            }
        });

        (items[count] || []).forEach((item, index) => {
            const offerId = item.offerid;
            if (item.subtext.indexOf("OFFER") != -1) {
                const slotKey = item.start + '|' + item.end;
                if (angefragtSlots.has(slotKey)) {
                    return; // Nicht rendern, nicht DOM anfassen, nicht samestart verändern
                }
            }

            const itemEl = document.createElement('div');
            itemEl.classList.add('item');
            itemEl.style.backgroundColor = item.color;

            if (currentstart == item.start) {
                samestart++;
            } else {
                samestart = 0;
            }
            currentstart = item.start;

            // is item not 90 minutes long?
            let starttime = item.start.split(":");
            let endtime = item.end.split(":");
            let start = new Date(0, 0, 0, starttime[0], starttime[1]);
            let end = new Date(0, 0, 0, endtime[0], endtime[1]);
            let diff = Math.abs(end - start) / 1000 / 60;


            let isAllowed = diff == 90;
            let isUnderOther = false;

            // Platzhalter (graue Auswahlkarten) identifizieren
            const isPlaceholder = (item.color === 'rgba(227, 227, 227, 0.4)');
            // Overlay-Elemente (OFFER/ANGEFRAGT) und Platzhalter sollen die Overlap-Logik nicht beeinflussen
            const isOverlay = (item.subtext && item.subtext.length > 0);
            if (!isOverlay && !isPlaceholder) {
                if (start.getTime() < lastEnd.getTime()) {
                    isAllowed = false;
                    isUnderOther = true;
                } else {
                    lastEnd = end;
                }
            }

            if (item.subtext.indexOf("OFFER") != -1) {
                const shown = displayTitleWithGroup(item.title);
                itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(shown)}</strong><hr class="title-line"><div class="badge text-bg-danger smallbadge">${sanitizeHtml(item.subtext)}</div>`;

            } else if (item.subtext == "" && (item.title.indexOf("(P-") != -1 || item.title.indexOf("(Ü-") != -1 || item.title.indexOf("(S-") != -1 || item.title.indexOf("(SU-") != -1 || item.title.indexOf("(T-") != -1) && item.title.match(/\(([^)]+)\)/)[1].split("-")[1] != undefined) {
                try {
                    var praktikumtype = item.title.match(/\(([^)]+)\)/)[1].split("-")[1];
                    itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title.split(" ")[0])}</strong><hr class="title-line"><p style="text-align: center;font-size: 28px;opacity: 0.7;color:#808080">${praktikumtype}</p>`;
                } catch (error) {
                    itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title)}</strong><hr class="title-line"><div class="badge text-bg-secondary smallbadge">${sanitizeHtml(item.subtext)}</div>`;
                }



            } else {
                if (item.subtext && item.subtext.indexOf('ANGEFRAGT') !== -1) {
                    const shown = displayTitleWithGroup(item.title);
                    itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(shown)}</strong><hr class="title-line"><div class="badge text-bg-secondary smallbadge">${sanitizeHtml(item.subtext)}</div>`;
                }
                else if (item.subtext && item.subtext.indexOf('VORSCHLAG') !== -1) {
                    const shown = displayTitleWithGroup(item.title);
                    itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(shown)}</strong><hr class="title-line"><div class="badge text-bg-secondary smallbadge">VORSCHLAG</div>`;
                    itemEl.addEventListener('mouseenter', function () { itemEl.style.outline = '2px dashed rgba(0,0,0,0.25)'; });
                    itemEl.addEventListener('mouseleave', function () { itemEl.style.outline = (lastclicked == offerId ? '3px solid #FB6D48' : ''); });
                } else {
                    itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title.split(" ")[0])}</strong><hr class="title-line"><div class="badge text-bg-secondary smallbadge">${sanitizeHtml(item.subtext)}</div>`;
                }
            }


            itemEl.style.top = `${timeToPosition(item.start)}%`;
            itemEl.style.height = `${timeToPosition(item.end) - timeToPosition(item.start)}%`;
            itemEl.style.marginLeft = `${samestart * 15}px`;


            var opa = itemEl.style.opacity + "";
            itemEl.addEventListener("mouseenter", function () {
                itemEl.style.zIndex = 100;
                itemEl.classList.add("foreground");
            });
            itemEl.addEventListener("mouseleave", function () {
                itemEl.style.zIndex = 1;
                itemEl.classList.remove("foreground");

            });





            let isVorlesung = false;
            try {
                var eventtype = item.title.match(/\(([^)]+)\)/)[1].split("-")[0];
                isVorlesung = (eventtype == "V");
            } catch (error) { }
            if (isVorlesung || item.subtext.indexOf("ANGEFRAGT") != -1 || !isAllowed) {
                itemEl.style.cursor = "not-allowed";
                if (loggedIn) {
                    var deletebtn = document.createElement('button');
                    deletebtn.style.position = "absolute";
                    deletebtn.style.right = "2px";
                    deletebtn.innerText = "X";
                    deletebtn.style.width = "18px";
                    deletebtn.style.height = "18px";
                    deletebtn.style.top = "2px";
                    deletebtn.style.borderRadius = "25%";
                    deletebtn.setAttribute('offerid', offerId);
                    deletebtn.style.backgroundColor = "rgb(255, 0, 0)";
                    deletebtn.style.fontSize = "10px";
                    deletebtn.title = "Termin löschen";
                    deletebtn.style.border = "none";

                    deletebtn.addEventListener('click', function () {
                        var id = deletebtn.getAttribute('offerid');

                        if (!confirm("Willst du den Termin wirklich löschen?")) {
                            return;
                        }
                        fetch("/removeTermin?terminid=" + id, {
                            method: 'GET',
                            credentials: 'include'
                        }).then(response => {
                            if (response.ok) {
                                getMyCalendar();
                                showMessage("Termin gelöscht", "Der Termin wurde erfolgreich gelöscht.", "success");
                            } else {
                                alert("Fehler beim Löschen des Termins");
                            }
                        });
                    });

                    itemEl.appendChild(deletebtn);
                }
            }
            if (state == 1) {
                itemEl.style.opacity = "0.3";
            }


            if (item.subtext.indexOf("ANGEFRAGT") != -1 || item.subtext.indexOf("VORSCHLAG") != -1) {
                const isVorschlag = item.subtext.indexOf("VORSCHLAG") !== -1;
                const isAngefragt = !isVorschlag;

                if (samestart == 0) {
                    itemEl.style.opacity = "0.6";
                    if (state == 1 && isVorschlag) {
                        itemEl.style.opacity = "1";
                    }
                } else {
                    itemEl.style.opacity = "";
                }

                if (isAngefragt) {
                    itemEl.style.cursor = "not-allowed";
                    itemEl.disabled = true;
                } else {
                    itemEl.style.cursor = "pointer";
                    itemEl.disabled = false;
                }
            }
            if (item.color == "rgba(227, 227, 227, 0.4)") {
                itemEl.style.opacity = "0.9";
            }

            if (item.subtext.indexOf("OFFER") != -1) {
                itemEl.style.fontSize = "10px";
            }
            if (lastclicked == offerId && lastclicked != -1) {
                itemEl.style.outline = "3px solid #FB6D48";
                itemEl.style.opacity = "1";
            }
            if (!isAllowed) {
                itemEl.title = "Das ist kein Standardtermin. Er kann leider nicht über diese Plattform getauscht werden.";
                itemEl.addEventListener("click", function () {
                    showMessage("Nicht tauschbar", "Das ist kein Standardtermin. Er kann zurzeit leider nicht über diese Plattform getauscht werden.", "warning");
                });
            }
            itemEl.addEventListener('mousedown', function () {
                if (item.subtext.indexOf("ANGEFRAGT") != -1) {
                    return;
                }
                if (!isAllowed) {
                    return;
                }

                if (localStorage.getItem('loggedIn') !== 'true') {
                    document.getElementById('loginModalTitle').innerText = "Anmeldung erforderlich";
                    var instance = Modal.getOrCreateInstance(document.getElementById('loginModal'));
                    instance.show();
                    return;
                }

                var curday = dayNames[1 + count];
                try {
                    var eventtype = item.title.match(/\(([^)]+)\)/)[1].split("-")[0];
                    if (eventtype == "V") {

                        return;
                    }


                } catch (error) {

                }
                if (item.subtext.includes("OFFER") && item.subtext.indexOf("ANGEFRAGT") == -1) {
                    if (confirm("Willst du das Angebot annehmen?")) {
                        const url = "/acceptOffer?selectedTermin=" + offerId;
                        fetch(url, {
                            method: 'GET',
                            credentials: 'include'
                        }).then(response => {
                            if (response.ok) {
                                state = 0;
                                offer = null;
                                gesucht = [];
                                getMyCalendar();
                                var instance = Modal.getOrCreateInstance(document.getElementById('exampleModal'));
                                instance.show();
                                var fakeprogress = document.getElementById('fakeprogress');
                                var percent = 0;
                                var interval = setInterval(function () {
                                    percent += 1;
                                    fakeprogress.style.width = percent + "%";
                                    fakeprogress.textContent = percent + "%";
                                    if (percent >= 100) {
                                        clearInterval(interval);

                                        response.text().then(data => {
                                            document.getElementById('modalbody').innerText = data;
                                        });
                                    }
                                }, 75);



                            } else {
                                alert("Fehler beim Annehmen des Angebots");
                            }
                        });

                    }
                    return;
                } else if (state == 1 && item.subtext && item.subtext.indexOf('VORSCHLAG') !== -1) {
                    // Ghost-Vorschläge: wie auswählbare Ziel-Slots behandeln (Toggle)
                    if (itemEl.style.border == "3px solid rgb(13, 92, 16)") {
                        itemEl.style.border = "";
                        gesucht.forEach((element, index) => {
                            if (element.day == currentDay && element.start == item.start && element.end == item.end) {
                                gesucht.splice(index, 1);
                            }
                        });
                    } else {
                        itemEl.style.border = "3px solid rgb(13, 92, 16)";
                        gesucht.push({
                            title: offer ? offer.title : item.title,
                            subtext: '',
                            color: 'rgba(227, 227, 227, 0.4)',
                            start: item.start,
                            end: item.end,
                            day: currentDay
                        });
                    }
                    if (gesucht.length > 0) {
                        document.getElementById('confirmOffer').disabled = false;
                    } else {
                        document.getElementById('confirmOffer').disabled = true;
                    }
                    return;
                } else if (state == 1 && item.title.indexOf("(") != -1) {
                    if (lastclicked == offerId) {
                        state = 0;
                        lastclicked = -1;
                        itemEl.style.outline = "";
                        document.getElementById('confirmOffer').disabled = true;
                        gesucht = [];
                        offer = null;
                        getMyCalendar();
                        return;
                    }



                    state = 1;
                    gesucht = [];
                    offer = {
                        offerid: offerId,
                        title: item.title,
                        subtext: item.subtext,
                        color: item.color,
                        start: item.start,
                        end: item.end,
                        day: currentDay
                    };
                    lastclicked = offerId;

                    itemEl.style.outline = "3px solid #FB6D48";
                    getMyCalendar();
                    return;
                }

                if (state == 0) {
                    state = 1;
                    lastclicked = offerId;
                    itemEl.style.outline = "3px solid #FB6D48";
                    document.getElementById('confirmOffer').disabled = "true";
                    offer = {
                        offerid: offerId,
                        title: item.title,
                        subtext: item.subtext,
                        color: item.color,
                        start: item.start,
                        end: item.end,
                        day: currentDay
                    };
                    getMyCalendar();

                } else if (state == 1) {


                    if (itemEl.style.border == "3px solid rgb(13, 92, 16)") {
                        itemEl.style.border = "";
                        gesucht.forEach((element, index) => {
                            if (element.day == currentDay && element.start == item.start && element.end == item.end) {
                                gesucht.splice(index, 1);
                            }
                        });
                    } else {
                        itemEl.style.border = "3px solid rgb(13, 92, 16)";

                        gesucht.push({
                            title: offer.title,
                            subtext: item.subtext,
                            color: item.color,
                            start: item.start,
                            end: item.end,
                            day: currentDay
                        });
                    }
                    console.log(gesucht);

                    if (gesucht.length > 0) {
                        document.getElementById('confirmOffer').disabled = false;
                    } else {
                        document.getElementById('confirmOffer').disabled = true;
                    }

                }

            });
            // In State 1 (Auswahl) nur Offers der ausgewählten Veranstaltung anzeigen
            if (state == 1 && item.subtext && item.subtext.indexOf('OFFER') !== -1 && offer && offer.title) {
                try {
                    const selectedBase = offer.title.split("(")[0].trim();
                    const itemBase = item.title.split("(")[0].trim();
                    if (selectedBase !== itemBase) {
                        const slotKey = currentDay + '|' + item.start + '|' + item.end;
                        if (!renderedPlaceholders.has(slotKey)) {
                            const phEl = document.createElement('div');
                            phEl.classList.add('item');
                            phEl.style.backgroundColor = 'rgba(227, 227, 227, 0.4)';
                            phEl.innerHTML = `<strong class="item-title">${sanitizeHtml(selectedBase.split(" ")[0])}</strong><hr class="title-line"><div class="badge text-bg-secondary smallbadge"></div>`;
                            phEl.style.top = `${timeToPosition(item.start)}%`;
                            phEl.style.height = `${timeToPosition(item.end) - timeToPosition(item.start)}%`;
                            phEl.style.marginLeft = `${samestart * 15}px`;
                            phEl.addEventListener("mouseenter", function () {
                                phEl.style.zIndex = 100;
                                phEl.classList.add("foreground");
                            });
                            phEl.addEventListener("mouseleave", function () {
                                phEl.style.zIndex = 1;
                                phEl.classList.remove("foreground");
                            });
                            phEl.addEventListener('mousedown', function () {
                                if (!isAllowed) return;
                                if (localStorage.getItem('loggedIn') !== 'true') {
                                    document.getElementById('loginModalTitle').innerText = "Anmeldung erforderlich";
                                    var instance = Modal.getOrCreateInstance(document.getElementById('loginModal'));
                                    instance.show();
                                    return;
                                }
                                if (phEl.style.border == "3px solid rgb(13, 92, 16)") {
                                    phEl.style.border = "";
                                    gesucht.forEach((element, index) => {
                                        if (element.day == currentDay && element.start == item.start && element.end == item.end) {
                                            gesucht.splice(index, 1);
                                        }
                                    });
                                } else {
                                    phEl.style.border = "3px solid rgb(13, 92, 16)";
                                    gesucht.push({
                                        title: offer.title,
                                        subtext: '',
                                        color: 'rgba(227, 227, 227, 0.4)',
                                        start: item.start,
                                        end: item.end,
                                        day: currentDay
                                    });
                                }
                                if (gesucht.length > 0) {
                                    document.getElementById('confirmOffer').disabled = false;
                                } else {
                                    document.getElementById('confirmOffer').disabled = true;
                                }
                            });
                            dayEl.appendChild(phEl);
                            renderedPlaceholders.add(slotKey);
                        }
                        return;
                    }
                } catch (e) { }
            }

            const isOffer = (item.subtext && item.subtext.indexOf('OFFER') !== -1);
            const isAngefragt = (item.subtext && item.subtext.indexOf('ANGEFRAGT') !== -1);
            if (!isUnderOther || isOffer || isPlaceholder || isAngefragt) {
                dayEl.appendChild(itemEl);
            }
        });

        calendarEl.appendChild(dayEl);
    });
    resizeDayHeaders();
}
const whoamiurl = "/whoami";
var loggedIn = localStorage.getItem('loggedIn') === 'true';
var whoamidata = "";


function manageVisibility() {
    var isAdmin = localStorage.getItem('isAdmin') === 'true';

    if (loggedIn) {
        loggedIn = true;
        document.getElementById('fileupload').disabled = false;
        document.getElementById('removeAllOvers').style.visibility = "visible";

        document.getElementById('loginshowbtntext').innerText = "Ausloggen";

        if (document.getElementById('loginshowbtn-mobile')) {
            document.getElementById('loginshowbtn-mobile').style.display = 'none';
        }
        if (document.getElementById('loginshowbtn-menu-mobile')) {
            document.getElementById('loginshowbtn-menu-mobile').style.display = 'block';
        }

        // Admin-Link anzeigen/verstecken
        if (document.getElementById('admin-link')) {
            document.getElementById('admin-link').style.display = isAdmin ? 'block' : 'none';
        }

        document.getElementById('feedbackbtn').style.display = "inline";
        document.getElementById('privateMailBox').style.display = "block";
        document.getElementById('confirmOffer').style.visibility = "";

        if (sgSettingsBox) sgSettingsBox.style.display = '';
        initUserStudiengangUI().catch(err => console.debug('SG init failed', err));
        if (document.getElementById('createPasskey')) {
            document.getElementById('createPasskey').style.display = 'inline-block';
        }
        if (document.getElementById('managePasskeysBtn')) {
            document.getElementById('managePasskeysBtn').style.display = 'inline-block';
        }

        if (localStorage.getItem('evaluation_login_redirect') === 'true') {
            localStorage.removeItem('evaluation_login_redirect');
            window.location.href = '/evaluation.html';
        }

    } else {
        document.getElementById('title').innerText = "Wochenkalender (Nicht eingeloggt)";
        document.getElementById('removeAllOvers').style.visibility = "hidden";
        document.getElementById('confirmOffer').style.visibility = "hidden";

        document.getElementById('loginshowbtntext').innerText = "Anmelden";

        if (document.getElementById('loginshowbtn-mobile')) {
            document.getElementById('loginshowbtn-mobile').style.display = 'inline-block';
        }
        if (document.getElementById('loginshowbtn-menu-mobile')) {
            document.getElementById('loginshowbtn-menu-mobile').style.display = 'none';
        }

        // Admin-Link verstecken
        if (document.getElementById('admin-link')) {
            document.getElementById('admin-link').style.display = 'none';
        }

        if (document.getElementById('uploadHint') != null) {
            document.getElementById('uploadHint').style.display = "block";
        }
        hidePasskeyManager();
        if (document.getElementById('createPasskey')) {
            document.getElementById('createPasskey').style.display = 'none';
        }
        if (document.getElementById('managePasskeysBtn')) {
            document.getElementById('managePasskeysBtn').style.display = 'none';
        }
        if (sgSettingsBox) sgSettingsBox.style.display = 'none';
    }
}
manageVisibility();


fetch(whoamiurl, {
    method: 'GET',
    credentials: 'include'
}).then(response => {
    if (response.ok) {
        response.json().then(data => {
            if (data.hsMail && data.hsMail.includes("student.hs-rm.de")) {
                if (localStorage.getItem('loggedIn') == null) {
                    localStorage.setItem('loggedIn', "true");
                    localStorage.setItem('uploadLocalCalendarIfNotExist', "true");
                    getMyCalendar();
                }
                loggedIn = true;
                isLoggedIn = true;
                whoamidata = data.hsMail;
                document.getElementById('title').innerText = "Wochenkalender für " + extractName(whoamidata);
                localStorage.setItem('whoami', extractName(whoamidata));
                localStorage.setItem('isAdmin', data.isAdmin === true ? 'true' : 'false');
                manageVisibility();

            } else {
                if (logintoken != null) {
                    loggedIn = false;
                    manageVisibility();
                    return;
                }
                localStorage.removeItem('loggedIn');
                localStorage.removeItem('isAdmin');
                loggedIn = false;
                manageVisibility();
            }
        });
    }
});

function extractName(adress) {
    let namensTeil = adress.split("@")[0];
    let name = namensTeil.split(".");
    var givenName = name[0];
    givenName = givenName.charAt(0).toUpperCase() + givenName.slice(1);
    var familyName = name[1];
    familyName = familyName.charAt(0).toUpperCase() + familyName.slice(1);

    let parsedName = givenName + " " + familyName;
    return parsedName;
}

// Auto Calendar Upload after Registration

async function checkCalendarAutoUpload() {
    if (localStorage.getItem('tempCalendar') != null && localStorage.getItem('loggedIn') !== 'true') {
        showIcalCalendar(ical.parseICS(localStorage.getItem('tempCalendar')));
    } else if (localStorage.getItem('uploadLocalCalendar') === 'true' && localStorage.getItem('tempCalendar') != null) {
        // Sicherstellen, dass ggf. fehlender Studiengang zuerst gesetzt wird (Gating im Upload)
        try {
            const sg = await getMyStudiengang();
            if (!sg || !sg.id) {
                localStorage.setItem('uploadAfterStudiengang', 'true');
                try { await checkAndPromptStudiengang(); } catch { }
                return;
            }
        } catch { }
        fetch("/uploadKalender", {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain;charset=UTF-8'
            },
            body: localStorage.getItem('tempCalendar'),
            credentials: 'include'
        }).then(response => {
            if (response.ok) {
                localStorage.removeItem('uploadLocalCalendar');
                localStorage.removeItem('tempCalendar');
                alert("Dein Temporärer Kalender wurde erfolgreich hochgeladen");
                getMyCalendar();
            } else {
                alert("Fehler beim Hochladen des Kalenders");
            }
        });
    }
}

checkCalendarAutoUpload();

// Darkmode
var darkmodeActive = localStorage.getItem('darkmode') === 'true' || localStorage.getItem('darkmode') === null;

document.getElementsByTagName('html')[0].setAttribute('data-bs-theme', darkmodeActive ? 'dark' : 'light');
var darkswitch = document.getElementById('flexSwitchCheckDefault');
darkswitch.checked = darkmodeActive;

darkswitch.addEventListener('click', function () {
    localStorage.setItem('darkmode', darkswitch.checked);
    document.getElementsByTagName('html')[0].setAttribute('data-bs-theme', darkswitch.checked ? 'dark' : 'light');

});
// Size of Day Header depending on screen width


function resizeDayHeaders() {
    var largeDayHeaders = ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag"];
    var smallDayHeaders = ["Mo", "Di", "Mi", "Do", "Fr"];
    var dayHeaders = document.querySelectorAll('.day-header');
    for (var i = 0; i < dayHeaders.length; i++) {
        if (window.innerWidth < 768) {
            dayHeaders[i].innerText = smallDayHeaders[i];
        } else {
            dayHeaders[i].innerText = largeDayHeaders[i];
        }
    }
}

window.addEventListener('resize', function () {
    resizeDayHeaders();
});
resizeDayHeaders();

// Private Mail Update
var mailUpdate = document.getElementById('private-mail');
var mailUpdateBtn = document.getElementById('save-private-mail');
mailUpdate.addEventListener('input', function () {
    // is the input valid?
    var mail = mailUpdate.value;

    if (mail.length >= 1) {
        mailUpdateBtn.innerText = "Speichern";
    } else {
        mailUpdateBtn.innerText = "Löschen";
    }

    if (mail.indexOf("@") > -1) {
        mailUpdateBtn.removeAttribute("disabled");
    } else {
        mailUpdateBtn.disabled = true;
    }

});
mailUpdate.addEventListener("change", function () {
    mailUpdate.dispatchEvent(new MouseEvent('input'));
});

// updatePrivateMail Endpoint. Get, Parameter is privateMail
mailUpdateBtn.addEventListener('click', function () {
    var mail = mailUpdate.value;
    if (mail.includes("student.hs-rm.de")) {
        showMessage("E-Mail nicht aktualisiert", "Du darfst keine Hochschul-E-Mail verwenden");
        mailUpdateBtn.innerText = "Löschen";
        mailUpdate.value = "";
        return;
    }
    let url = "/updatePrivateMail";
    if (mail.length >= 1) {
        url = url + "?privateMail=" + mail;
    }

    fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            showMessage("E-Mail aktualisiert", "Deine private E-Mail wurde aktualisiert");
            mailUpdate.value = "";
            mailUpdateBtn.innerText = "Löschen";

        } else {
            alert("Fehler beim Speichern der E-Mail");
        }
    });
});
var demoLogin = document.getElementById('demoLogin');
var demoLoginInput = document.getElementById('demoLoginInput');
var betaLoginBox = document.getElementById('betaLoginBox');
if (import.meta.env.DEV) {
    demoLogin.style.display = "block";
    demoLoginInput.style.display = "block";
    betaLoginBox.style.display = "block";
} else {
    demoLogin.style.display = "none";
    demoLoginInput.style.display = "none";
    betaLoginBox.style.display = "none";
}
demoLogin.addEventListener('click', function () {
    if (demoLoginInput.value == "" || demoLoginInput.value == null || demoLoginInput.value > 100 || demoLoginInput.value < 1) {
        alert("Die Nummer muss zwischen 1 und 100 liegen");
        return;
    }

    fetch("/betaLogin?number=" + demoLoginInput.value, {
        method: 'GET',
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            localStorage.setItem('loggedIn', "true");
            window.location.href = "/";
        } else {
            alert("Fehler beim Einloggen");
        }
    });
});

// Login-Button Event Handler (gemeinsame Funktion)
function handleLoginButtonClick() {
    if (localStorage.getItem('loggedIn') === 'true') {
        logout();
    } else {
        var instance = Modal.getOrCreateInstance(document.getElementById('loginModal'));
        instance.show();
        setTimeout(async function () {
            const emailInput = document.getElementById('email');

            if (await supportsConditionalPasskeys()) {
                if (emailInput) {
                    emailInput.focus({ preventScroll: true });
                }
                return;
            }

            if (window.PublicKeyCredential && typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable === 'function') {
                try {
                    const uvpaAvailable = await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
                    if (uvpaAvailable) {
                        const doc = document.getElementById('passkey-login-button');
                        if (doc) {
                            doc.focus();
                            return;
                        }
                    }
                } catch (error) {
                    console.debug('UVPA-Check fehlgeschlagen', error);
                }
            }

            if (emailInput) {
                emailInput.focus({ preventScroll: true });
            }
        }, 125);
    }
}

// Event Listener für Desktop-Button
var loginshowbtn = document.getElementById('loginshowbtn');
if (loginshowbtn) {
    loginshowbtn.addEventListener('mousedown', handleLoginButtonClick);
}

// Event Listener für Mobile-Button (Anmelden - außerhalb Menü)
var loginshowbtnMobile = document.getElementById('loginshowbtn-mobile');
if (loginshowbtnMobile) {
    loginshowbtnMobile.addEventListener('click', handleLoginButtonClick);
}

// Event Listener für Mobile-Button (Ausloggen - im Menü)
var loginshowbtnMenuMobile = document.getElementById('loginshowbtn-menu-mobile');
if (loginshowbtnMenuMobile) {
    loginshowbtnMenuMobile.addEventListener('click', handleLoginButtonClick);
}

var loginModalElement = document.getElementById('loginModal');
if (loginModalElement) {
    loginModalElement.addEventListener('hidden.bs.modal', function () {
        cancelConditionalPasskeyRequest();
    });
}

if (localStorage.getItem('evaluation_login_redirect') === 'true') {
    setTimeout(function () {
        if (localStorage.getItem('loggedIn') !== 'true') {
            var instance = Modal.getOrCreateInstance(document.getElementById('loginModal'));
            instance.show();
        } else {
            localStorage.removeItem('evaluation_login_redirect');
            window.location.href = '/evaluation.html';
        }
    }, 500);
}

function logout(all = false) {
    let url = "/logmeout";
    if (all) {
        url += "?all=true";
    }
    if (loginshowbtn) loginshowbtn.disabled = true;
    if (loginshowbtnMobile) loginshowbtnMobile.disabled = true;
    if (loginshowbtnMenuMobile) loginshowbtnMenuMobile.disabled = true;

    fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            if (all) {
                alert("Alle anderen Sitzungen wurden erfolgreich beendet");
            } else {
                localStorage.removeItem('tempCalendar');
                localStorage.removeItem('loggedIn');
                localStorage.removeItem('whoami');
                localStorage.removeItem('isAdmin');
                hidePasskeyManager();
                showMessage("Erfolgreich ausgeloggt", "Du wurdest erfolgreich ausgeloggt");

                if (import.meta.env.DEV) {
                    window.location.href = "/";
                } else {
                    setTimeout(function () {
                        window.location.href = "/";
                    }, 2500);
                }
            }

        } else {
            if (loginshowbtn) loginshowbtn.removeAttribute("disabled");
            if (loginshowbtnMobile) loginshowbtnMobile.removeAttribute("disabled");
            if (loginshowbtnMenuMobile) loginshowbtnMenuMobile.removeAttribute("disabled");
            showMessage("Fehler beim Ausloggen", "Fehler beim Ausloggen. Bitte versuche es erneut");
        }
    });

}


var sharebtn = document.getElementById('sharebtn');
if (!navigator.share) {
    sharebtn.style.display = "none";
}

window.addEventListener('load', () => {
    if (localStorage.getItem('loggedIn') !== 'true') {

        document.querySelector('#pow-login').configure({
            strings: {
                label: 'Prüfe auf Roboter...',
                verified: "Prüfung erfolgreich",
                verifying: "Prüfe auf Roboter...",
                error: "Fehler. Roboter erkannt!",

            },
        });
    }
    // Falls ein zurückgehaltener Upload vorhanden ist und Benutzer eingeloggt ist, sofort SG-Abfrage anzeigen
    try {
        if (localStorage.getItem('loggedIn') === 'true' && localStorage.getItem('uploadAfterStudiengang') === 'true' && localStorage.getItem('tempCalendar')) {
            setTimeout(async () => {
                try { await checkAndPromptStudiengang(); } catch { }
            }, 0);
        }
    } catch { }
});
document.querySelector('#pow-login').addEventListener('statechange', (ev) => {
    if (ev.detail.state === 'verified') {
        powverified = true;
        powpayload = ev.detail.payload;
        submitemail.removeAttribute("disabled");
    }
});
var isLoggedIn = localStorage.getItem('loggedIn');
var stop = false;
setInterval(() => {
    if (localStorage.getItem('loggedIn') === 'true' && !isLoggedIn && !stop) {
        stop = true;
        window.location.reload();
    } else if (localStorage.getItem('loggedIn') == null && isLoggedIn && !stop) {
        stop = true;
        window.location.reload();
    }
}, 1000);

async function createPasskey() {
    const registerButton = document.getElementById('register-passkey');
    if (registerButton) {
        registerButton.disabled = true;
    }

    try {
        // Registrierungsoptionen vom Server abrufen
        const optionsUrl = "/webauthn/register/options";
        const csrfoptions = await getCsrfToken();

        const optionsResponse = await fetch(optionsUrl, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                [csrfoptions.headerName]: csrfoptions.token
            }
        });
        if (!optionsResponse.ok) {
            throw new Error("Fehler beim Abrufen der Registrierungsoptionen");
        }
        const options = await optionsResponse.json();

        // Base64url-kodierte Werte dekodieren (Challenge, user.id, excludeCredentials[].id)
        options.challenge = base64urlToBuffer(options.challenge);
        options.user.id = new Uint8Array(base64urlToBuffer(options.user.id));
        if (options.excludeCredentials) {
            options.excludeCredentials = options.excludeCredentials.map(cred => {
                cred.id = new Uint8Array(base64urlToBuffer(cred.id));
                return cred;
            });
        }

        // Credential erstellen
        const credential = await navigator.credentials.create({ publicKey: options });

        // Das Credential-Objekt transformieren, sodass ArrayBuffers in base64url-kodierte Strings umgewandelt werden
        const credentialData = transformCredential(credential);

        const userProvidedLabel = passkeyLabelInput ? passkeyLabelInput.value.trim() : "";
        const label = userProvidedLabel.length > 0 ? userProvidedLabel : "Passkey";

        // Payload zusammenstellen: publicKey-Objekt mit dem Credential und dem Label
        const payload = {
            publicKey: {
                credential: credentialData,
                label: label
            }
        };

        // Registrierungsdaten an den Server schicken
        const registerUrl = "/webauthn/register";


        const csrfregister = await getCsrfToken();
        const registerResponse = await fetch(registerUrl, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfregister.headerName]: csrfregister.token
            },
            credentials: 'include',
            body: JSON.stringify(payload)
        });
        if (!registerResponse.ok) {
            throw new Error("Fehler beim Erstellen des Passkeys");
        }
        showMessage("Passkey erstellt", "Dein Passkey wurde erfolgreich erstellt und kann jetzt verwendet werden.");
        var instance = Modal.getOrCreateInstance(document.getElementById('passkey-modal'), {
            backdrop: 'static',
            keyboard: false
        });
        instance.hide();
        if (passkeyLabelInput) {
            passkeyLabelInput.value = "";
        }
        await loadPasskeys();

    } catch (error) {
        console.error("Error during passkey registration:", error);
        if (error?.name === 'NotAllowedError') {
            showMessage("Vorgang abgebrochen", "Die Passkey-Erstellung wurde abgebrochen.");
        } else {
            showMessage("Fehler", "Passkey konnte nicht erstellt werden. Prüfe die Geräteeinstellungen oder versuche es später erneut.");
        }
    } finally {
        if (registerButton) {
            registerButton.disabled = false;
        }
    }
}


const createPasskeyButton = document.getElementById('createPasskey');
if (createPasskeyButton) {
    createPasskeyButton.addEventListener('click', function () {
        showPasskeyModal();
    });
}


// Hilfsfunktion: base64url-kodierten String in ArrayBuffer umwandeln
function base64urlToBuffer(base64urlString) {
    const padding = '='.repeat((4 - (base64urlString.length % 4)) % 4);
    const base64 = (base64urlString + padding)
        .replace(/-/g, '+')
        .replace(/_/g, '/');
    const binary = atob(base64);
    const buffer = new ArrayBuffer(binary.length);
    const view = new Uint8Array(buffer);
    for (let i = 0; i < binary.length; i++) {
        view[i] = binary.charCodeAt(i);
    }
    return buffer;
}

// Hilfsfunktion: Wandelt das Credential-Objekt in ein JSON-kompatibles Format um
function transformCredential(credential) {
    const attestationObject = credential.response.attestationObject;
    const clientDataJSON = credential.response.clientDataJSON;
    return {
        id: credential.id,
        rawId: bufferToBase64url(new Uint8Array(credential.rawId)),
        type: credential.type,
        response: {
            attestationObject: bufferToBase64url(new Uint8Array(attestationObject)),
            clientDataJSON: bufferToBase64url(new Uint8Array(clientDataJSON))
        },
        clientExtensionResults: credential.getClientExtensionResults
            ? credential.getClientExtensionResults()
            : {}
    };
}


// Hilfsfunktion: Wandelt einen Uint8Array in einen base64url-kodierten String um
function bufferToBase64url(buffer) {
    let binary = "";
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    let base64 = btoa(binary);
    return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

async function getCsrfToken() {
    const response = await fetch('/api/csrf-token', {
        credentials: 'same-origin' // wichtig für Session-Cookies
    });
    return await response.json();
}





async function getAuthOptions() {
    const csrf = await getCsrfToken();

    const authOptionsUrl = "/webauthn/authenticate/options";
    const response = await fetch(authOptionsUrl, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token
        },
        body: ""
    });

    if (!response.ok) {
        console.log("Keine Passkey-Optionen erhalten oder Fehler bei der Anfrage");
        return;
    }

    const options = await response.json();

    // Jetzt die base64url-kodierten Binärwerte dekodieren
    options.challenge = base64urlToBuffer(options.challenge);
    if (options.allowCredentials) {
        options.allowCredentials = options.allowCredentials.map(cred => {
            cred.id = base64urlToBuffer(cred.id);
            return cred;
        });
    }



    return options;
}

let conditionalPasskeySupport = null;
let conditionalPasskeyRequest = null;
let conditionalPasskeyAbortController = null;

async function supportsConditionalPasskeys() {
    if (!window.PublicKeyCredential || !PublicKeyCredential.isConditionalMediationAvailable) {
        return false;
    }
    if (conditionalPasskeySupport !== null) {
        return conditionalPasskeySupport;
    }
    try {
        conditionalPasskeySupport = await PublicKeyCredential.isConditionalMediationAvailable();
    } catch (error) {
        console.debug('Prüfung auf Passkey-Autofill-Unterstützung fehlgeschlagen', error);
        conditionalPasskeySupport = false;
    }
    return conditionalPasskeySupport;
}

function cancelConditionalPasskeyRequest() {
    if (conditionalPasskeyAbortController) {
        conditionalPasskeyAbortController.abort();
    }
    conditionalPasskeyAbortController = null;
    conditionalPasskeyRequest = null;
}

async function ensureConditionalPasskeyAutofill() {
    if (conditionalPasskeyRequest) {
        return;
    }
    if (!(await supportsConditionalPasskeys())) {
        return;
    }

    const options = await getAuthOptions();
    if (!options) {
        return;
    }

    conditionalPasskeyAbortController = new AbortController();
    conditionalPasskeyRequest = navigator.credentials.get({
        publicKey: options,
        mediation: 'conditional',
        signal: conditionalPasskeyAbortController.signal
    });

    conditionalPasskeyRequest.then(credential => {
        conditionalPasskeyAbortController = null;
        conditionalPasskeyRequest = null;
        if (!credential) {
            return;
        }
        const assertionData = transformAssertion(credential);
        handlePasskeyLogin(assertionData).catch(error => {
            console.error('Fehler beim Verarbeiten der Passkey-Antwort:', error);
        });
    }).catch(error => {
        conditionalPasskeyAbortController = null;
        conditionalPasskeyRequest = null;
        if (error?.name === 'AbortError' || error?.name === 'NotAllowedError') {
            console.debug('Passkey-Autofill abgebrochen oder nicht genutzt', error);
            return;
        }
        console.error('Fehler bei der Passkey-Autofill-Anmeldung:', error);
    });
}

document.getElementById('passkey-login-button').addEventListener('click', async function () {
    try {
        cancelConditionalPasskeyRequest();
        const assertion = await navigator.credentials.get({ publicKey: await getAuthOptions() });
        const assertionData = transformAssertion(assertion);
        handlePasskeyLogin(assertionData);

    } catch (error) {
        console.error("Fehler während der Passkey-Authentifizierung:", error);
    }
});

async function handlePasskeyLogin(assertionData) {
    const loginUrl = "/login/webauthn";

    const logincsrf = await getCsrfToken();

    const loginResponse = await fetch(loginUrl, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            [logincsrf.headerName]: logincsrf.token
        },
        body: JSON.stringify(assertionData)
    });

    if (!loginResponse.ok) {
        alert("Passkey-Authentifizierung fehlgeschlagen");
        return;
    }

    const result = await loginResponse.json();
    if (result.authenticated) {
        console.log(result);
        window.location.href = result.redirectUrl || "/";
    } else {
        alert("Passkey-Authentifizierung fehlgeschlagen");
    }
}


function transformAssertion(assertion) {
    return {
        id: assertion.id,
        rawId: bufferToBase64url(assertion.rawId),
        type: assertion.type,
        response: {
            authenticatorData: bufferToBase64url(assertion.response.authenticatorData),
            clientDataJSON: bufferToBase64url(assertion.response.clientDataJSON),
            signature: bufferToBase64url(assertion.response.signature),
            userHandle: assertion.response.userHandle
                ? bufferToBase64url(assertion.response.userHandle)
                : null
        },
        clientExtensionResults: assertion.getClientExtensionResults
            ? assertion.getClientExtensionResults()
            : {}
    };
}

function showPasskeyModal() {
    if (window.PublicKeyCredential && PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable) {
        var instance = Modal.getOrCreateInstance(document.getElementById('passkey-modal'), {
            backdrop: 'static',
            keyboard: false
        });
        instance.show();
        const registerPasskeybtn = document.getElementById('register-passkey');
        if (registerPasskeybtn) {
            registerPasskeybtn.disabled = false;
            registerPasskeybtn.onclick = async function () {
                await createPasskey();
            };
        }
        if (passkeyLabelInput) {
            passkeyLabelInput.value = "";
        }
        window.history.replaceState({}, document.title, "/");
    }
}

if (window.location.search.includes("pk")) {
    showPasskeyModal();
}

// ===================== Studiengang: Helpers =====================
async function fetchStudiengaengeList() {
    if (cachedStudiengaengeListe !== null) {
        return cachedStudiengaengeListe;
    }

    if (studiengaengeListPromise !== null) {
        return studiengaengeListPromise;
    }

    studiengaengeListPromise = fetch('/api/user/studiengang/list', { credentials: 'include' })
        .then(async resp => {
            if (resp.ok) {
                const data = await resp.json();
                cachedStudiengaengeListe = data;
                studiengaengeListPromise = null;
                return data;
            }
            studiengaengeListPromise = null;
            return [];
        })
        .catch(() => {
            studiengaengeListPromise = null;
            return [];
        });

    return studiengaengeListPromise;
}

async function getMyStudiengang() {
    if (cachedStudiengang !== null) {
        return cachedStudiengang;
    }
    const resp = await fetch('/api/user/studiengang', { credentials: 'include' });
    if (!resp.ok) return null;
    const data = await resp.json();
    cachedStudiengang = data;
    return cachedStudiengang;
}

async function setMyStudiengang(studiengangId) {
    const resp = await fetch('/api/user/studiengang', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, credentials: 'include', body: JSON.stringify({ studiengangId }) });
    if (resp.ok) {
        cachedStudiengang = null;
    }
    return resp.ok;
}

async function populateStudiengangSelects(current) {
    const list = await fetchStudiengaengeList();
    const baseOptions = list.map(s => `<option value="${s.id}">${sanitizeHtml(s.shortCode || '')} – ${sanitizeHtml(s.name || '')}</option>`);
    if (studiengangSelect) {
        studiengangSelect.innerHTML = baseOptions.join('');
        if (current?.id) {
            studiengangSelect.value = String(current.id);
        } else if (list.length > 0) {
            studiengangSelect.value = String(list[0].id);
        }
    }

    if (studiengangSettingsSelect) {
        if (current?.id) {
            studiengangSettingsSelect.innerHTML = baseOptions.join('');
            studiengangSettingsSelect.value = String(current.id);
        } else {
            const settingsOptions = ['<option value="">Bitte auswählen</option>'].concat(baseOptions);
            studiengangSettingsSelect.innerHTML = settingsOptions.join('');
            studiengangSettingsSelect.value = '';
        }
    }
}

async function checkAndPromptStudiengang() {
    try {
        const current = await getMyStudiengang();
        await populateStudiengangSelects(current);
        if (localStorage.getItem('loggedIn') === 'true' && (!current || !current.id)) {
            const el = document.getElementById('studiengangModal');
            const modal = Modal.getOrCreateInstance(el, { backdrop: 'static', keyboard: false });
            modal.show();
            return true;
        }
        return false;
    } catch (e) { console.debug('SG prompt failed', e); return false; }
}

async function initUserStudiengangUI() {
    const current = await getMyStudiengang();
    await populateStudiengangSelects(current);
}

if (saveStudiengangBtn) {
    saveStudiengangBtn.addEventListener('click', async () => {
        const id = studiengangSelect && studiengangSelect.value ? parseInt(studiengangSelect.value) : null;
        if (!id) { alert('Bitte auswählen'); return; }
        const ok = await setMyStudiengang(id);
        if (ok) {
            showMessage('Gespeichert', 'Studiengang gesetzt. Dein Angebote wurden gelöscht.');
            if (studiengangSettingsSelect) {
                studiengangSettingsSelect.value = String(id);
            }
            const modal = Modal.getOrCreateInstance(document.getElementById('studiengangModal'));
            modal.hide();
            if (localStorage.getItem('uploadAfterStudiengang') === 'true' && localStorage.getItem('tempCalendar')) {
                try {
                    const body = localStorage.getItem('tempCalendar');
                    const resp = await fetch('/uploadKalender', {
                        method: 'POST',
                        headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
                        body,
                        credentials: 'include'
                    });
                    if (resp.ok) {
                        localStorage.removeItem('uploadAfterStudiengang');
                        localStorage.removeItem('uploadLocalCalendar');
                        localStorage.removeItem('tempCalendar');
                        showMessage('Erfolg', 'Kalender erfolgreich hochgeladen');
                        getMyCalendar();
                        return;
                    } else {
                        showMessage('Fehler', 'Fehler beim Hochladen des Kalenders');
                    }
                } catch (e) { console.debug('Upload nach SG fehlgeschlagen', e); }
            }
            getMyCalendar();
        } else {
            alert('Fehler beim Speichern');
        }
    });
}

if (saveStudiengangSettingsBtn) {
    saveStudiengangSettingsBtn.addEventListener('click', async () => {
        const idStr = studiengangSettingsSelect ? studiengangSettingsSelect.value : '';
        if (!idStr) { alert('Bitte Studiengang auswählen'); return; }
        if (!confirm('Achtung: deine Angebote werden gelöscht. Fortfahren?')) return;
        const ok = await setMyStudiengang(parseInt(idStr));
        if (ok) {
            showMessage('Studiengang geändert', 'Kalender/Angebote gelöscht. Lade Daten neu…');
            getMyCalendar();
        } else {
            alert('Fehler beim Speichern');
        }
    });
}