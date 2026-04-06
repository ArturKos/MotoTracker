// ============================================================
// MotoTracker - map.js
// ============================================================

// --- Map setup ---
var map = L.map('map', { zoomControl: true }).setView([52.0694, 19.4800], 7);
var markersLayer = L.layerGroup().addTo(map);
var speedSegments = L.layerGroup().addTo(map);
var replayMarker = null;
var currentPoints = [];
var replayIndex = 0;
var replayTimer = null;
var isPlaying = false;

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

// --- Layer switcher ---
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

// --- Haversine distance (km) ---
function haversine(lat1, lon1, lat2, lon2) {
    var R = 6371;
    var dLat = (lat2 - lat1) * Math.PI / 180;
    var dLon = (lon2 - lon1) * Math.PI / 180;
    var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// --- Speed color: green -> yellow -> red ---
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

// --- Get speeds: use GPS speed from DB if available, otherwise calculate ---
function computeSpeeds(points) {
    var hasGpsSpeed = points.some(function(p) { return p.speed !== null && p.speed > 0; });

    if (hasGpsSpeed) {
        // Use real GPS speed from device (more accurate than calculation)
        return points.map(function(p) {
            return (p.speed !== null) ? p.speed : 0;
        });
    }

    // Fallback: calculate speed from distance/time between points
    var speeds = [0];
    for (var i = 1; i < points.length; i++) {
        var dist = haversine(points[i-1].lat, points[i-1].lon, points[i].lat, points[i].lon);
        var t1 = new Date(points[i-1].timestamp).getTime();
        var t2 = new Date(points[i].timestamp).getTime();
        var hours = (t2 - t1) / 3600000;
        if (hours > 0) {
            speeds.push(dist / hours);
        } else {
            speeds.push(0);
        }
    }
    return speeds;
}

// --- Custom marker icons ---
function createDivIcon(html, className) {
    return L.divIcon({
        html: html,
        className: 'custom-marker ' + className,
        iconSize: [30, 30],
        iconAnchor: [15, 15]
    });
}

var startIcon = createDivIcon('<i class="fas fa-flag"></i>', 'start-marker');
var finishIcon = createDivIcon('<i class="fas fa-flag-checkered"></i>', 'finish-marker');
var motoIcon = createDivIcon('<i class="fas fa-motorcycle"></i>', 'moto-icon');

// --- Format duration ---
function formatDuration(ms) {
    var totalSec = Math.floor(ms / 1000);
    var h = Math.floor(totalSec / 3600);
    var m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return h + 'h ' + m + 'm';
    return m + ' min';
}

// --- Update stats panel ---
function updateStats(points, speeds) {
    if (points.length < 2) {
        document.getElementById('statDistance').textContent = '--';
        document.getElementById('statDuration').textContent = '--';
        document.getElementById('statAvgSpeed').textContent = '--';
        document.getElementById('statMaxSpeed').textContent = '--';
        document.getElementById('statTemp').textContent = '--';
        document.getElementById('statBattery').textContent = '--';
        return;
    }

    var totalDist = 0;
    for (var i = 1; i < points.length; i++) {
        totalDist += haversine(points[i-1].lat, points[i-1].lon, points[i].lat, points[i].lon);
    }

    var t0 = new Date(points[0].timestamp).getTime();
    var tN = new Date(points[points.length - 1].timestamp).getTime();
    var duration = tN - t0;

    var maxSpeed = 0;
    for (var j = 0; j < speeds.length; j++) {
        if (speeds[j] > maxSpeed) maxSpeed = speeds[j];
    }

    var avgSpeed = (duration > 0) ? totalDist / ((duration) / 3600000) : 0;

    // Filter out unrealistic speeds (GPS glitches)
    var realisticMax = Math.min(maxSpeed, 300);

    var lastPoint = points[points.length - 1];

    document.getElementById('statDistance').textContent = totalDist.toFixed(1) + ' km';
    document.getElementById('statDuration').textContent = formatDuration(duration);
    document.getElementById('statAvgSpeed').textContent = avgSpeed.toFixed(0) + ' km/h';
    document.getElementById('statMaxSpeed').textContent = realisticMax.toFixed(0) + ' km/h';
    document.getElementById('statTemp').textContent = lastPoint.temperature.toFixed(1) + ' °C';
    document.getElementById('statBattery').textContent = lastPoint.battery.toFixed(1) + ' V';
}

// --- Draw speed-colored route ---
function drawSpeedRoute(points, speeds) {
    speedSegments.clearLayers();

    if (points.length < 2) return;

    var maxSpeed = 0;
    for (var i = 0; i < speeds.length; i++) {
        if (speeds[i] < 300 && speeds[i] > maxSpeed) maxSpeed = speeds[i];
    }

    // Draw colored segments
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
        speedSegments.addLayer(segment);
    }

    // Direction arrows every N points
    var arrowInterval = Math.max(1, Math.floor(points.length / 20));
    for (var k = arrowInterval; k < points.length; k += arrowInterval) {
        var p1 = points[k - 1];
        var p2 = points[k];
        var angle = Math.atan2(p2.lon - p1.lon, p2.lat - p1.lat) * 180 / Math.PI;

        var arrowIcon = L.divIcon({
            html: '<i class="fas fa-chevron-up" style="color: white; font-size: 10px; transform: rotate(' + angle + 'deg); opacity: 0.7;"></i>',
            className: '',
            iconSize: [12, 12],
            iconAnchor: [6, 6]
        });
        L.marker([p2.lat, p2.lon], { icon: arrowIcon, interactive: false }).addTo(speedSegments);
    }

    // Update legend
    document.getElementById('legendMax').textContent = maxSpeed.toFixed(0) + ' km/h';
    document.getElementById('speed-legend').classList.add('visible');
}

// --- Draw full ride ---
function drawRide(points) {
    currentPoints = points;
    markersLayer.clearLayers();
    speedSegments.clearLayers();
    stopReplay();

    if (!points || points.length === 0) {
        updateStats([], []);
        document.getElementById('speed-legend').classList.remove('visible');
        return;
    }

    var speeds = computeSpeeds(points);
    updateStats(points, speeds);
    drawSpeedRoute(points, speeds);

    // Start marker
    L.marker([points[0].lat, points[0].lon], { icon: startIcon })
        .bindPopup('<b>Start</b><br>' + points[0].timestamp)
        .addTo(markersLayer);

    // Finish marker
    if (points.length > 1) {
        var last = points[points.length - 1];
        L.marker([last.lat, last.lon], { icon: finishIcon })
            .bindPopup('<b>Finish</b><br>' + last.timestamp)
            .addTo(markersLayer);
    }

    // Fit bounds
    var latlngs = points.map(function(p) { return [p.lat, p.lon]; });
    map.fitBounds(latlngs, { padding: [30, 30] });
}

// --- Fetch & display ride for a date ---
function refreshMap() {
    var selectedDate = document.getElementById('datePicker').value;

    fetch('pobierz_punkty.php?date=' + encodeURIComponent(selectedDate))
        .then(function(r) { return r.json(); })
        .then(function(points) {
            if (Array.isArray(points)) {
                drawRide(points);
                highlightActiveRide(selectedDate);
            }
        })
        .catch(function(err) { console.error('Error:', err); });
}

// --- Load ride by date (from history click) ---
function loadRide(date) {
    document.getElementById('datePicker').value = date;
    refreshMap();
}

// --- Highlight active ride in sidebar ---
function highlightActiveRide(date) {
    document.querySelectorAll('.ride-item').forEach(function(item) {
        item.classList.remove('active');
        if (item.dataset.date === date) {
            item.classList.add('active');
        }
    });
}

// --- Ride history ---
function loadRideHistory() {
    var rideList = document.getElementById('rideList');
    rideList.innerHTML = '<div class="loading"><i class="fas fa-spinner"></i> Loading...</div>';

    fetch('pobierz_historie.php')
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
                item.onclick = function() { loadRide(ride.ride_date); };

                var dateObj = new Date(ride.ride_date + 'T00:00:00');
                var dayName = dateObj.toLocaleDateString('en-US', { weekday: 'short' });
                var dateStr = dateObj.toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' });

                var startTime = ride.start_time ? ride.start_time.split(' ')[1] : '';
                var endTime = ride.end_time ? ride.end_time.split(' ')[1] : '';
                if (startTime) startTime = startTime.substring(0, 5);
                if (endTime) endTime = endTime.substring(0, 5);

                item.innerHTML =
                    '<div class="ride-icon"><i class="fas fa-route"></i></div>' +
                    '<div class="ride-info">' +
                        '<div class="ride-date">' + dayName + ', ' + dateStr + '</div>' +
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

// --- Ride replay ---
function toggleReplay() {
    if (isPlaying) {
        pauseReplay();
    } else {
        startReplay();
    }
}

function startReplay() {
    if (currentPoints.length < 2) return;

    isPlaying = true;
    document.getElementById('replayIcon').className = 'fas fa-pause';

    if (!replayMarker) {
        replayMarker = L.marker(
            [currentPoints[0].lat, currentPoints[0].lon],
            { icon: motoIcon, zIndexOffset: 1000 }
        ).addTo(map);
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
    if (replayTimer) {
        clearTimeout(replayTimer);
        replayTimer = null;
    }
}

function stopReplay() {
    pauseReplay();
    replayIndex = 0;
    document.getElementById('replayProgress').style.width = '0%';
    if (replayMarker) {
        map.removeLayer(replayMarker);
        replayMarker = null;
    }
}

document.getElementById('replaySpeed').addEventListener('input', function() {
    document.getElementById('speedLabel').textContent = this.value + 'x';
});

// --- GPX export ---
function exportGPX() {
    var date = document.getElementById('datePicker').value || '';
    window.open('export_gpx.php?date=' + encodeURIComponent(date), '_blank');
}

// --- Fullscreen ---
function toggleFullscreen() {
    document.body.classList.toggle('fullscreen');
    var icon = document.querySelector('#btnFullscreen i');
    if (document.body.classList.contains('fullscreen')) {
        icon.className = 'fas fa-compress';
    } else {
        icon.className = 'fas fa-expand';
    }
    setTimeout(function() { map.invalidateSize(); }, 300);
}

// --- Sidebar toggle ---
function toggleSidebar() {
    var sidebar = document.getElementById('sidebar');
    var toggle = document.getElementById('sidebar-toggle');
    var icon = document.getElementById('sidebarIcon');

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

// --- Live tracking: auto-refresh every 2 seconds for today ---
var liveTimer = null;

function isToday(dateStr) {
    return dateStr === new Date().toISOString().split('T')[0];
}

function startLiveRefresh() {
    stopLiveRefresh();
    liveTimer = setInterval(function() {
        var selectedDate = document.getElementById('datePicker').value;
        if (isToday(selectedDate)) {
            fetch('pobierz_punkty.php?date=' + encodeURIComponent(selectedDate))
                .then(function(r) { return r.json(); })
                .then(function(points) {
                    if (Array.isArray(points) && points.length > 0) {
                        // Only redraw if point count changed (new data)
                        if (points.length !== currentPoints.length) {
                            drawRide(points);
                            // Pan to latest point
                            var last = points[points.length - 1];
                            map.panTo([last.lat, last.lon]);
                        }
                    }
                })
                .catch(function(err) { console.error('Live refresh error:', err); });
        }
    }, 2000);
}

function stopLiveRefresh() {
    if (liveTimer) {
        clearInterval(liveTimer);
        liveTimer = null;
    }
}

// Restart live refresh when date changes
document.getElementById('datePicker').addEventListener('change', function() {
    if (isToday(this.value)) {
        startLiveRefresh();
    } else {
        stopLiveRefresh();
    }
});

// --- Init ---
document.getElementById('datePicker').value = new Date().toISOString().split('T')[0];
loadRideHistory();
refreshMap();
startLiveRefresh();
