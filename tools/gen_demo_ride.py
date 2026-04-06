#!/usr/bin/env python3
"""
Generate demo motorcycle ride data for MotoTracker GPS database.

Usage:
    python3 gen_demo_ride.py > /tmp/ride.sql
    sudo mysql -u root gps_db_data < /tmp/ride.sql

Or in one line:
    python3 gen_demo_ride.py | ssh malinka 'sudo mysql -u root gps_db_data'

Customization:
    - Edit 'waypoints' list below to change the route
      Format: (latitude, longitude, target_speed_kmh, "description")
    - Edit 'base_hour' / 'base_minute' to change start time
    - Edit 'ride_date' to change the date
    - Edit 'temp_base' for different weather conditions
    - Points are interpolated between waypoints every ~45 seconds
"""

import math
import random

# Motorcycle ride ~100km loop near Szczecin, Poland
# Start: Szczecin -> south through Gryfino -> Chojna -> loop back
# Duration: ~2 hours, points every 30-60 seconds

waypoints = [
    # lat, lon, target_speed (km/h), description
    (53.4285, 14.5528, 0, "Start - Szczecin"),
    (53.4200, 14.5600, 30, "City streets"),
    (53.4050, 14.5700, 50, "Leaving city"),
    (53.3800, 14.5850, 80, "Road south"),
    (53.3500, 14.5900, 90, "Open road"),
    (53.3200, 14.5800, 95, "Countryside"),
    (53.2800, 14.5600, 85, "Through forest"),
    (53.2500, 14.5300, 70, "Approaching Gryfino"),
    (53.2200, 14.4900, 40, "Gryfino town"),
    (53.2100, 14.4700, 35, "Gryfino center"),
    (53.1900, 14.4400, 60, "Leaving Gryfino"),
    (53.1600, 14.4000, 90, "Road to Chojna"),
    (53.1300, 14.3600, 100, "Fast section"),
    (53.1000, 14.3200, 105, "Straight road"),
    (53.0700, 14.2900, 95, "Gentle curves"),
    (53.0500, 14.2700, 50, "Approaching Chojna"),
    (53.0400, 14.2500, 30, "Chojna town"),
    (53.0350, 14.2400, 0, "Coffee stop"),
    (53.0400, 14.2500, 25, "Leaving Chojna"),
    (53.0600, 14.2800, 70, "Road north-east"),
    (53.0900, 14.3200, 90, "Open road"),
    (53.1200, 14.3800, 100, "Fast countryside"),
    (53.1500, 14.4300, 95, "Rolling hills"),
    (53.1800, 14.4800, 85, "Through villages"),
    (53.2100, 14.5100, 75, "Back road"),
    (53.2400, 14.5400, 90, "Main road"),
    (53.2700, 14.5600, 85, "Approaching Szczecin"),
    (53.3000, 14.5700, 70, "Suburbs"),
    (53.3300, 14.5750, 60, "City outskirts"),
    (53.3600, 14.5700, 50, "City traffic"),
    (53.3900, 14.5650, 40, "Urban area"),
    (53.4100, 14.5600, 30, "Almost home"),
    (53.4285, 14.5528, 0, "Finish - Szczecin"),
]

# ---- Settings (edit these) ----
ride_date = "2026-03-30"   # Date of the ride (YYYY-MM-DD)
base_hour = 9              # Start hour (24h format)
base_minute = 0            # Start minute
temp_base = 14.0           # Base temperature (°C)
battery = 4.05             # Starting battery voltage (V)
# ---- End of settings ----

# Interpolate points between waypoints
points = []
current_time_sec = base_hour * 3600 + base_minute * 60

for i in range(len(waypoints) - 1):
    lat1, lon1, spd1, _ = waypoints[i]
    lat2, lon2, spd2, _ = waypoints[i + 1]
    
    # Distance between waypoints
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    dist_deg = math.sqrt(dlat**2 + dlon**2)
    
    # Number of sub-points (roughly every 30-60 seconds of travel)
    avg_speed = max((spd1 + spd2) / 2, 5)  # km/h
    dist_km = dist_deg * 111  # rough deg->km
    time_hours = dist_km / avg_speed
    time_sec = time_hours * 3600
    n_points = max(int(time_sec / 45), 2)  # point every ~45 seconds
    
    for j in range(n_points):
        frac = j / n_points
        lat = lat1 + dlat * frac + random.uniform(-0.0003, 0.0003)
        lon = lon1 + dlon * frac + random.uniform(-0.0004, 0.0004)
        speed = spd1 + (spd2 - spd1) * frac + random.uniform(-5, 5)
        speed = max(0, speed)
        
        # Time progression
        dt = time_sec / n_points
        current_time_sec += dt
        
        h = int(current_time_sec // 3600)
        m = int((current_time_sec % 3600) // 60)
        s = int(current_time_sec % 60)
        
        # Temperature varies slightly during ride
        temp = temp_base + random.uniform(-1, 2) + (current_time_sec - base_hour*3600) / 7200
        
        # Battery slowly decreases
        battery -= random.uniform(0.001, 0.003)
        
        ts = f"{ride_date} {h:02d}:{m:02d}:{s:02d}"
        
        points.append((lat, lon, speed, temp, 0, battery, ts))

# Add final point
lat, lon, spd, desc = waypoints[-1]
current_time_sec += 30
h = int(current_time_sec // 3600)
m = int((current_time_sec % 3600) // 60)
s = int(current_time_sec % 60)
ts = f"{ride_date} {h:02d}:{m:02d}:{s:02d}"
points.append((lat, lon, 0, temp_base + 2, 0, battery, ts))

# Calculate total distance
total_dist = 0
for i in range(1, len(points)):
    dlat = (points[i][0] - points[i-1][0]) * math.pi / 180
    dlon = (points[i][1] - points[i-1][1]) * math.pi / 180
    a = math.sin(dlat/2)**2 + math.cos(points[i-1][0]*math.pi/180) * math.cos(points[i][0]*math.pi/180) * math.sin(dlon/2)**2
    total_dist += 6371 * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

print(f"-- Demo ride: {len(points)} points, ~{total_dist:.1f} km")
print(f"-- From {points[0][6]} to {points[-1][6]}")
print()

# Generate SQL
print("INSERT INTO S_02 (lat, lon, speed, temperature, humidyty, battery, timestamp) VALUES")
values = []
for p in points:
    values.append(f"  ({p[0]:.6f}, {p[1]:.6f}, {p[2]:.1f}, {p[3]:.1f}, {p[4]:.1f}, {p[5]:.2f}, '{p[6]}')")
print(",\n".join(values) + ";")
