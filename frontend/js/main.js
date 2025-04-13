import sanitizeHtml from 'sanitize-html';
import 'altcha';


function isDev() {
    return window.location.href.includes("localhost") || window.location.href.includes("http://");
}



var aorurl = document.getElementById('aorurl');
var aorurl2 = document.getElementById('aorurl2');
var aorurl3 = document.getElementById('aorurl3');
var powverified = false;
var powpayload = "";

// select text of aorurl
function copy(event) {
    event.preventDefault();
    var range = document.createRange();
    range.selectNode(event.target);
    window.getSelection().removeAllRanges();
    window.getSelection().addRange(range);
    document.execCommand('copy');
    window.getSelection().removeAllRanges();
    showMessage("Link kopiert", "Link wurde in die Zwischenablage kopiert");
};
aorurl.addEventListener('click', copy);
aorurl2.addEventListener('click', copy);
aorurl3.addEventListener('click', copy);





// if file is dropped over the uploadHint, drop it into the fileupload input
var uploadHint = document.getElementById('uploadHint');
var myKalendar = document.getElementById('weekCalendar');
var file = document.getElementById('fileupload');

// make the uploadHint to a dropzone
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

// Account Management
var email = document.getElementById('email');
var submitemail = document.getElementById('submitemail');
var state = 0;
var offer = null;
var gesucht = [];

var urlparams = new URLSearchParams(window.location.search);
var logintoken = urlparams.get('otttoken');
if (logintoken != null) {
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/login/ott";
    } else {
        url = "/login/ott";
    }

    fetch(url, {
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


    var firstpart = email.value.split("@")[0];
    if (!powverified) {
        alert("Bitte bestätigen Sie, dass Sie kein Roboter sind");
        return;
    }

    if (firstpart.includes(".")) {
        var url = "";
        if (isDev()) {
            url = "http://" + window.location.hostname + ":8085/ott/generate";
        } else {
            url = "/ott/generate";
        }


        if (notifyUser) {
            submitemail.innerHTML = "<span class='spinner-border spinner-border-sm' role='status' aria-hidden='true'></span>";
            submitemail.disabled = true;
            setTimeout(function () {
                submitemail.disabled = false;
            }, 30000);
        }

    } else {
        alert("Bitte geben Sie eine gültige Studenten-E-Mail ein");
        return;
    }

    // Form data required for the request. Send hsMail as username and pow as pow
    fetch(url, {
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
                }, 2500);
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
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/randomFeedback";
    } else {
        url = "/randomFeedback";
    }
    fetch(url + "?count=3", {
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

    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/feedback";
    } else {
        url = "/feedback";
    }
    fetch(url, {
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
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/removeMyOffers";
    } else {
        url = "/removeMyOffers";
    }
    fetch(url, {
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




function showUploadedCalendar() {

    var filedata = file.files[0];
    if (filedata.name.split('.').pop() != "ics") {
        alert("Bitte laden Sie eine .ics-Datei hoch");
        return;
    }

    var reader = new FileReader();
    reader.onload = function (e) {
        var data = e.target.result;
        var url = "";
        if (isDev()) {
            url = "http://" + window.location.hostname + ":8085/uploadKalender";
        } else {
            url = "/uploadKalender";
        }
        if (localStorage.getItem('loggedIn') !== 'true') {
            localStorage.setItem('tempCalendar', data);
            showIcalCalendar(ical.parseICS(data));

            return;
        }

        fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: data,
            credentials: 'include'
        }).then(response => {
            if (response.ok) {
                alert("Kalender erfolgreich hochgeladen");
                getMyCalendar();
            } else {
                alert("Fehler beim Hochladen des Kalenders");
            }
        });

        var parsed = ical.parseICS(data);
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
    var offerUrl = "";
    if (isDev()) {
        offerUrl = "http://" + window.location.hostname + ":8085/createOffer";
    } else {
        offerUrl = "/createOffer";
    }

    fetch(offerUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(angebot),
        credentials: 'include'
    }).then(response => {
        if (response.ok) {


            var angebotErstelltModal = Modal.getOrCreateInstance(document.getElementById('angebotErstelltModal'));
            state = 1;
            gesucht = [];
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


function debounce(func, wait) {
    let timeout;
    return function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}


function getMyCalendar() {
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/myKalender";
    } else {
        url = "/myKalender";
    }

    var stateInfo = document.getElementById('stateInfo');
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
                showCalendar(data);
                localStorage.removeItem('uploadLocalCalendarIfNotExist');
            });
        } else {
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

function showCalendar(items) {
    var dayNames = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag'];
    const days = [{ 'Montag': [] }, { 'Dienstag': [] }, { 'Mittwoch': [] }, { 'Donnerstag': [] }, { 'Freitag': [] }];
    const startHour = 8;  // 8:00 Uhr
    const endHour = 21.2;   // 21:00 Uhr



    const calendarEl = document.getElementById('weekCalendar');
    calendarEl.innerHTML = '';
    function timeToPosition(time) {
        const [hours, minutes] = time.split(':').map(Number);
        const totalHours = (hours - startHour) + (minutes / 60);
        // add percentage to hide header
        // day height is 100% - 25px (header height)
        var dayHeight = 650;
        var percent = 25 * 100 / dayHeight;
        var perc = ((1 - (totalHours / (endHour - startHour)) * 1) * percent);
        return ((totalHours / (endHour - startHour)) * 100) + perc;
    }


    // Zeitskala hinzufügen
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
        const currentDay = (count);
        items[count].sort((a, b) => {
            return a.start.localeCompare(b.start);
        });

        (items[count] || []).forEach((item, index) => {
            const offerId = item.offerid;
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
            if (start.getTime() < lastEnd.getTime()) {
                isAllowed = false;
                isUnderOther = true;
            } else {
                lastEnd = end;
            }

            if (item.subtext.indexOf("OFFER") != -1) {
                itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title)}</strong><hr class="title-line"><div class="badge text-bg-danger"  style="opacity:1!important;background-color::black!important;transform:brightness(0.8)">${sanitizeHtml(item.subtext)}</div>`;

            } else if (item.subtext == "" && (item.title.indexOf("(P-") != -1 || item.title.indexOf("(Ü-") != -1 || item.title.indexOf("(S-") != -1 || item.title.indexOf("(SU-") != -1) && item.title.match(/\(([^)]+)\)/)[1].split("-")[1] != undefined) {
                try {
                    var praktikumtype = item.title.match(/\(([^)]+)\)/)[1].split("-")[1];
                    itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title.split(" ")[0])}</strong><hr class="title-line"><p style="text-align: center;font-size: 28px;opacity: 0.7;color:#808080">${praktikumtype}</p>`;
                } catch (error) {
                    itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title)}</strong><hr class="title-line"><div class="badge text-bg-secondary smallbadge">${sanitizeHtml(item.subtext)}</div>`;
                }



            } else {
                itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title.split(" ")[0])}</strong><hr class="title-line"><div class="badge text-bg-secondary smallbadge">${sanitizeHtml(item.subtext)}</div>`;
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





            try {
                var eventtype = item.title.match(/\(([^)]+)\)/)[1].split("-")[0];
                if (eventtype == "V" || item.subtext.indexOf("ANGEFRAGT") != -1 || !isAllowed) {
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
                            var url = "";
                            if (isDev()) {
                                url = "http://" + window.location.hostname + ":8085/removeTermin?terminid=" + id;
                            } else {
                                url = "/removeTermin?terminid=" + id;
                            }
                            fetch(url, {
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
            } catch (error) {

            }
            if (state == 1) {
                itemEl.style.opacity = "0.3";
            }


            if (item.subtext.indexOf("ANGEFRAGT") != -1) {
                if (samestart == 0) {
                    itemEl.style.opacity = "0.6";
                } else {
                    itemEl.style.opacity = "";
                }


                itemEl.style.cursor = "not-allowed";
                itemEl.style.fontSize = "10px";
                itemEl.disabled = true;
                //

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
                    setTimeout(function () {

                    }, 100);
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
                        var url = "";
                        if (isDev()) {
                            url = "http://" + window.location.hostname + ":8085/acceptOffer";
                        } else {
                            url = "/acceptOffer";
                        }
                        url = url + "?selectedTermin=" + offerId;
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
            if (!isUnderOther) {
                dayEl.appendChild(itemEl);
            }
        });

        calendarEl.appendChild(dayEl);
    });
    resizeDayHeaders();
}
// Manage Visibility
var whoamiurl = "/";
if (isDev()) {
    whoamiurl = "http://" + window.location.hostname + ":8085/whoami";
} else {
    whoamiurl = "/whoami";
}
var loggedIn = localStorage.getItem('loggedIn') === 'true';
var name = "";
var whoamidata = "";


function manageVisibility() {
    if (loggedIn) {
        loggedIn = true;
        document.getElementById('fileupload').disabled = false;
        document.getElementById('removeAllOvers').style.visibility = "visible";
        document.getElementById('loginshowbtntext').innerText = "Ausloggen";
        document.getElementById('feedbackbtn').style.display = "inline";
        document.getElementById('privateMailBox').style.display = "block";
        document.getElementById('confirmOffer').style.visibility = "";
        document.getElementById('createPasskey').style.display = "block";

    } else {
        document.getElementById('title').innerText = "Wochenkalender (Nicht eingeloggt)";
        document.getElementById('removeAllOvers').style.visibility = "hidden";
        document.getElementById('confirmOffer').style.visibility = "hidden";
        document.getElementById('loginshowbtntext').innerText = "Anmelden";

        if (document.getElementById('uploadHint') != null) {
            document.getElementById('uploadHint').style.display = "block";
        }
    }
}
manageVisibility();


fetch(whoamiurl, {
    method: 'GET',
    credentials: 'include'
}).then(response => {
    if (response.ok) {
        response.text().then(data => {
            if (data.includes("student.hs-rm.de")) {
                if (localStorage.getItem('loggedIn') == null) {
                    localStorage.setItem('loggedIn', "true");
                    localStorage.setItem('uploadLocalCalendarIfNotExist', "true");
                    getMyCalendar();
                }
                loggedIn = true;
                isLoggedIn = true;
                whoamidata = data;
                document.getElementById('title').innerText = "Wochenkalender für " + extractName(whoamidata);
                localStorage.setItem('whoami', extractName(whoamidata));
                manageVisibility();

            } else {
                if (logintoken != null) {
                    loggedIn = false;
                    manageVisibility();
                    return;
                }
                localStorage.removeItem('loggedIn');
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

function checkCalendarAutoUpload() {
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/uploadKalender";
    } else {
        url = "/uploadKalender";
    }

    if (localStorage.getItem('tempCalendar') != null && localStorage.getItem('loggedIn') !== 'true') {
        showIcalCalendar(ical.parseICS(localStorage.getItem('tempCalendar')));
    } else if (localStorage.getItem('uploadLocalCalendar') === 'true' && localStorage.getItem('tempCalendar') != null) {
        fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
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
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/updatePrivateMail";
    } else {
        url = "/updatePrivateMail";
    }
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
if (isDev()) {
    demoLogin.style.display = "block";
    demoLoginInput.style.display = "block";
    betaLoginBox.style.display = "block";
}
demoLogin.addEventListener('click', function () {
    var url = "";
    if (demoLoginInput.value == "" || demoLoginInput.value == null || demoLoginInput.value > 100 || demoLoginInput.value < 1) {
        alert("Die Nummer muss zwischen 1 und 100 liegen");
        return;
    }

    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/betaLogin?number=" + demoLoginInput.value;
    } else {
        url = "/betaLogin?number=" + demoLoginInput.value;
    }
    fetch(url, {
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

var loginshowbtn = document.getElementById('loginshowbtn');
loginshowbtn.addEventListener('mousedown', function () {
    if (localStorage.getItem('loggedIn') === 'true') {
        logout();
    } else {
        var instance = Modal.getOrCreateInstance(document.getElementById('loginModal'));
        instance.show();
        setTimeout(async function () {
            // is passkey supported?
            if (window.PublicKeyCredential && PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable) {
                let doc = document.getElementById('passkey-login-button');
                doc.focus();
            } else {
                document.getElementById('email').focus();

            }
        }, 125);
    }
});

function logout(all = false) {
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/logmeout";
    } else {
        url = "/logmeout";
    }
    if (all) {
        url += "?all=true";
    }
    loginshowbtn.disabled = true;

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
                showMessage("Erfolgreich ausgeloggt", "Du wurdest erfolgreich ausgeloggt");
                setTimeout(function () {
                    window.location.href = "/";
                }, 2500);
            }

        } else {
            loginshowbtn.removeAttribute("disabled");
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
                verified: "Student erkannt",
                verifying: "Prüfe auf Roboter...",
                error: "Fehler. Roboter erkannt!",

            },
        });
    }
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
    // Prüfen, ob der User angemeldet ist

    try {
        // Registrierungsoptionen vom Server abrufen
        const optionsUrl = isDev()
            ? `http://${window.location.hostname}:8085/webauthn/register/options`
            : "/webauthn/register/options";
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

        // Label (z.B. aus einem Input-Feld) ermitteln – passe den Selector ggf. an
        const label = document.getElementById('labelInput')?.value || "Default Label";

        // Payload zusammenstellen: publicKey-Objekt mit dem Credential und dem Label
        const payload = {
            publicKey: {
                credential: credentialData,
                label: label
            }
        };

        // Registrierungsdaten an den Server schicken
        const registerUrl = isDev()
            ? `http://${window.location.hostname}:8085/webauthn/register`
            : "/webauthn/register";


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
        alert("Passkey erfolgreich erstellt!");
        var instance = Modal.getOrCreateInstance(document.getElementById('passkey-modal'), {
            backdrop: 'static',
            keyboard: false
        });
        instance.hide();

    } catch (error) {
        console.error("Error during passkey registration:", error);
        alert("Fehler beim Erstellen des Passkeys. Entweder dein Browser oder Betriebssystem hat Probleme bei der Unterstützung von Passkeys oder du hast bereits einen Passkey erstellt.");
    }
}


let passkey = document.getElementById('createPasskey');
passkey.addEventListener('click', function () {
    createPasskey();
});





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

    // URL der Options-Anfrage; beachte ggf. deine Dev-/Prod-Logik
    const authOptionsUrl = isDev()
        ? `http://${window.location.hostname}:8085/webauthn/authenticate/options`
        : "/webauthn/authenticate/options";

    // Optional: Übermittle hier die eingegebene E‑Mail, wenn dein Backend danach filtert

    // Hole die Authentifizierungsoptionen vom Server
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

    // Zuerst den JSON-Response parsen
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

document.getElementById('passkey-login-button').addEventListener('click', async function () {
    try {
        const csrf = await getCsrfToken();



        // Navigator für Authentifizierung aufrufen
        const assertion = await navigator.credentials.get({ publicKey: await getAuthOptions() });
        const assertionData = transformAssertion(assertion);
        handlePasskeyLogin(assertionData);

    } catch (error) {
        console.error("Fehler während der Passkey-Authentifizierung:", error);
    }
});

async function handlePasskeyLogin(assertionData) {
    // Sende die authentifizierten Daten an den Server
    const loginUrl = isDev()
        ? `http://${window.location.hostname}:8085/login/webauthn`
        : "/login/webauthn";

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
        console.log(result);            // Erfolgreich authentifiziert; leite weiter (z. B. auf die Startseite)
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
        let registerPasskeybtn = document.getElementById('register-passkey');
        registerPasskeybtn.addEventListener('click', function () {
            createPasskey();
        });
        window.history.replaceState({}, document.title, "/");
    }
}

// Has Url Parameter "passkey" and show Modal
if (window.location.search.includes("pk")) {
    showPasskeyModal();
}