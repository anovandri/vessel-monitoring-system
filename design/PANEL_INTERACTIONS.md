# VMS Panel Interaction Guide

## Quick Reference: Which Panel Appears When?

This guide shows which popup/overlay panel appears when each navigation menu item is clicked.

---

## Navigation Menu → Panel Mapping

### 🏠 Dashboard (Active by default)
**Opens**: Dashboard Overview Panel
- **Node ID**: `jRirV`
- **Position**: x:120, y:80
- **Size**: 1240 x 620px
- **Contents**: 
  - 4 metric cards (Total Vessels, In Port, At Sea, Active Alerts)
  - Recent activity feed (3 items)
- **Use Case**: Quick overview of fleet status and recent events

---

### 🗺️ Live Map
**Shows**: Main map view with all overlays
- No specific panel opens
- Displays real-time vessel positions
- Shows alerts panel, map controls, vessel markers
- Primary working view for operators

---

### 🔍 Vessel Search
**Opens**: Vessel Search Panel
- **Node ID**: `OSki6`
- **Position**: x:700, y:80
- **Size**: 520 x 580px
- **Contents**:
  - Search input bar
  - Filter tabs (All, Cargo, Tanker)
  - Vessel results list (3 visible items)
- **Use Case**: Find specific vessels by name, MMSI, or type

---

### 🔔 Alerts
**Shows**: Alerts Panel (always visible)
- **Node ID**: `QT5u5`
- **Position**: x:120, y:740
- **Size**: 360 x 280px
- **Contents**:
  - Header with alert count badge
  - 3 most recent alerts with severity colors
- **Use Case**: Monitor critical system notifications
- **Note**: This panel is persistent and always shown in the corner

---

### 📊 Analytics
**Opens**: Analytics Dashboard Panel
- **Node ID**: `E3exJ`
- **Position**: x:580, y:80
- **Size**: 680 x 600px
- **Contents**:
  - 3 metric cards with trend indicators
  - Traffic over time chart (7 bars)
  - Vessel types distribution (4 types)
- **Use Case**: Review traffic statistics and trends

---

### ⏱️ History Playback
**Opens**: History Playback Panel
- **Node ID**: `9Ak0y`
- **Position**: x:720, y:80
- **Size**: 480 x 700px
- **Contents**:
  - Date range selector (start/end dates)
  - Vessel selection dropdown
  - Playback controls (skip back, play, skip forward)
  - Speed controls (0.5x, 1x, 2x, 4x)
  - Timeline visualization with progress bar
  - Vessel count indicator
- **Use Case**: Review historical vessel movements

---

### ⚙️ Settings
**Opens**: Settings Panel
- **Node ID**: `rkysu`
- **Position**: x:640, y:80
- **Size**: 600 x 780px
- **Contents**:
  - Display Settings (theme, units, grid)
  - Notifications (alerts, sound, email)
  - Data Sources (AIS, satellite status)
  - Account (profile, sign out)
- **Use Case**: Configure system preferences

---

## Additional Panels (Context-Based)

### 📍 Vessel Detail Panel
**Opens When**: User clicks on a vessel marker on the map
- **Node ID**: `85eql`
- **Position**: x:1440, y:80 (right side)
- **Size**: 420 x 740px
- **Contents**:
  - Vessel name and identification (MMSI/IMO)
  - Vessel photo
  - Information grid (type, flag, speed, course)
  - Destination and ETA
  - Tab navigation (Overview, Track History, Analytics, 3D View)
- **Use Case**: View detailed information about a specific vessel

---

### 🎛️ Map Controls
**Always Visible**: Fixed controls for map interaction
- **Node ID**: `M6GaU`
- **Position**: x:120, y:80 (top-left corner)
- **Size**: 44 x 136px
- **Contents**:
  - Zoom In button (+)
  - Zoom Out button (-)
  - Layers button
- **Use Case**: Control map zoom and layer visibility

---

## Panel Interaction Patterns

### Opening Panels
1. Click navigation menu item in sidebar
2. Corresponding panel slides in/fades in on the map overlay
3. Close button (X) appears in top-right corner of panel
4. Panel has glassmorphism effect (semi-transparent with blur)

### Closing Panels
1. Click close button (X) in panel header
2. Click outside panel area (optional behavior)
3. Click different navigation menu item (replaces current panel)
4. Press ESC key (keyboard interaction)

### Multiple Panel States

**Can be open simultaneously:**
- Dashboard Overview + Alerts Panel
- Vessel Search + Alerts Panel
- Analytics + Alerts Panel
- History Playback + Alerts Panel
- Settings + Alerts Panel
- Any main panel + Vessel Detail Panel + Alerts Panel
- Map Controls (always visible)

**Cannot be open simultaneously:**
- Dashboard Overview + Vessel Search (mutually exclusive)
- Dashboard Overview + Analytics (mutually exclusive)
- Vessel Search + Analytics (mutually exclusive)
- Only ONE main content panel at a time

### Panel Priority (Z-Index)
From bottom to top:
1. Map background
2. Vessel markers and tracks
3. Map Controls (M6GaU) - bottom left
4. Alerts Panel (QT5u5) - bottom left
5. Main content panels (Dashboard, Search, Analytics, History, Settings)
6. Vessel Detail Panel (85eql) - right side (appears on top when vessel clicked)

---

## Recommended Panel Layouts

### Default View (Operator Monitoring)
- **Open**: None (just map view)
- **Visible**: Alerts Panel, Map Controls, Bottom Timeline
- **Use**: Active monitoring of vessel movements

### Investigation Mode
- **Open**: Vessel Detail Panel (after clicking vessel)
- **Visible**: Alerts Panel, Map Controls
- **Use**: Detailed inspection of specific vessel

### Search Mode
- **Open**: Vessel Search Panel
- **Visible**: Alerts Panel, Map Controls
- **Use**: Finding specific vessels

### Analysis Mode
- **Open**: Analytics Dashboard Panel
- **Visible**: Alerts Panel
- **Use**: Reviewing traffic patterns and statistics

### Review Mode
- **Open**: History Playback Panel
- **Visible**: Map Controls
- **Use**: Playing back historical vessel tracks

### Configuration Mode
- **Open**: Settings Panel
- **Visible**: None (focus on configuration)
- **Use**: Adjusting system preferences

---

## Panel Behavior Rules

### 1. Navigation Item States
- **Active**: Cyan glow + accent background
- **Hover**: Slight brightness increase
- **Inactive**: Default gray appearance

### 2. Panel Transitions
- **Enter**: Fade in + slide from appropriate direction (0.3s ease)
- **Exit**: Fade out + slide to edge (0.2s ease)
- **Replace**: Cross-fade between panels (0.3s ease)

### 3. Responsive Behavior
- **Desktop (1920x1080)**: All panels as specified
- **Laptop (1366x768)**: Scale panels to 75% size, adjust positions
- **Tablet**: Full-width panels, stacked vertically
- **Mobile**: Full-screen modals for all panels

### 4. Keyboard Navigation
- **Tab**: Cycle through interactive elements
- **ESC**: Close active panel
- **Arrow Keys**: Navigate within lists (search results, activities)
- **Space/Enter**: Activate selected item

---

## Developer Implementation Notes

### State Management
```javascript
// Example panel state structure
const panelState = {
  activeMainPanel: 'dashboard' | 'search' | 'analytics' | 'history' | 'settings' | null,
  vesselDetailOpen: boolean,
  selectedVesselId: string | null,
  alertsPanelVisible: true, // Always visible
  mapControlsVisible: true  // Always visible
}
```

### Panel Component Props
```javascript
// Example React component props
<Panel
  nodeId="jRirV"
  position={{ x: 120, y: 80 }}
  size={{ width: 1240, height: 620 }}
  isOpen={activePanel === 'dashboard'}
  onClose={() => setActivePanel(null)}
  zIndex={100}
/>
```

### CSS Classes
```css
.panel {
  position: absolute;
  background: var(--bg-panel);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.2);
  backdrop-filter: blur(20px);
}

.panel-enter {
  opacity: 0;
  transform: translateY(-20px);
}

.panel-enter-active {
  opacity: 1;
  transform: translateY(0);
  transition: all 0.3s ease;
}
```

---

**Quick Tip**: For the best user experience, implement smooth transitions between panels and ensure only one main content panel is open at a time to avoid screen clutter.
