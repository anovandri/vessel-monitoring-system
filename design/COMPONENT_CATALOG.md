# VMS Component Catalog

## Reusable Component Library

This document catalogs all reusable UI components in the VMS design for easy reference and implementation.

---

## Buttons

### Primary Button (Cyan)
**Example**: Play button in History Playback
- **Fill**: `$accent-cyan` (#00E5FF)
- **Text Color**: `#0A0E1A` (dark, for contrast)
- **Corner Radius**: 8px (standard) or 28px (circular)
- **Padding**: 12-16px horizontal, 8-12px vertical
- **Font**: Inter, 13px, weight 600
- **States**:
  - Hover: Brightness 110%
  - Active: Brightness 90%
  - Disabled: Opacity 50%

### Secondary Button (Gray)
**Example**: Skip buttons, speed controls
- **Fill**: `$bg-page` (#0A0E1A)
- **Text Color**: `$text-secondary` (#8B9DAF)
- **Border**: 1px solid `$border` (#1A2332)
- **Corner Radius**: 8px
- **Padding**: 12-16px horizontal, 8-12px vertical
- **Font**: Inter, 13px, weight 500

### Danger Button (Red)
**Example**: Sign Out button
- **Fill**: Transparent or `$danger` background
- **Text Color**: `$danger` (#FF3D71)
- **Border**: Optional 1px solid `$danger`
- **Corner Radius**: 8px
- **Font**: Inter, 13px, weight 500

### Icon Button
**Example**: Close buttons, map controls
- **Size**: 32x32px (standard), 48x48px (large)
- **Fill**: `$bg-page` or transparent
- **Icon Size**: 18-20px
- **Corner Radius**: 8px
- **Hover**: Background opacity 80%

---

## Inputs

### Search Input
**Example**: Top bar search, vessel search panel
- **Height**: 44-48px
- **Fill**: `$bg-page` (#0A0E1A)
- **Border**: 1px solid `$border` (#1A2332)
- **Corner Radius**: 8px
- **Padding**: 12-16px
- **Placeholder**: `$text-tertiary` (#556677)
- **Text**: `$text-primary` (#E8F0F8)
- **Font**: Inter, 13px
- **Icon**: 20px, `$text-tertiary`, positioned left or right

**Focus State**:
- Border: `$accent-cyan`
- Box shadow: 0 0 0 3px rgba(0, 229, 255, 0.1)

### Date Picker Input
**Example**: History playback date range
- **Height**: 44px
- **Fill**: `$bg-page`
- **Border**: 1px solid `$border`
- **Corner Radius**: 8px
- **Layout**: Horizontal with calendar icon (18px) + date text
- **Font**: Inter, 13px
- **Icon Color**: `$text-tertiary`

### Dropdown Select
**Example**: Speed units, vessel selection
- **Height**: 36-44px
- **Fill**: `$bg-page`
- **Border**: 1px solid `$border`
- **Corner Radius**: 8px
- **Padding**: 12px horizontal
- **Font**: Inter, 13px
- **Icon**: Chevron-down, 16px, `$text-tertiary`
- **Layout**: Text on left, icon on right (space-between)

---

## Toggles & Switches

### Toggle Switch
**Example**: Settings panel toggles
- **Width**: 44px
- **Height**: 24px
- **Corner Radius**: 12px (pill shape)
- **Padding**: 2px

**ON State**:
- **Track Fill**: `$accent-cyan`
- **Thumb**: 20x20px white circle at x:18 (right)

**OFF State**:
- **Track Fill**: `$bg-page`
- **Track Border**: 1px solid `$border`
- **Thumb**: 20x20px `$text-tertiary` circle at x:2 (left)

**Animation**: Smooth 0.2s ease transition

### Button Toggle Group
**Example**: Theme toggle (Dark/Light), Speed controls
- **Container**:
  - Height: 36-40px
  - Fill: `$bg-page`
  - Corner Radius: 8px
  - Padding: 2px
  - Layout: Horizontal, no gap

- **Toggle Button**:
  - Width: Equal division (fill_container)
  - Height: Fill container
  - Corner Radius: 6px

- **Active State**:
  - Fill: `$accent-cyan`
  - Text: `$text-primary`, weight 600

- **Inactive State**:
  - Fill: Transparent
  - Text: `$text-tertiary`, weight 500

---

## Cards

### Metric Card
**Example**: Dashboard metrics, analytics cards
- **Size**: Variable width x 120px height
- **Fill**: `$bg-page`
- **Border**: 1px solid `$border`
- **Corner Radius**: 12px
- **Padding**: 20px
- **Layout**: Vertical, gap 12px

**Structure**:
1. Icon (24x24px, colored)
2. Value (32px, weight 700, `$text-primary`)
3. Label (13px, `$text-tertiary`)
4. Optional trend indicator (+12%, -6%)

**Icon Colors**:
- Primary: `$accent-cyan`
- Success: `$success`
- Warning: `$warning`
- Danger: `$danger`
- Info: `$info`

### Activity Card
**Example**: Recent activity items in dashboard
- **Height**: 68px
- **Fill**: `$bg-page`
- **Border**: 1px solid `$border`
- **Corner Radius**: 12px
- **Padding**: 16px
- **Layout**: Horizontal, gap 16px, center-aligned

**Structure**:
1. Icon container (40x40px, colored background with 15% opacity, 8px radius)
   - Icon (20x20px, solid color)
2. Content container (vertical, gap 4px)
   - Title (13px, weight 500, `$text-primary`)
   - Timestamp (12px, `$text-tertiary`)

### Vessel Result Card
**Example**: Vessel search results
- **Height**: 68-72px
- **Fill**: `$bg-page` or transparent
- **Border**: 1px solid `$accent-cyan` (selected) or `$border`
- **Corner Radius**: 12px
- **Padding**: 16px
- **Layout**: Horizontal, gap 12px

**Structure**:
1. Icon indicator (colored)
2. Content (vertical layout)
   - Vessel name (13px, weight 500)
   - MMSI + type + speed (12px, `$text-tertiary`)

---

## Panels & Containers

### Main Panel
**Example**: All popup panels
- **Fill**: `$bg-panel` (#0F1420)
- **Border**: 1px solid `$border` (#1A2332)
- **Corner Radius**: 16px
- **Shadow**: 0 8px 40px rgba(0,0,0,0.2)
- **Backdrop Filter**: blur(20px) for glassmorphism
- **Padding**: 24px
- **Layout**: Vertical, gap 24px

**Standard Header**:
- Height: 32px
- Layout: Horizontal, space-between
- Title: 18px, weight 600, `$text-primary`
- Close button: 32x32px icon button

### Panel Section
**Example**: Settings sections, activity sections
- **Layout**: Vertical, gap 16px
- **Padding**: 20px vertical, 0 horizontal
- **Border Bottom**: 1px solid `$border` (optional separator)

**Section Header**:
- Font: Inter, 15px, weight 600
- Color: `$text-primary`

### Sub-panel / Card Container
**Example**: Metrics row, activity list
- **Layout**: Horizontal or vertical
- **Gap**: 12-16px
- **Width**: Fill container

---

## Lists

### Vertical List
**Example**: Activity feed, vessel search results
- **Layout**: Vertical
- **Gap**: 12px
- **Width**: Fill container

**List Item**:
- See Activity Card or Vessel Result Card specs

### Tab List
**Example**: Filter tabs in vessel search
- **Layout**: Horizontal
- **Gap**: 12px
- **Height**: 36-40px

**Tab Button**:
- **Inactive**:
  - Fill: Transparent
  - Text: `$text-secondary`, 13px
  - Border: None
- **Active**:
  - Fill: `$accent-cyan` or transparent
  - Border: 2px solid `$accent-cyan`
  - Text: `$text-primary`, weight 600
- **Corner Radius**: 8px
- **Padding**: 12-16px horizontal

---

## Status Indicators

### Connection Status
**Example**: AIS Stream, Satellite Data
- **Layout**: Horizontal, gap 8px, center-aligned

**Structure**:
1. Indicator dot (8x8px, corner radius 4px)
2. Status text (12px, weight 500)

**Colors**:
- Connected/Active: `$success` (#00FF88)
- Disconnected/Error: `$danger` (#FF3D71)
- Warning: `$warning` (#FFB800)

### Live Indicator
**Example**: Top bar live data indicator
- **Pulse Animation**: 0-100% opacity, 2s infinite
- **Dot**: 8x8px, `$success`
- **Text**: "Live Data", 13px, weight 500, `$success`
- **Layout**: Horizontal, gap 8px

### Badge Counter
**Example**: Alert count, notification count
- **Size**: Auto width x 20px height (minimum)
- **Fill**: `$danger` or `$accent-cyan`
- **Text**: White (#FFFFFF), 11px, weight 600
- **Corner Radius**: 10px (pill)
- **Padding**: 4-6px horizontal
- **Position**: Top-right corner (absolute or relative)

---

## Charts & Visualizations

### Bar Chart
**Example**: Traffic over time in analytics
- **Bar Width**: Variable (based on container)
- **Bar Gap**: 12-16px
- **Bar Fill**: `$accent-cyan-dim` (inactive), `$accent-cyan` (active/highlighted)
- **Bar Corner Radius**: 4px (top)
- **Height**: Variable (60-180px)
- **Layout**: Horizontal container with vertical bars

### Timeline Slider
**Example**: Bottom timeline, history playback timeline
- **Height**: 60-80px
- **Fill**: Gradient from `$accent-cyan` (5% opacity) to (15% opacity)
- **Corner Radius**: 8px
- **Layout**: Absolute positioning

**Progress Fill**:
- Width: Variable (based on current position)
- Fill: Gradient `$accent-cyan` (30% to 60% opacity)

**Marker**:
- Width: 4px
- Height: 36px
- Fill: `$accent-cyan` (solid)
- Corner Radius: 2px
- Position: Vertical center

**Labels**:
- Start/End labels: 11px, weight 500, `$text-tertiary`
- Positioned at timeline edges

---

## Icons & Graphics

### Navigation Icons
- **Size**: 24x24px
- **Color**: `$text-tertiary` (inactive), `$accent-cyan` (active)
- **Library**: Lucide

**Icon Names**:
- Dashboard: `layout-dashboard`
- Live Map: `map`
- Search: `search`
- Alerts: `bell`
- Analytics: `bar-chart`
- History: `clock`
- Settings: `settings`

### Feature Icons
- **Size**: 20px (standard), 24px (large)
- **Library**: Lucide

**Common Icons**:
- Ship: `ship`
- Anchor: `anchor`
- Waves: `waves`
- Alert: `triangle-alert`
- Trending Up: `trending-up`
- Calendar: `calendar`
- Play: `play`
- Pause: `pause`
- Skip Back: `skip-back`
- Skip Forward: `skip-forward`
- Chevron Down: `chevron-down`
- Chevron Right: `chevron-right`
- Close: `x`
- Plus: `plus`
- Minus: `minus`
- Layers: `layers`
- User: `user`
- Bell: `bell`
- Search: `search`
- Log Out: `log-out`

### Vessel Markers
**Example**: Map vessel icons
- **Size**: 32x32px
- **Icon**: `navigation` (Lucide)
- **Rotation**: Based on vessel heading
- **Color**: `$accent-cyan` or vessel-type specific
- **Glow**: 0 0 20px `$accent-cyan` with 40% opacity

---

## Typography Styles

### Heading 1 (Panel Titles)
- **Font**: Inter
- **Size**: 18px
- **Weight**: 600
- **Color**: `$text-primary`
- **Line Height**: 1.4

### Heading 2 (Section Headers)
- **Font**: Inter
- **Size**: 15px
- **Weight**: 600
- **Color**: `$text-primary`
- **Line Height**: 1.4

### Body Text
- **Font**: Inter
- **Size**: 13px
- **Weight**: 400-500
- **Color**: `$text-primary` (primary), `$text-secondary` (secondary)
- **Line Height**: 1.5

### Caption / Small Text
- **Font**: Inter
- **Size**: 12px
- **Weight**: 400-500
- **Color**: `$text-tertiary`
- **Line Height**: 1.4

### Technical Data
- **Font**: Roboto Mono
- **Size**: 12-13px
- **Weight**: 400-500
- **Color**: `$text-secondary` or `$text-tertiary`
- **Use Cases**: MMSI, IMO, coordinates, timestamps

### Metric Values
- **Font**: Inter
- **Size**: 32px
- **Weight**: 700
- **Color**: `$text-primary`
- **Line Height**: 1.2

---

## Spacing System

### Layout Gaps
- **4px**: Tight (inline elements, badges)
- **8px**: Small (icon + text, close elements)
- **12px**: Medium (list items, cards)
- **16px**: Standard (sections, form elements)
- **24px**: Large (panel sections, major divisions)

### Padding Scale
- **8px**: Compact (badges, small buttons)
- **12px**: Standard (buttons, inputs)
- **16px**: Comfortable (cards, list items)
- **20px**: Spacious (panel sections)
- **24px**: Extra spacious (main panels)

---

## Shadow System

### Elevation 1 (Cards)
```css
box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
```

### Elevation 2 (Panels)
```css
box-shadow: 0 8px 40px rgba(0, 0, 0, 0.2);
```

### Elevation 3 (Modals)
```css
box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
```

### Glow Effect (Active Elements)
```css
box-shadow: 0 0 20px rgba(0, 229, 255, 0.4);
```

---

## Animation Timings

- **Fast**: 0.15s (hover states, ripples)
- **Normal**: 0.3s (transitions, fades)
- **Slow**: 0.5s (page transitions, modals)

**Easing Functions**:
- **ease-in-out**: Default for most transitions
- **ease-out**: For enter animations
- **ease-in**: For exit animations

---

## Implementation Code Examples

### React Component Example (Metric Card)
```jsx
function MetricCard({ icon, value, label, color = "$accent-cyan" }) {
  return (
    <div className="metric-card">
      <Icon name={icon} size={24} color={color} />
      <div className="metric-value">{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  );
}
```

### CSS Variables
```css
:root {
  --bg-page: #0A0E1A;
  --bg-panel: #0F1420;
  --bg-sidebar: #060912;
  --accent-cyan: #00E5FF;
  --accent-cyan-dim: rgba(0, 229, 255, 0.25);
  --text-primary: #E8F0F8;
  --text-secondary: #8B9DAF;
  --text-tertiary: #556677;
  --success: #00FF88;
  --danger: #FF3D71;
  --warning: #FFB800;
  --info: #4A90E2;
  --border: #1A2332;
  
  --font-primary: 'Inter', sans-serif;
  --font-mono: 'Roboto Mono', monospace;
  
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  
  --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.1);
  --shadow-md: 0 8px 40px rgba(0, 0, 0, 0.2);
  --shadow-lg: 0 20px 60px rgba(0, 0, 0, 0.3);
}
```

---

**Note**: All measurements are in pixels unless otherwise specified. For responsive implementations, consider using rem units with a 16px base.
