# Generate file again ensuring STRICT one-line-per-row format
# Use plain-to-plain conversion to avoid any markdown interpretation
# Keep newline strictly as "\n"

import random
import string

areas = [
    ("Malacca Strait", (-3.5, 1.5), (97.0, 102.0)),
    ("Java Sea", (-6.5, -2.0), (106.0, 114.0)),
    ("Banda Sea", (-6.0, -2.5), (125.0, 132.0)),
    ("Arafura Sea", (-9.5, -6.0), (132.0, 140.0)),
    ("Makassar Strait", (-4.0, -1.0), (117.0, 121.0)),
    ("Sunda Strait", (-6.8, -5.5), (104.5, 106.5)),
    ("Lombok Strait", (-9.0, -7.5), (114.0, 116.5)),
    ("Timor Sea", (-10.5, -8.0), (123.0, 128.0)),
]

type_codes = [60, 70, 79, 80, 30, 52, 31, 32, 36, 37]

# Random ship name prefixes
ship_prefixes = [
    "NUSANTARA", "SAMUDERA", "PELNI", "PERTAMINA", "SINAR", "TANTO",
    "MERATUS", "SPIL", "BATAM", "RIAU", "KEPRI", "SUMATRA", "MEDAN",
    "BELAWAN", "DUMAI", "SIAK", "PEKANBARU", "INDRAGIRI", "KAMPAR",
    "ROKAN", "BAGAN", "PANGKALAN", "RUPAT", "BENGKALIS", "SELAT",
    "TANJUNG", "ASAHAN", "LABUHAN", "RANTAU", "KISARAN", "PULAU",
    "SUNGAI", "TELUK", "BENGKULU", "PADANG", "PARIAMAN", "PAINAN",
    "MENTAWAI", "SINGAPORE", "JOHOR", "BINTAN", "KARIMUN", "NATUNA",
    "ANAMBAS", "LINGGA", "SINGKEP", "KEPULAUAN", "MORO", "KUNDUR",
    "GALANG", "BULANG", "LOBAM", "TELAGA", "SENGGARANG", "DOMPAK",
    "SEKUPANG", "NONGSA", "BATU", "HARBOUR", "LUBUK", "BALOI",
    "KABIL", "SAMBAU", "SAGULUNG", "SETOKO", "PUNGGUR", "JAKARTA",
    "MERAK", "BANTEN", "SERANG", "CILEGON", "PANDEGLANG", "TANGERANG",
    "BEKASI", "BOGOR", "DEPOK", "CIREBON", "INDRAMAYU", "SUBANG",
    "PURWAKARTA", "KARAWANG", "BANDUNG", "SUKABUMI", "TASIKMALAYA",
    "GARUT", "CIAMIS", "KUNINGAN", "MAJALENGKA", "SUMEDANG", "CIMAHI",
    "BANJAR", "PANGANDARAN", "PALABUHAN", "PELABUHAN", "UJUNG",
    "CISOLOK", "CIKIDANG", "JAMPANG", "MANONJAYA", "PAMEUNGPEUK",
    "CIBALONG", "CIKELET", "KALIPUCANG", "CIPATUJAH", "CIJULANG"
]

# Random ship name suffixes
ship_suffixes = [
    "EXPRESS", "CARRIER", "LINER", "FREIGHT", "CARGO", "SHIP",
    "VESSEL", "FERRY", "TANKER", "LINE", "TRADER", "NAVIGATOR",
    "VOYAGER", "EXPLORER", "PIONEER", "MERCHANT", "ENTERPRISE",
    "FORTUNE", "HARMONY", "PROSPERITY", "VICTORY", "SPIRIT",
    "PRIDE", "STAR", "OCEAN", "WAVE", "MARINE", "MARITIME",
    "PACIFIC", "ATLANTIC", "MAKMUR", "JAYA", "SEJAHTERA"
]

lines = []

# Exact header (no extra spaces)
header = "MMSI|Ship Name|IMO|Call Sign|Type Code|Latitude|Longitude|Area"
lines.append(header)

# Generate unique ship names
used_names = set()

for i in range(1000):
    mmsi = 525000001 + i
    imo = 220150001 + i
    
    # Generate unique ship name
    ship_name = None
    attempts = 0
    while ship_name is None or ship_name in used_names:
        if attempts < 700 and random.random() < 0.7:  # 70% chance of prefix + suffix
            ship_name = f"{random.choice(ship_prefixes)} {random.choice(ship_suffixes)}"
        elif attempts < 900:  # Try prefix with number
            ship_name = f"{random.choice(ship_prefixes)} {random.randint(1, 999):03d}"
        else:  # Fallback to guaranteed unique name
            ship_name = f"{random.choice(ship_prefixes)} {i+1}"
        attempts += 1
        
        # Safety break to prevent infinite loop
        if attempts > 1000:
            ship_name = f"VESSEL {mmsi}"
            break
    
    used_names.add(ship_name)
    
    call_sign = "YB" + ''.join(random.choices(string.ascii_uppercase, k=2))
    type_code = type_codes[i % len(type_codes)]
    
    area_name, lat_range, lon_range = areas[i % len(areas)]
    lat = round(random.uniform(*lat_range), 6)
    lon = round(random.uniform(*lon_range), 6)
    
    row = f"{mmsi}|{ship_name}|{imo}|{call_sign}|{type_code}|{lat}|{lon}|{area_name}"
    lines.append(row)

# Join strictly with single newline character
text_content = "\n".join(lines) + "\n"

output_path = "indonesia_mock_ais_1000_v4.txt"

# Write directly to file
with open(output_path, 'w', encoding='utf-8') as f:
    f.write(text_content)

print(f"Generated {len(lines)} lines (including header) to {output_path}")