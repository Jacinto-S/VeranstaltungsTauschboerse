import sanitizeHtml from 'sanitize-html';


function isDev() {
    return window.location.href.includes("localhost") || window.location.href.includes("http://");
}



var aorurl = document.getElementById('aorurl');
var aorurl2 = document.getElementById('aorurl2');
var aorurl3 = document.getElementById('aorurl3');

// select text of aorurl
function copy(event) {
    event.preventDefault();
    var range = document.createRange();
    range.selectNode(event.target);
    window.getSelection().removeAllRanges();
    window.getSelection().addRange(range);
    document.execCommand('copy');
    window.getSelection().removeAllRanges();
    alert("Link wurde in die Zwischenablage kopiert");
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
        alert('Link wurde in die Zwischenablage kopiert');
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
var logintoken = urlparams.get('logintoken');
if (logintoken != null) {
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/login";
    } else {
        url = "/login";
    }
    url = url + "?logintoken=" + logintoken;
    fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            response.text().then(data => {
                if (data.indexOf("New") != -1) {
                    localStorage.setItem('uploadLocalCalendar', "true");
                    localStorage.setItem('loggedIn', "true");
                    window.location.href = "/";
                } else {
                    localStorage.setItem('loggedIn', "true");
                    localStorage.removeItem('tempCalendar');
                    alert("Erfolgreich eingeloggt");
                    window.location.href = "/";

                }

            });

        } else {
            window.history.replaceState({}, document.title, "/");
        }
    });
} else {

}







submitemail.addEventListener('click', function () {

    if (email.value.includes("@student.hs-rm.de")) {
        var firstpart = email.value.split("@")[0];
        if (firstpart.includes(".")) {
            var url = "";
            if (isDev()) {
                url = "http://" + window.location.hostname + ":8085/requestLogin";
            } else {
                url = "/requestLogin";
            }
            url = url + "?hsMail=" + email.value;
            submitemail.disabled = true;
            setTimeout(function () {
                submitemail.disabled = false;
            }, 30000);

            fetch(url, {
                method: 'GET',
                credentials: 'include'
            }).then(response => {
                if (response.ok) {
                    alert("Bitte prüfe dein Postfach um die Anmeldung abzuschließen");
                } else {
                    alert("Fehler beim Versenden der E-Mail");
                }
            });

        } else {
            alert("Bitte geben Sie eine gültige Studenten-E-Mail ein");
            return;
        }
    } else {
        alert("Bitte geben Sie eine gültige Studenten-E-Mail ein");
    }

});

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
            alert("Feedback erfolgreich gesendet. Hinweis: Es kann etwas dauern, bis das Feedback sichtbar wird.");
            location.reload();

        } else {
            alert("Fehler beim Senden des Feedbacks");
        }
    });
});

import { Modal } from 'bootstrap';
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
            alert("Alle Angebote erfolgreich gelöscht");
            getMyCalendar();
        } else {
            alert("Fehler beim Löschen der Angebote");
        }
    });
});

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
        stateInfo.innerText = "Klicke einen Termin an, um ein Tauschangebot zu erstellen oder zu suchen.";
    }



    if (state == 1) {
        url += "?title=" + (offer.title.split("(")[0].trim()) + "&terminid=" + offer.offerid;
        stateInfo.style.visibility = "visible";
        stateInfo.innerText = "Wähle die Termine aus, die du gerne hättest.";
    }


    fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(response => response.json()).then(data => {
        showCalendar(data);
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
                    end: parsedEndDate
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
        const currentDay = (count);

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


            if (item.subtext.indexOf("OFFER") != -1) {
                itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title)}</strong><hr class="title-line"><div class="badge text-bg-danger"  style="opacity:1!important;background-color::black!important;transform:brightness(0.8)">${sanitizeHtml(item.subtext)}</div>`;
            } else {
                itemEl.innerHTML = `<strong class="item-title">${sanitizeHtml(item.title)}</strong><hr class="title-line"><div class="badge text-bg-secondary">${sanitizeHtml(item.subtext)}</div>`;
            }


            itemEl.style.top = `${timeToPosition(item.start)}%`;
            itemEl.style.height = `${timeToPosition(item.end) - timeToPosition(item.start)}%`;
            itemEl.style.left = `${samestart * 25}px`;


            itemEl.addEventListener('mouseover', function () {
                itemEl.style.zIndex = 2;  // hervorheben
            });
            itemEl.addEventListener('mouseout', function () {
                itemEl.style.zIndex = 1;  // zurücksetzen
            });

            try {
                var eventtype = item.title.match(/\(([^)]+)\)/)[1].split("-")[0];
                if (eventtype == "V") {
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
                        deletebtn.style.backgroundColor = "rgba(255, 0, 0, 0.6)";
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
                                    alert("Termin erfolgreich gelöscht");
                                    getMyCalendar();
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
                itemEl.style.opacity = "0.6";
                itemEl.style.cursor = "not-allowed";
                itemEl.style.fontSize = "10px";
                itemEl.disabled = true;
                itemEl.style.border = "3px solid green";
            }
            if (item.color == "rgba(227, 227, 227, 0.4)") {
                itemEl.style.opacity = "0.9";
            }

            if (item.subtext.indexOf("OFFER") != -1) {
                itemEl.style.opacity = "1";
                itemEl.style.fontSize = "10px";
            }
            if (lastclicked == offerId) {
                itemEl.style.border = "5px solid #FB6D48";
                itemEl.style.opacity = "1";
            }
            itemEl.addEventListener('click', function () {
                if (item.subtext.indexOf("ANGEFRAGT") != -1) {
                    return;
                }

                if (localStorage.getItem('loggedIn') !== 'true') {
                    document.getElementById('loginModalTitle').innerText = "Anmeldung erforderlich";
                    var instance = Modal.getOrCreateInstance(document.getElementById('loginModal'));
                    instance.show();
                    setTimeout(function () {
                        document.getElementById('email').focus();
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
                if (state == 1 && item.subtext.includes("OFFER") && item.subtext.indexOf("ANGEFRAGT") == -1) {
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
                        itemEl.style.border = "1px solid black";
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


                    getMyCalendar();
                    return;
                }

                if (state == 0) {
                    state = 1;
                    lastclicked = offerId;
                    itemEl.style.border = "5px solid #FB6D48";
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

            dayEl.appendChild(itemEl);
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
        document.getElementById('MailBoxGroup').style.display = "none";
        document.getElementById('removeAllOvers').style.visibility = "visible";
        document.getElementById('loginshowbtntext').innerText = "Ausloggen";
        document.getElementById('feedbackbtn').style.display = "inline";
        document.getElementById('privateMailBox').style.display = "block";
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
                loggedIn = true;
                localStorage.setItem('loggedIn', "true");
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
var url = "";
if (isDev()) {
    url = "http://" + window.location.hostname + ":8085/uploadKalender";
} else {
    url = "/uploadKalender";
}

if (localStorage.getItem('tempCalendar') != null && localStorage.getItem('loggedIn') !== 'true') {
    showIcalCalendar(ical.parseICS(localStorage.getItem('tempCalendar')));
} else if (localStorage.getItem('uploadLocalCalendar') === 'true') {
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
var mailUpdate = document.getElementById('private-mail'); var mailUpdateBtn = document.getElementById('save-private-mail');
mailUpdate.addEventListener('input', function () {
    // is the input valid?
    var mail = mailUpdate.value;
    if (mail.includes("@")) {
        mailUpdateBtn.disabled = false;
    } else {
        mailUpdateBtn.disabled = true;
    }
    if (mail.includes("student.hs-rm.de")) {
        mailUpdateBtn.disabled = true;
    }
});

// updatePrivateMail Endpoint. Get, Parameter is privateMail
mailUpdateBtn.addEventListener('click', function () {
    var mail = mailUpdate.value;
    var url = "";
    if (isDev()) {
        url = "http://" + window.location.hostname + ":8085/updatePrivateMail";
    } else {
        url = "/updatePrivateMail";
    }
    url = url + "?privateMail=" + mail;
    fetch(url, {
        method: 'GET',
        credentials: 'include'
    }).then(response => {
        if (response.ok) {
            alert("Private E-Mail erfolgreich gespeichert");
        } else {
            alert("Fehler beim Speichern der E-Mail");
        }
    });
});
var demoLogin = document.getElementById('demoLogin');
var demoLoginInput = document.getElementById('demoLoginInput');
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
loginshowbtn.addEventListener('click', function () {
    if (localStorage.getItem('loggedIn') === 'true') {
        var url = "";
        if (isDev()) {
            url = "http://" + window.location.hostname + ":8085/logmeout";
        } else {
            url = "/logmeout";
        }
        fetch(url, {
            method: 'GET',
            credentials: 'include'
        }).then(response => {
            if (response.ok) {
                localStorage.removeItem('tempCalendar');
                localStorage.removeItem('loggedIn');
                localStorage.removeItem('whoami');
                alert("Erfolgreich ausgeloggt");
                window.location.reload();
            } else {
                alert("Fehler beim Ausloggen");
            }
        });
    } else {
        var instance = Modal.getOrCreateInstance(document.getElementById('loginModal'));
        instance.show();
        setTimeout(function () {
            document.getElementById('email').focus();
        }, 100);
    }
});

var sharebtn = document.getElementById('sharebtn');
if (!navigator.share) {
    sharebtn.style.display = "none";
}