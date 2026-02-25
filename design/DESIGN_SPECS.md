# VMS Design Technical Specifications

## Node ID Reference

This document provides the technical node IDs and structure for developers implementing the VMS design.

### Main Structure

```
5fCAq - Main Screen (1920x1080)
├── ffFyj - Sidebar (80px wide)
│   ├── QBqjK - Nav Top (logo area)
│   ├── khB33 - Nav Items Container
│   │   ├── Dashboard nav item (active with cyan glow)
│   │   ├── Live Map nav item
│   │   ├── Vessel Search nav item
│   │   ├── Alerts nav item
│   │   ├── Analytics nav item
│   │   ├── History nav item
│   │   └── Settings nav item
│   └── wdMBQ - Nav Bottom (user/support section)
│
└── Wua39 - Main Container
    ├── iJIbi - Top Bar (64px height)
    │   ├── Search bar (480px wide)
    │   ├── Live indicator
    │   ├── Notification bell
    │   └── User profile
    │
    └── L0X2m - Map Area
        ├── WumUZ - Map Overlay (layout: "none" for absolute positioning)
        │   ├── BTdjc - Vessel marker 1
        │   ├── tNgBB - Vessel marker 2
        │   ├── XRyWB - Vessel marker 3
        │   ├── ... (7 total vessel markers)
        │   │
        │   ├── 85eql - Vessel Detail Panel (x:1440, y:80, 420x740px)
        │   ├── QT5u5 - Alerts Panel (x:120, y:740, 360x280px)
        │   ├── M6GaU - Map Controls (x:120, y:80, 44x136px)
        │   ├── OSki6 - Vessel Search Panel (x:700, y:80, 520x580px)
        │   ├── E3exJ - Analytics Dashboard Panel (x:580, y:80, 680x600px)
        │   ├── 9Ak0y - History Playback Panel (x:720, y:80, 480x700px)
        │   ├── rkysu - Settings Panel (x:640, y:80, 600x780px)
        │   └── jRirV - Dashboard Overview Panel (x:120, y:80, 1240x620px)
        │
        └── KvOkF - Bottom Overlay (y:936, height:80px)
            ├── Play button
            ├── Time slider
            ├── Current time display
            └── Live statistics
```

## Panel Details with Node IDs

### 1. Dashboard Overview Panel (jRirV)

**Position**: x:120, y:80, 1240x620px

**Structure**:
```
jRirV - Dashboard Panel
├── gCebz - Header
│   ├── g7XIy - Title: "Dashboard Overview"
│   └── 2onkc - Close button
│       └── t8eBH - Close icon (x)
│
├── Ahw11 - Metrics Row
│   ├── rllwT - Metric 1 (Total Vessels)
│   │   ├── i88Zp - Ship icon
│   │   ├── NHGRZ - Value: "247"
│   │   └── Zhw44 - Label: "Total Vessels"
│   │
│   ├── f5XGu - Metric 2 (In Port)
│   │   ├── CyKem - Anchor icon
│   │   ├── ande6 - Value: "89"
│   │   └── XaNSn - Label: "In Port"
│   │
│   ├── 2MKjq - Metric 3 (At Sea)
│   │   ├── I9Mhv - Waves icon
│   │   ├── kN9r4 - Value: "158"
│   │   └── TDWvx - Label: "At Sea"
│   │
│   └── svjYp - Metric 4 (Active Alerts)
│       ├── BJy4i - Alert icon
│       ├── g51nl - Value: "3"
│       └── MP5mb - Label: "Active Alerts"
│
└── XHP6c - Recent Activity Section
    ├── GTlaJ - Section title: "Recent Activity"
    └── vfunJ - Activity List
        ├── OT9Yq - Activity 1
        │   ├── 3w75T - Icon container
        │   │   └── kdTYG - Ship icon
        │   └── HfGOv - Content
        │       ├── sUwBf - Title
        │       └── HzLfm - Time
        │
        ├── xMGAG - Activity 2
        │   ├── qVKWi - Icon container
        │   │   └── abqM5 - Alert icon
        │   └── h6dTX - Content
        │       ├── oRf1z - Title
        │       └── 0RQB2 - Time
        │
        └── ILmRd - Activity 3
            ├── aGyga - Icon container
            │   └── 4BgoJ - Warning icon
            └── IBx4B - Content
                ├── kEO9u - Title
                └── a5nvq - Time
```

### 2. Settings Panel (rkysu)

**Position**: x:640, y:80, 600x780px

**Structure**:
```
rkysu - Settings Panel
├── 0IXSE - Header
│   ├── lcQMX - Title: "Settings"
│   └── zLOAY - Close button
│       └── nwzdR - Close icon
│
├── krB9g - Display Settings Section
│   ├── wU0lj - Section header
│   ├── I96ln - Theme Row
│   │   ├── wvYsQ - Label: "Theme"
│   │   └── qUr02 - Theme Toggle
│   │       ├── 6iJSH - Dark button (active)
│   │       │   └── 5jRXj - "Dark" text
│   │       └── A6wZW - Light button
│   │           └── GjPnt - "Light" text
│   │
│   ├── kS4hF - Units Row
│   │   ├── zJSXV - Label: "Speed Units"
│   │   └── 3zDKA - Units Select
│   │       ├── 8jfdg - "Knots" text
│   │       └── DfZIV - Chevron icon
│   │
│   └── jBfq8 - Map Layers Row
│       ├── vCIcg - Label: "Show Grid Lines"
│       └── 4538I - Toggle Switch
│           └── jTs98 - Switch thumb
│
├── 9jHxP - Notifications Section
│   ├── kMUPI - Section header
│   ├── i6yUO - Critical Alerts Row
│   │   ├── ibqu1 - Label
│   │   └── MDuNe - Switch (ON)
│   │       └── n8APU - Thumb
│   │
│   ├── lCVdE - Sound Alerts Row
│   │   ├── PAPyw - Label
│   │   └── x7fhF - Switch (OFF)
│   │       └── UTbgf - Thumb
│   │
│   └── kaeaL - Email Notifications Row
│       ├── UjyGB - Label
│       └── AHtXz - Switch (ON)
│           └── wHPMI - Thumb
│
├── 1GkNw - Data Sources Section
│   ├── Sw48O - Section header
│   ├── zrgV8 - AIS Stream Row
│   │   ├── MWNyC - Label
│   │   └── iT87k - Status
│   │       ├── dbjyA - Indicator (green)
│   │       └── 85BIw - "Connected" text
│   │
│   └── 0h3K5 - Satellite Data Row
│       ├── H4bRa - Label
│       └── bihie - Status
│           ├── aDQFA - Indicator (green)
│           └── 2tGhf - "Active" text
│
└── mjSQ5 - Account Section
    ├── SAlx1 - Section header
    ├── AuNts - Profile Row
    │   ├── Qxd8c - "Profile Settings" label
    │   └── rAWCP - Chevron icon
    │
    └── G7jq3 - Logout Row
        ├── BRTtO - "Sign Out" label (red)
        └── lsbER - Logout icon (red)
```

### 3. History Playback Panel (9Ak0y)

**Position**: x:720, y:80, 480x700px

**Structure**:
```
9Ak0y - History Playback Panel
├── RIBLT - Header
│   ├── WO0Wl - Title: "History Playback"
│   └── EsLV1 - Close button
│       └── IeDjY - Close icon
│
├── HR4zJ - Date Range Section
│   └── G1CFV - Date Inputs Container
│       ├── gifeM - Start Date Input
│       │   └── PHEh5 - Calendar icon + "Feb 18, 2026" text
│       └── ... - End Date Input
│           └── ... - Calendar icon + "Feb 25, 2026" text
│
├── PdDB2 - Vessel Select Section
│   ├── 91ed9 - Label: "Select Vessel"
│   └── P7dM0 - Select Input
│       ├── EhLbJ - "MV PACIFIC EXPLORER" text
│       └── WT2kc - Chevron icon
│
├── tGmJ7 - Playback Controls Section
│   ├── H57Si - Section title: "Playback Controls"
│   ├── Sv49c - Playback Buttons
│   │   ├── aTLZe - Skip Back Button
│   │   │   └── AIgl9 - Skip back icon
│   │   ├── 2nkGe - Play Button (large, cyan)
│   │   │   └── baFQv - Play icon
│   │   └── KzWXY - Skip Forward Button
│   │       └── ekPyo - Skip forward icon
│   │
│   └── 0X4qk - Speed Control
│       ├── 3pR0i - 0.5x button
│       │   └── 9btPG - "0.5x" text
│       ├── ltaqq - 1x button (active)
│       │   └── n1p9t - "1x" text
│       ├── 0x0VN - 2x button
│       │   └── xkYcr - "2x" text
│       └── kkFCG - 4x button
│           └── 2FhC8 - "4x" text
│
├── hk08L - Timeline Section
│   ├── O1XIl - Section title: "Timeline"
│   └── ogdX4 - Timeline Visual
│       ├── qJ5M5 - Background
│       ├── lirMG - Progress (180px filled)
│       ├── 2SLZv - Timeline marker (vertical cyan line)
│       ├── Uhbpm - Start label: "Feb 18"
│       └── JGnzP - End label: "Feb 25"
│
└── WE3W1 - Vessel Count Info
    ├── hfFOI - Ship icon
    └── pG7TN - "7 Vessels in Playback" text
```

### 4. Vessel Search Panel (OSki6)

**Position**: x:700, y:80, 520x580px

**Node IDs**:
- `OSki6` - Main panel container
- `uxCqw` - Search input
- `34siv` - Filter tabs (All/Cargo/Tanker)
- `iJz0a` - Results list (3 vessel items)

### 5. Analytics Dashboard Panel (E3exJ)

**Position**: x:580, y:80, 680x600px

**Node IDs**:
- `E3exJ` - Main panel container
- `emPSh` - Metrics grid (3 cards)
- `mQBPE` - Traffic chart section (7 bars)
- `167I1` - Vessel types distribution (4 types)

### 6. Vessel Detail Panel (85eql)

**Position**: x:1440, y:80, 420x740px

**Node ID**: `85eql`

Contains: Header, MMSI/IMO, vessel image, info grid, tabs, last update

### 7. Alerts Panel (QT5u5)

**Position**: x:120, y:740, 360x280px

**Node ID**: `QT5u5`

Contains: Header with badge, 3 alert items

### 8. Map Controls (M6GaU)

**Position**: x:120, y:80, 44x136px

**Node ID**: `M6GaU`

Contains: Zoom in, zoom out, layers buttons

## Design Variables

```javascript
{
  "bg-page": "#0A0E1A",
  "bg-panel": "#0F1420",
  "bg-sidebar": "#060912",
  "accent-cyan": "#00E5FF",
  "accent-cyan-dim": "#00E5FF40",
  "text-primary": "#E8F0F8",
  "text-secondary": "#8B9DAF",
  "text-tertiary": "#556677",
  "success": "#00FF88",
  "danger": "#FF3D71",
  "warning": "#FFB800",
  "info": "#4A90E2",
  "border": "#1A2332"
}
```

## Layout Properties

### Flexbox Layouts
- **Horizontal**: `layout: "horizontal"` - for row-based layouts
- **Vertical**: `layout: "vertical"` - for column-based layouts
- **None**: `layout: "none"` - for absolute positioning (used in map overlay)

### Sizing
- **Fill Container**: `width: "fill_container"` - expands to parent width
- **Fit Content**: `height: "fit_content(0)"` - adjusts to content height
- **Fixed**: Specific pixel values (e.g., `width: 480`)

### Common Gaps
- 4px - Tight spacing
- 8px - Small spacing
- 12px - Medium spacing
- 16px - Standard spacing
- 24px - Large spacing

### Common Padding
- 12px - Compact elements
- 16px - Standard elements
- 20px - Comfortable spacing
- 24px - Spacious layouts

## Typography Scale

- **Headings**: 18px (panel titles), 15px (section headers)
- **Body**: 13px (standard text)
- **Small**: 12px (metadata, timestamps)
- **Large**: 32px (metric values)

## Border Radius

- 4px - Small elements (indicators)
- 6px - Small buttons
- 8px - Standard buttons, inputs
- 12px - Cards, panels (inner)
- 16px - Main panels (outer)
- 28px - Circular buttons (half of size)

## Icon Sizes

- 16px - Small indicators
- 18px - Standard icons
- 20px - Panel icons
- 24px - Navigation icons, feature icons

## Implementation Notes

### Panel Visibility Logic
Each panel should be associated with its corresponding navigation item:
- Dashboard panel → Dashboard nav item
- Vessel Search panel → Search nav item
- Analytics panel → Analytics nav item
- History panel → History nav item
- Settings panel → Settings nav item

### Active States
- Active nav items: Cyan background + glow effect
- Active toggles: Cyan fill with white thumb at x:18
- Inactive toggles: Gray fill with gray thumb at x:2
- Active buttons: Cyan background, bold text
- Inactive buttons: Gray background, normal text

### Z-Index Layering
1. Base map layer (lowest)
2. Vessel markers
3. Map overlay panels
4. Top bar
5. Bottom overlay
6. Modals/Dialogs (highest)

### Responsive Breakpoints
While designed for 1920x1080, consider these adjustments:
- Tablet: Stack some panels vertically
- Mobile: Full-screen panels, collapsible sidebar
- 4K: Scale up spacing proportionally

---

**For Development Questions**: Refer to the main README.md for design principles and user workflows.
