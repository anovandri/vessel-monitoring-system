# Vessel Monitoring System (VMS) UI Design

## Overview
This is a modern, professional web application UI design for a **Vessel Monitoring System (VMS)** used for maritime operations and fleet tracking. The design features a dark maritime theme with cyan accents, providing optimal visibility for 24/7 monitoring operations.

## Design Specifications

### Screen Dimensions
- **Resolution**: 1920 x 1080 pixels
- **Aspect Ratio**: 16:9 (Full HD)

### Color System
The design uses a custom maritime dark theme with the following color variables:

- **Background Colors**:
  - `bg-page`: #0A0E1A (Deep dark blue - main background)
  - `bg-panel`: #0F1420 (Panel background)
  - `bg-sidebar`: #060912 (Sidebar background)

- **Accent Colors**:
  - `accent-cyan`: #00E5FF (Primary accent - bright cyan)
  - `accent-cyan-dim`: #00E5FF40 (Dimmed cyan with 25% opacity)

- **Text Colors**:
  - `text-primary`: #E8F0F8 (Primary text - light gray-blue)
  - `text-secondary`: #8B9DAF (Secondary text - medium gray-blue)
  - `text-tertiary`: #556677 (Tertiary text - darker gray-blue)

- **Status Colors**:
  - `success`: #00FF88 (Green - for active/positive states)
  - `danger`: #FF3D71 (Red - for alerts/errors)
  - `warning`: #FFB800 (Orange - for warnings)
  - `info`: #4A90E2 (Blue - for informational elements)

- **Border**:
  - `border`: #1A2332 (Dark blue-gray for borders)

### Typography
- **Primary Font**: Inter (UI elements, body text)
- **Secondary Font**: Roboto Mono (Technical data, coordinates, MMSI numbers)

### Icon System
- **Icon Library**: Lucide (icon_font)
- Clean, modern line icons with consistent stroke weights

## Layout Structure

### Main Components

#### 1. Sidebar Navigation (80px wide)
Located on the left side, contains icon-based vertical navigation:

**Navigation Items:**
- 🏠 **Dashboard** - Overview of all vessel activities (active state with cyan glow)
- 🗺️ **Live Map** - Real-time vessel tracking view
- 🔍 **Vessel Search** - Search and filter vessels
- 🔔 **Alerts** - System alerts and notifications
- 📊 **Analytics** - Traffic statistics and analytics
- ⏱️ **History Playback** - Historical vessel track playback
- ⚙️ **Settings** - System configuration

**Features:**
- Active state indicated by cyan glow effect and accent background
- Icon-only design for compact space usage
- Dark sidebar background (#060912) for clear separation

#### 2. Top Bar (height: 64px)
Fixed header spanning the width of the main content area:

**Components:**
- **Search Bar** (480px wide): "Search vessel by name, MMSI, IMO..."
- **Live Data Indicator**: Green dot + "Live Data" label with pulse animation
- **Notification Bell**: Icon with notification count badge
- **User Profile**: Avatar circle with user identification

#### 3. Map Area
Main central area displaying the interactive maritime map:

**Background:**
- Dark maritime blue (#2A3B4D)
- Grid overlay system for coordinate reference
- Scale bar at bottom left

**Map Elements:**
- **Vessel Markers**: 7 vessels positioned across the map with rotation indicators
- **Vessel Tracks**: Dashed lines showing recent vessel paths
- **Glow Effects**: Cyan glows around active vessels for visibility
- **Coordinate Labels**: Positioned at strategic points (e.g., "15.2°N, 112.5°E")

#### 4. Bottom Overlay (height: 80px)
Fixed timeline control bar at the bottom:

**Components:**
- **Play/Pause Button**: For timeline playback control
- **Time Slider**: Horizontal slider for time navigation
- **Current Time Display**: "Feb 25, 2026 • 14:30 UTC"
- **Live Statistics**:
  - 247 Active Vessels
  - 12.4k Positions/hour
  - 3 Active Alerts

## Interactive Panels

All panels appear as floating overlay panels on the map area with the following shared characteristics:
- **Background**: `$bg-panel` (#0F1420)
- **Border**: 1px solid `$border` (#1A2332)
- **Corner Radius**: 16px
- **Shadow**: 40px blur, 8px offset Y, rgba(0,0,0,0.2)
- **Close Button**: X icon in top-right corner

### Panel Details

#### 1. Dashboard Overview Panel
**Position**: x: 120, y: 80
**Size**: 1240 x 620px

**Contents:**
- **4 Metric Cards** (horizontal layout):
  - Total Vessels: 247 (ship icon, cyan)
  - In Port: 89 (anchor icon, blue)
  - At Sea: 158 (waves icon, green)
  - Active Alerts: 3 (alert icon, red)

- **Recent Activity Section**:
  - Activity 1: "MV PACIFIC EXPLORER entered port" (2 minutes ago) - cyan icon
  - Activity 2: "AIS signal lost for SEA PHOENIX" (5 minutes ago) - red alert icon
  - Activity 3: "Speed anomaly detected on OCEAN CARRIER" (12 minutes ago) - orange warning icon

**Purpose**: Quick overview of fleet status when clicking Dashboard menu

---

#### 2. Vessel Detail Panel
**Position**: x: 1440, y: 80
**Size**: 420 x 740px

**Contents:**
- **Header**: Vessel name "MV PACIFIC EXPLORER" with edit icon
- **MMSI/IMO Data**: MMSI: 538005989 | IMO: 9876543
- **Vessel Image**: Full-width hero image from Unsplash
- **Information Grid**:
  - Type: Cargo Ship
  - Flag: Panama
  - Speed: 14.2 kn
  - Course: 245°
- **Destination Info**: Port of Singapore | ETA: Feb 26, 08:30 UTC
- **Tab Navigation**: Overview (active), Track History, Analytics, 3D View
- **Last Update**: "Last update: 2 min ago"

**Purpose**: Detailed information when a vessel marker is selected

---

#### 3. Vessel Search Panel
**Position**: x: 700, y: 80
**Size**: 520 x 580px

**Contents:**
- **Search Input**: "Enter vessel name or MMSI..." with search icon
- **Filter Tabs**:
  - All (247) - active
  - Cargo (89)
  - Tanker (53)
- **Search Results** (3 vessels shown):
  - MV PACIFIC EXPLORER: MMSI 538005989, Cargo, 14.2 kn
  - MV ATLANTIC BREEZE: MMSI 241829800, Tanker, 8.5 kn
  - MV OCEAN VOYAGER: MMSI 352001456, Cargo, 11.2 kn

**Purpose**: Search and filter vessels when clicking Vessel Search menu

---

#### 4. Analytics Dashboard Panel
**Position**: x: 580, y: 80
**Size**: 680 x 600px

**Contents:**
- **3 Metric Cards** (top row):
  - 247 Active Vessels (+12%)
  - 89 In Port (-6%)
  - 158 At Sea

- **Traffic Over Time Chart**:
  - 7 vertical bars showing weekly traffic data
  - "Last 7 days" dropdown selector
  - Highlighted current day in bright cyan

- **Vessel Types Distribution**:
  - Cargo Ships: 89 (cyan dot)
  - Tankers: 53 (green dot)
  - Passenger: 41 (blue dot)
  - Fishing: 64 (orange dot)

**Purpose**: Statistical overview when clicking Analytics menu

---

#### 5. History Playback Panel
**Position**: x: 720, y: 80
**Size**: 480 x 700px

**Contents:**
- **Date Range Section**:
  - Start Date: Feb 18, 2026 (calendar picker)
  - End Date: Feb 25, 2026 (calendar picker)

- **Vessel Selection**:
  - Dropdown: "MV PACIFIC EXPLORER" (with chevron-down icon)

- **Playback Controls**:
  - Skip Back button
  - Large Play button (cyan circular, 56px)
  - Skip Forward button

- **Speed Controls** (horizontal button group):
  - 0.5x
  - 1x (active - cyan background)
  - 2x
  - 4x

- **Timeline Visualization**:
  - Progress bar (180px filled)
  - Timeline marker (vertical cyan line)
  - Start label: "Feb 18"
  - End label: "Feb 25"

- **Vessel Count Info**: Ship icon + "7 Vessels in Playback"

**Purpose**: Historical vessel track playback when clicking History menu

---

#### 6. Settings Panel
**Position**: x: 640, y: 80
**Size**: 600 x 780px

**Contents:**

**Display Settings:**
- Theme Toggle: Dark (active) / Light
- Speed Units Dropdown: "Knots" (with chevron-down)
- Show Grid Lines: Toggle switch (ON - cyan)

**Notifications:**
- Critical Alerts: Toggle switch (ON - cyan)
- Sound Alerts: Toggle switch (OFF - gray)
- Email Notifications: Toggle switch (ON - cyan)

**Data Sources:**
- AIS Stream: Status indicator (Connected - green)
- Satellite Data: Status indicator (Active - green)

**Account:**
- Profile Settings: Link with chevron-right arrow
- Sign Out: Red text with logout icon

**Purpose**: System configuration when clicking Settings menu

---

#### 7. Alerts Panel
**Position**: x: 120, y: 740
**Size**: 360 x 280px

**Contents:**
- **Header**: "Recent Alerts" with badge showing "3"
- **Alert Items**:
  - Alert 1: "AIS Signal Lost" - SEA PHOENIX (red icon, 5 minutes ago)
  - Alert 2: "Speed Anomaly" - OCEAN CARRIER (orange icon, 12 minutes ago)
  - Alert 3: "Geofence Entry" - MV PACIFIC EXPLORER (blue icon, 15 minutes ago)

**Purpose**: Quick access to recent system alerts

---

#### 8. Map Controls
**Position**: x: 120, y: 80
**Size**: 44 x 136px

**Contents:**
- Zoom In button (+)
- Zoom Out button (-)
- Layers button (layers icon)

**Purpose**: Map interaction controls

---

## Design System Features

### Visual Effects
- **Glassmorphism**: Semi-transparent panels with backdrop blur effect
- **Glow Effects**: Cyan glows around active elements and vessels
- **Gradients**: Subtle gradients for depth and visual interest
- **Shadows**: Layered shadows for elevation (40px blur, 8px offset)

### Interaction States
- **Active Navigation**: Cyan glow + background accent
- **Hover States**: Brightness increase on interactive elements
- **Toggle Switches**: Animated position changes with color transitions
- **Button States**: Primary (cyan), Secondary (gray), Danger (red)

### Responsive Elements
- **Flexbox Layouts**: `layout: "horizontal"` or `"vertical"` for responsive sizing
- **Fill Container**: Elements with `width: "fill_container"` for fluid layouts
- **Absolute Positioning**: Map overlay uses `layout: "none"` for precise vessel marker placement

### Maritime-Specific Elements
- **Coordinates**: Geographic coordinates displayed in decimal degrees (e.g., "15.2°N, 112.5°E")
- **MMSI/IMO Numbers**: Monospace font (Roboto Mono) for technical identification
- **Nautical Units**: Speed in knots (kn), courses in degrees (°)
- **Time Format**: UTC timezone with "Feb 25, 2026 • 14:30 UTC" format

## Technical Implementation Notes

### Layout System
- Root screen uses `layout: "horizontal"` with sidebar + main container
- Main map area uses `layout: "none"` to allow absolute positioning of overlay elements
- Panels use `layout: "vertical"` with consistent 16-24px gaps
- All floating panels positioned absolutely on the map overlay layer (node ID: WumUZ)

### Component Architecture
1. **Main Screen** (5fCAq)
   - **Sidebar** (ffFyj) - Navigation
   - **Main Container** (Wua39)
     - **Top Bar** (iJIbi)
     - **Map Area** (L0X2m)
       - **Map Overlay** (WumUZ) - Contains all floating panels
       - **Bottom Overlay** (KvOkF) - Timeline controls

### Icon Usage
All icons use the Lucide icon font family with consistent sizing:
- Navigation icons: 24px
- Panel icons: 20px
- Small indicators: 16-18px

### Performance Considerations
- Use of design variables for consistent theming
- Efficient layering with proper z-index management
- Optimized shadow and blur effects
- Reusable component patterns

## Use Cases

### Primary User Workflows

1. **Monitor Active Vessels**
   - View real-time vessel positions on map
   - Check vessel details by clicking markers
   - Filter vessels by type using search panel

2. **Respond to Alerts**
   - View alerts panel for critical notifications
   - Click alert items to navigate to affected vessels
   - Acknowledge or dismiss alerts

3. **Analyze Traffic Patterns**
   - Open Analytics Dashboard
   - Review traffic trends over time
   - Examine vessel type distribution

4. **Review Historical Data**
   - Open History Playback panel
   - Select date range and vessels
   - Play back historical tracks at various speeds

5. **Configure System**
   - Access Settings panel
   - Adjust display preferences
   - Manage notification settings
   - Configure data sources

## Design Principles

1. **Readability**: High contrast text on dark backgrounds for 24/7 monitoring
2. **Hierarchy**: Clear visual hierarchy with size, color, and spacing
3. **Consistency**: Consistent spacing (12px, 16px, 24px), colors, and component patterns
4. **Efficiency**: Quick access to critical information, minimal clicks
5. **Professional**: Clean, modern aesthetic suitable for maritime operations
6. **Accessibility**: Clear color coding for different alert severities

## File Information
- **Design File**: `vessel-monitoring.pen`
- **Design Tool**: Pencil (`.pen` format)
- **Last Updated**: February 2026
- **Version**: 1.0

---

## Next Steps for Implementation

1. **Export Assets**: Extract icons, images, and graphics
2. **Component Library**: Build reusable React/Vue components
3. **State Management**: Implement real-time data updates
4. **Map Integration**: Integrate with mapping library (Mapbox, Leaflet)
5. **API Integration**: Connect to AIS data sources
6. **Testing**: Cross-browser and device testing
7. **Performance**: Optimize for handling large vessel datasets

---

**Design Credits**: Created using Pencil design tool with custom maritime theme based on elegantluxury style guide.
