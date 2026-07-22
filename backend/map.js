// ============================================================
// MotoTracker - map.js (multi-device, multi-user)
// ============================================================

// --- Auth helpers ---
// A read-only token may be provided via ?token=... in the URL (used when
// embedding the dashboard in Home Assistant etc). The token is stashed in
// sessionStorage so internal links/refreshes keep working without polluting
// the address bar; in token mode, mutating UI is hidden and 401s do NOT
// redirect to login.html (that would loop inside an iframe).
var AUTH_TOKEN = (function() {
    var u = new URLSearchParams(window.location.search);
    var t = u.get('token');
    if (t) {
        try { sessionStorage.setItem('mt_token', t); } catch (e) {}
        return t;
    }
    try { return sessionStorage.getItem('mt_token') || ''; } catch (e) { return ''; }
})();

function withToken(url) {
    if (!AUTH_TOKEN) return url;
    return url + (url.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(AUTH_TOKEN);
}

function authFetch(url, opts) {
    return fetch(withToken(url), opts).then(function(r) {
        if (r.status === 401) {
            if (!AUTH_TOKEN) window.location.href = 'login.html';
            throw new Error('unauthenticated');
        }
        return r;
    });
}

function logout() {
    fetch('logout.php', { method: 'POST' }).finally(function() {
        window.location.href = 'login.html';
    });
}

function loadCurrentUser() {
    return authFetch('pobierz_me.php')
        .then(function(r) { return r.json(); })
        .then(function(me) {
            var readonly = !!(me && me.readonly);
            var el = document.getElementById('userInfo');
            if (el && me && me.username) {
                var adminBadge = me.is_admin && !readonly ? ' <span class="user-admin">admin</span>' : '';
                var roBadge    = readonly ? ' <span class="user-admin">read-only</span>' : '';
                el.innerHTML = '<i class="fas fa-user"></i> ' +
                    (me.display_name || me.username) + adminBadge + roBadge;
            }
            var adminLink = document.getElementById('btnAdmin');
            if (adminLink) adminLink.style.display = (me && me.is_admin && !readonly) ? '' : 'none';
            // In token mode hide every control that mutates state or breaks the embed.
            if (readonly) {
                ['btnLogout'].forEach(function(id) {
                    var b = document.getElementById(id);
                    if (b) b.style.display = 'none';
                });
                document.body.classList.add('readonly');
            }
            return me;
        })
        .catch(function() {
            if (AUTH_TOKEN) {
                var el = document.getElementById('userInfo');
                if (el) el.innerHTML = '<i class="fas fa-triangle-exclamation"></i> invalid token';
            }
            return null;
        });
}

// --- Map setup ---
var map = L.map('map', { zoomControl: true }).setView([52.0694, 19.4800], 7);
var markersLayer = L.layerGroup().addTo(map);
var trackLayer   = L.layerGroup().addTo(map);
var replayMarker = null;
var currentPoints = [];       // single-device mode: flat array
var currentMulti = [];        // all-devices mode: [{code,name,color,points}]
var replayIndex = 0;
var replayTimer = null;
var isPlaying = false;

var devices = [];             // [{id, code, name, color, active}]
var devicesByCode = {};
var currentDeviceCode = 'all';

// --- Tile layers ---
var tileLayers = {
    street: L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors'
    }),
    satellite: L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        attribution: '&copy; Esri'
    }),
    terrain: L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenTopoMap'
    }),
    dark: L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; CartoDB'
    })
};
tileLayers.street.addTo(map);
var activeLayer = 'street';

document.querySelectorAll('.layer-btn').forEach(function(btn) {
    btn.addEventListener('click', function() {
        var layer = this.dataset.layer;
        if (layer === activeLayer) return;
        map.removeLayer(tileLayers[activeLayer]);
        tileLayers[layer].addTo(map);
        activeLayer = layer;
        document.querySelectorAll('.layer-btn').forEach(function(b) { b.classList.remove('active'); });
        this.classList.add('active');
    });
});

// --- Helpers ---
function haversine(lat1, lon1, lat2, lon2) {
    var R = 6371;
    var dLat = (lat2 - lat1) * Math.PI / 180;
    var dLon = (lon2 - lon1) * Math.PI / 180;
    var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function speedColor(speed, maxSpeed) {
    var ratio = Math.min(speed / Math.max(maxSpeed, 1), 1);
    var r, g, b;
    if (ratio < 0.5) {
        r = Math.round(255 * (ratio * 2));
        g = 213;
        b = 115 - Math.round(115 * ratio);
    } else {
        r = 233;
        g = Math.round(213 - (213 - 69) * ((ratio - 0.5) * 2));
        b = Math.round(96);
    }
    return 'rgb(' + r + ',' + g + ',' + b + ')';
}

function computeSpeeds(points) {
    var hasGpsSpeed = points.some(function(p) { return p.speed !== null && p.speed > 0; });
    if (hasGpsSpeed) {
        return points.map(function(p) { return (p.speed !== null) ? p.speed : 0; });
    }
    var speeds = [0];
    for (var i = 1; i < points.length; i++) {
        var dist = haversine(points[i-1].lat, points[i-1].lon, points[i].lat, points[i].lon);
        var t1 = new Date(points[i-1].timestamp).getTime();
        var t2 = new Date(points[i].timestamp).getTime();
        var hours = (t2 - t1) / 3600000;
        speeds.push(hours > 0 ? dist / hours : 0);
    }
    return speeds;
}

function createDivIcon(html, className) {
    return L.divIcon({
        html: html,
        className: 'custom-marker ' + className,
        iconSize: [30, 30],
        iconAnchor: [15, 15]
    });
}

var startIcon  = createDivIcon('<i class="fas fa-flag"></i>', 'start-marker');
var finishIcon = createDivIcon('<i class="fas fa-flag-checkered"></i>', 'finish-marker');
var motoIcon   = createDivIcon('<i class="fas fa-motorcycle"></i>', 'moto-icon');

function deviceDotIcon(color) {
    return L.divIcon({
        html: '<div style="background:' + color + '"></div>',
        className: 'device-marker',
        iconSize: [18, 18],
        iconAnchor: [9, 9]
    });
}

function formatDuration(ms) {
    var totalSec = Math.floor(ms / 1000);
    var h = Math.floor(totalSec / 3600);
    var m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return h + 'h ' + m + 'm';
    return m + ' min';
}

// --- Stats ---
function clearStats() {
    ['statDistance','statDuration','statMovingTime','statAvgSpeed','statMovingAvg','statMaxSpeed','statTemp','statBattery']
        .forEach(function(id) { document.getElementById(id).textContent = '--'; });
}

var MOVING_THRESHOLD = 2; // km/h

function updateStats(points, speeds) {
    if (!points || points.length < 2) { clearStats(); return; }

    var totalDist = 0;
    var movingTime = 0;
    for (var i = 1; i < points.length; i++) {
        totalDist += haversine(points[i-1].lat, points[i-1].lon, points[i].lat, points[i].lon);
        if (speeds[i] >= MOVING_THRESHOLD) {
            var t1 = new Date(points[i-1].timestamp).getTime();
            var t2 = new Date(points[i].timestamp).getTime();
            movingTime += (t2 - t1);
        }
    }

    var t0 = new Date(points[0].timestamp).getTime();
    var tN = new Date(points[points.length - 1].timestamp).getTime();
    var duration = tN - t0;

    var maxSpeed = 0;
    for (var j = 0; j < speeds.length; j++) {
        if (speeds[j] > maxSpeed) maxSpeed = speeds[j];
    }
    var avgSpeed = (duration > 0) ? totalDist / (duration / 3600000) : 0;
    var movingAvg = (movingTime > 0) ? totalDist / (movingTime / 3600000) : 0;
    var realisticMax = Math.min(maxSpeed, 300);
    var lastPoint = points[points.length - 1];

    document.getElementById('statDistance').textContent = totalDist.toFixed(1) + ' km';
    document.getElementById('statDuration').textContent = formatDuration(duration);
    document.getElementById('statMovingTime').textContent = formatDuration(movingTime);
    document.getElementById('statAvgSpeed').textContent = avgSpeed.toFixed(0) + ' km/h';
    document.getElementById('statMovingAvg').textContent = movingAvg.toFixed(0) + ' km/h';
    document.getElementById('statMaxSpeed').textContent = realisticMax.toFixed(0) + ' km/h';
    document.getElementById('statTemp').textContent    = (lastPoint.temperature !== null && lastPoint.temperature !== undefined) ? lastPoint.temperature.toFixed(1) + ' °C' : '--';
    document.getElementById('statBattery').textContent = (lastPoint.battery     !== null && lastPoint.battery     !== undefined) ? lastPoint.battery.toFixed(1)     + ' V'  : '--';
}

// --- Drawing: single device (matched polyline if available, else speed gradient) ---
function drawSingleDevice(points, matchedGeom) {
    currentPoints = points;
    currentMulti = [];
    markersLayer.clearLayers();
    trackLayer.clearLayers();
    stopReplay();

    if (!points || points.length === 0) {
        clearStats();
        document.getElementById('speed-legend').classList.remove('visible');
        return;
    }

    var speeds = computeSpeeds(points);
    updateStats(points, speeds);

    var hasMatched = matchedGeom && matchedGeom.coordinates && matchedGeom.coordinates.length >= 2;

    if (hasMatched) {
        var dev = devicesByCode[currentDeviceCode];
        var lineColor = (dev && dev.color) ? dev.color : '#3498db';
        var latlngs = matchedGeom.coordinates.map(function(c) { return [c[1], c[0]]; });
        L.polyline(latlngs, { color: lineColor, weight: 4, opacity: 0.9 }).addTo(trackLayer);
        document.getElementById('speed-legend').classList.remove('visible');
    } else {
        var maxSpeed = 0;
        for (var i = 0; i < speeds.length; i++) {
            if (speeds[i] < 300 && speeds[i] > maxSpeed) maxSpeed = speeds[i];
        }
        for (var j = 1; j < points.length; j++) {
            var color = speedColor(Math.min(speeds[j], 300), maxSpeed);
            var segment = L.polyline(
                [[points[j-1].lat, points[j-1].lon], [points[j].lat, points[j].lon]],
                { color: color, weight: 4, opacity: 0.85 }
            );
            var speedSource = (points[j].speed !== null && points[j].speed > 0) ? 'GPS' : 'calc';
            segment.bindPopup(
                '<b>' + Math.min(speeds[j], 300).toFixed(0) + ' km/h</b> <small>(' + speedSource + ')</small><br>' +
                points[j].timestamp
            );
            trackLayer.addLayer(segment);
        }
        document.getElementById('legendMax').textContent = maxSpeed.toFixed(0) + ' km/h';
        document.getElementById('speed-legend').classList.add('visible');
    }

    var arrowInterval = Math.max(1, Math.floor(points.length / 20));
    for (var k = arrowInterval; k < points.length; k += arrowInterval) {
        var p1 = points[k - 1], p2 = points[k];
        var angle = Math.atan2(p2.lon - p1.lon, p2.lat - p1.lat) * 180 / Math.PI;
        var arrow = L.divIcon({
            html: '<i class="fas fa-chevron-up" style="color:white;font-size:10px;transform:rotate(' + angle + 'deg);opacity:0.7;"></i>',
            className: '', iconSize: [12, 12], iconAnchor: [6, 6]
        });
        L.marker([p2.lat, p2.lon], { icon: arrow, interactive: false }).addTo(trackLayer);
    }

    L.marker([points[0].lat, points[0].lon], { icon: startIcon })
        .bindPopup('<b>Start</b><br>' + points[0].timestamp).addTo(markersLayer);
    if (points.length > 1) {
        var last = points[points.length - 1];
        L.marker([last.lat, last.lon], { icon: finishIcon })
            .bindPopup('<b>Finish</b><br>' + last.timestamp).addTo(markersLayer);
    }

    var bounds = points.map(function(p) { return [p.lat, p.lon]; });
    if (bounds.length) map.fitBounds(bounds, { padding: [30, 30] });
}

// --- Drawing: all devices (per-device solid colors) ---
function drawAllDevices(groups) {
    currentMulti = groups;
    currentPoints = [];
    markersLayer.clearLayers();
    trackLayer.clearLayers();
    stopReplay();
    clearStats();
    document.getElementById('speed-legend').classList.remove('visible');

    if (!groups || groups.length === 0) return;

    var allLatlngs = [];
    groups.forEach(function(g) {
        if (!g.points || g.points.length === 0) return;
        var coords = g.points.map(function(p) { return [p.lat, p.lon]; });
        var line = L.polyline(coords, { color: g.color, weight: 3, opacity: 0.85 });
        line.bindPopup('<b>' + g.name + '</b><br>' + g.points.length + ' points');
        trackLayer.addLayer(line);

        var last = g.points[g.points.length - 1];
        L.marker([last.lat, last.lon], { icon: deviceDotIcon(g.color) })
            .bindPopup('<b>' + g.name + '</b><br>' + last.timestamp)
            .addTo(markersLayer);

        coords.forEach(function(c) { allLatlngs.push(c); });
    });

    if (allLatlngs.length) map.fitBounds(allLatlngs, { padding: [30, 30] });
}

// --- Fetching ---
function fetchMatchedGeom(date, deviceCode) {
    var url = 'pobierz_match.php?date=' + encodeURIComponent(date) +
              '&device=' + encodeURIComponent(deviceCode);
    return authFetch(url)
        .then(function(r) { return r.json(); })
        .then(function(j) { return (j && j.matched_geometry) ? j.matched_geometry : null; })
        .catch(function() { return null; });
}

function refreshMap() {
    var selectedDate = document.getElementById('datePicker').value;
    var ptsUrl = 'pobierz_punkty.php?date=' + encodeURIComponent(selectedDate) +
              '&device=' + encodeURIComponent(currentDeviceCode);

    if (currentDeviceCode === 'all') {
        authFetch(ptsUrl)
            .then(function(r) { return r.json(); })
            .then(function(data) {
                drawAllDevices(Array.isArray(data) ? data : []);
                highlightActiveRide(selectedDate);
            })
            .catch(function(err) { console.error('refreshMap error:', err); });
        return;
    }

    Promise.all([
        authFetch(ptsUrl).then(function(r) { return r.json(); }),
        fetchMatchedGeom(selectedDate, currentDeviceCode)
    ]).then(function(results) {
        var pts = Array.isArray(results[0]) ? results[0] : [];
        drawSingleDevice(pts, results[1]);
        highlightActiveRide(selectedDate);
    }).catch(function(err) { console.error('refreshMap error:', err); });
}

function loadRide(date, deviceCode) {
    document.getElementById('datePicker').value = date;
    if (deviceCode && deviceCode !== currentDeviceCode) {
        currentDeviceCode = deviceCode;
        document.getElementById('devicePicker').value = deviceCode;
    }
    refreshMap();
    if (isToday(date)) startLiveRefresh(); else stopLiveRefresh();
}

function highlightActiveRide(date) {
    document.querySelectorAll('.ride-item').forEach(function(item) {
        item.classList.remove('active');
        if (item.dataset.date === date && item.dataset.device === currentDeviceCode) {
            item.classList.add('active');
        }
    });
}

// --- Ride history ---
function loadRideHistory() {
    var rideList = document.getElementById('rideList');
    rideList.innerHTML = '<div class="loading"><i class="fas fa-spinner"></i> Loading...</div>';

    var url = 'pobierz_historie.php?device=' + encodeURIComponent(currentDeviceCode);
    authFetch(url)
        .then(function(r) { return r.json(); })
        .then(function(rides) {
            rideList.innerHTML = '';
            if (!rides || rides.length === 0) {
                rideList.innerHTML = '<div class="loading">No rides found</div>';
                return;
            }
            rides.forEach(function(ride) {
                var item = document.createElement('div');
                item.className = 'ride-item';
                item.dataset.date = ride.ride_date;
                item.dataset.device = ride.device_code;
                (function(r) {
                    item.onclick = function() { loadRide(r.ride_date, r.device_code); };
                })(ride);

                var dateObj = new Date(ride.ride_date + 'T00:00:00');
                var dayName = dateObj.toLocaleDateString('en-US', { weekday: 'short' });
                var dateStr = dateObj.toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' });
                var startTime = ride.start_time ? ride.start_time.split(' ')[1] : '';
                var endTime   = ride.end_time   ? ride.end_time.split(' ')[1]   : '';
                if (startTime) startTime = startTime.substring(0, 5);
                if (endTime)   endTime   = endTime.substring(0, 5);

                item.innerHTML =
                    '<div class="ride-icon" style="color:' + ride.device_color + '"><i class="fas fa-route"></i></div>' +
                    '<div class="ride-info">' +
                        '<div class="ride-date">' + dayName + ', ' + dateStr +
                            ' <span class="ride-device" style="background:' + ride.device_color + '">' + ride.device_name + '</span>' +
                        '</div>' +
                        '<div class="ride-meta">' +
                            '<i class="fas fa-map-pin"></i> ' + ride.points + ' pts' +
                            (startTime ? ' &middot; ' + startTime + ' - ' + endTime : '') +
                        '</div>' +
                    '</div>';
                rideList.appendChild(item);
            });
        })
        .catch(function(err) {
            rideList.innerHTML = '<div class="loading">Error loading rides</div>';
            console.error(err);
        });
}

// --- Replay (single-device mode only) ---
function toggleReplay() {
    if (currentDeviceCode === 'all') return;
    if (isPlaying) pauseReplay(); else startReplay();
}

function startReplay() {
    if (currentPoints.length < 2) return;
    isPlaying = true;
    document.getElementById('replayIcon').className = 'fas fa-pause';
    if (!replayMarker) {
        replayMarker = L.marker([currentPoints[0].lat, currentPoints[0].lon],
            { icon: motoIcon, zIndexOffset: 1000 }).addTo(map);
        replayIndex = 0;
    }
    stepReplay();
}

function stepReplay() {
    if (!isPlaying || replayIndex >= currentPoints.length - 1) {
        if (replayIndex >= currentPoints.length - 1) {
            pauseReplay();
            document.getElementById('replayProgress').style.width = '100%';
        }
        return;
    }
    replayIndex++;
    var p = currentPoints[replayIndex];
    replayMarker.setLatLng([p.lat, p.lon]);
    var progress = (replayIndex / (currentPoints.length - 1)) * 100;
    document.getElementById('replayProgress').style.width = progress + '%';
    var speed = parseInt(document.getElementById('replaySpeed').value);
    replayTimer = setTimeout(stepReplay, 1000 / speed);
}

function pauseReplay() {
    isPlaying = false;
    document.getElementById('replayIcon').className = 'fas fa-play';
    if (replayTimer) { clearTimeout(replayTimer); replayTimer = null; }
}

function stopReplay() {
    pauseReplay();
    replayIndex = 0;
    document.getElementById('replayProgress').style.width = '0%';
    if (replayMarker) { map.removeLayer(replayMarker); replayMarker = null; }
}

document.getElementById('replaySpeed').addEventListener('input', function() {
    document.getElementById('speedLabel').textContent = this.value + 'x';
});

// --- GPX export (single-device only) ---
function exportGPX() {
    if (currentDeviceCode === 'all') { alert('Select a single device to export GPX.'); return; }
    var date = document.getElementById('datePicker').value || '';
    window.open(withToken('export_gpx.php?date=' + encodeURIComponent(date) + '&device=' + encodeURIComponent(currentDeviceCode)), '_blank');
}

function toggleFullscreen() {
    document.body.classList.toggle('fullscreen');
    var icon = document.querySelector('#btnFullscreen i');
    icon.className = document.body.classList.contains('fullscreen') ? 'fas fa-compress' : 'fas fa-expand';
    setTimeout(function() { map.invalidateSize(); }, 300);
}

function toggleSidebar() {
    var sidebar = document.getElementById('sidebar');
    var toggle  = document.getElementById('sidebar-toggle');
    var icon    = document.getElementById('sidebarIcon');
    sidebar.classList.toggle('collapsed');
    toggle.classList.toggle('collapsed');
    if (sidebar.classList.contains('collapsed')) {
        icon.className = 'fas fa-chevron-right';
        toggle.style.left = '0px';
    } else {
        icon.className = 'fas fa-chevron-left';
        var w = window.innerWidth <= 768 ? 280 : 320;
        toggle.style.left = w + 'px';
    }
    setTimeout(function() { map.invalidateSize(); }, 300);
}

// --- Live refresh ---
var liveTimer = null;

function isToday(dateStr) {
    return dateStr === new Date().toISOString().split('T')[0];
}

function startLiveRefresh() {
    stopLiveRefresh();
    liveTimer = setInterval(function() {
        var selectedDate = document.getElementById('datePicker').value;
        if (!isToday(selectedDate)) return;

        var url = 'pobierz_punkty.php?date=' + encodeURIComponent(selectedDate) +
                  '&device=' + encodeURIComponent(currentDeviceCode);
        authFetch(url)
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (currentDeviceCode === 'all') {
                    var totalNew = 0;
                    data.forEach(function(g) { totalNew += g.points ? g.points.length : 0; });
                    var totalOld = 0;
                    currentMulti.forEach(function(g) { totalOld += g.points ? g.points.length : 0; });
                    if (totalNew !== totalOld) drawAllDevices(data);
                } else {
                    if (Array.isArray(data) && data.length !== currentPoints.length) {
                        fetchMatchedGeom(selectedDate, currentDeviceCode).then(function(matched) {
                            drawSingleDevice(data, matched);
                            if (data.length > 0) {
                                var last = data[data.length - 1];
                                map.panTo([last.lat, last.lon]);
                            }
                        });
                    }
                }
            })
            .catch(function(err) { console.error('Live refresh error:', err); });
    }, 2000);
}

function stopLiveRefresh() {
    if (liveTimer) { clearInterval(liveTimer); liveTimer = null; }
}

document.getElementById('datePicker').addEventListener('change', function() {
    if (isToday(this.value)) startLiveRefresh(); else stopLiveRefresh();
});

// --- Devices ---
function loadDevices() {
    return authFetch('pobierz_urzadzenia.php')
        .then(function(r) { return r.json(); })
        .then(function(list) {
            devices = Array.isArray(list) ? list : [];
            devicesByCode = {};
            devices.forEach(function(d) { devicesByCode[d.code] = d; });

            var sel = document.getElementById('devicePicker');
            sel.innerHTML = '';
            var all = document.createElement('option');
            all.value = 'all'; all.textContent = 'All devices';
            sel.appendChild(all);
            devices.forEach(function(d) {
                var opt = document.createElement('option');
                opt.value = d.code;
                opt.textContent = d.name;
                opt.style.color = d.color;
                sel.appendChild(opt);
            });
            sel.value = currentDeviceCode;
            updateDeviceSettingsButton();

            sel.addEventListener('change', function() {
                currentDeviceCode = this.value;
                updateDeviceSettingsButton();
                loadRideHistory();
                refreshMap();
            });
        });
}

function updateDeviceSettingsButton() {
    var btn = document.getElementById('btnDeviceSettings');
    if (!btn) return;
    var show = (currentDeviceCode && currentDeviceCode !== 'all') && !AUTH_TOKEN;
    btn.style.display = show ? '' : 'none';
}

// --- Device settings modal ---
function openModal(id)  { document.getElementById(id).classList.add('visible'); }
function closeModal(id) { document.getElementById(id).classList.remove('visible'); }

function switchDevTab(tab) {
    ['info', 'cleanup'].forEach(function(t) {
        document.querySelector('.dev-tab[data-tab="' + t + '"]').classList.toggle('active', t === tab);
        document.getElementById('devTab-' + t).style.display = (t === tab) ? '' : 'none';
    });
}

function openDeviceSettings() {
    var d = devicesByCode[currentDeviceCode];
    if (!d) return;
    document.getElementById('devSettingsHeader').textContent =
        'Editing ' + d.name + ' (' + d.code + ')';
    document.getElementById('devEditName').value  = d.name || '';
    document.getElementById('devEditColor').value = /^#[0-9a-fA-F]{6}$/.test(d.color || '') ? d.color : '#3498db';
    var today = new Date().toISOString().split('T')[0];
    document.getElementById('devDelFrom').value = today;
    document.getElementById('devDelTo').value   = today;
    switchDevTab('info');
    openModal('modalDeviceSettings');
}

function submitDeviceUpdate() {
    var d = devicesByCode[currentDeviceCode];
    if (!d) return;
    var name  = document.getElementById('devEditName').value.trim();
    var color = document.getElementById('devEditColor').value;
    if (!name) { alert('Name is required'); return; }

    var body = new URLSearchParams();
    body.set('device_id', d.id);
    body.set('name', name);
    body.set('color', color);

    authFetch('device/update.php', { method: 'POST', body: body })
        .then(function(r) { return r.json().then(function(j) { return { ok: r.ok, j: j }; }); })
        .then(function(res) {
            if (!res.ok) { alert('Update failed: ' + (res.j.error || 'unknown')); return; }
            closeModal('modalDeviceSettings');
            return loadDevices().then(function() { refreshMap(); });
        });
}

function submitDevDelete(filter) {
    var d = devicesByCode[currentDeviceCode];
    if (!d) return;
    var from = document.getElementById('devDelFrom').value;
    var to   = document.getElementById('devDelTo').value;
    if (!from || !to) { alert('Pick a date range'); return; }

    var msg = filter === 'stationary'
        ? 'Purge stationary points (speed < 1 km/h) for ' + d.name + ' between ' + from + ' and ' + to + '?'
        : 'Delete ALL points for ' + d.name + ' between ' + from + ' and ' + to + '?\nThis cannot be undone.';
    if (!confirm(msg)) return;

    var body = new URLSearchParams();
    body.set('device_id', d.id);
    body.set('from', from);
    body.set('to', to);
    body.set('filter', filter);

    authFetch('device/delete_points.php', { method: 'POST', body: body })
        .then(function(r) { return r.json().then(function(j) { return { ok: r.ok, j: j }; }); })
        .then(function(res) {
            if (!res.ok) { alert('Delete failed: ' + (res.j.error || 'unknown')); return; }
            alert('Deleted ' + res.j.deleted + ' points (+ ' + res.j.cascaded + ' interpolated children).');
            closeModal('modalDeviceSettings');
            loadRideHistory();
            refreshMap();
        });
}

// --- Init ---
document.getElementById('datePicker').value = new Date().toISOString().split('T')[0];
loadCurrentUser().then(function(me) {
    if (!me) return;
    return loadDevices().then(function() {
        loadRideHistory();
        refreshMap();
        startLiveRefresh();
    });
});
