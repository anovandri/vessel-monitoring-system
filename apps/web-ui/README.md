# Web UI - Vessel Monitoring System

Modern Next.js 14 frontend application for real-time vessel monitoring and maritime intelligence.

## 🎨 Design System

Based on the Pencil design file (`design/vessel-monitoring.pen`):

- **Theme**: Maritime Dark Theme
- **Primary Colors**: 
  - Background: `#0A0E1A`
  - Sidebar: `#060912`
  - Accent: `#00E5FF` (Cyan)
  - Text: `#E8F0F8`
- **Fonts**: 
  - Primary: Inter
  - Monospace: Roboto Mono
- **Layout**: Fixed 80px sidebar, responsive main content area

## 🚀 Tech Stack

- **Framework**: Next.js 14 with App Router
- **Language**: TypeScript
- **Styling**: Tailwind CSS v4 (PostCSS-based)
- **Icons**: Lucide React
- **Real-time**: STOMP.js (WebSocket client - planned)
- **Maps**: Leaflet.js (planned)

## 📦 Running with Nx

This project is integrated into the Nx monorepo. Use these commands from the **root directory**:

### Development Server
```bash
./nx serve web-ui
```
Starts the Next.js dev server at http://localhost:3000

### Build for Production
```bash
./nx build web-ui
```
Creates an optimized production build in `.next` folder

### Start Production Server
```bash
./nx start web-ui
```
Runs the production build (requires build first)

### Linting
```bash
./nx lint web-ui
```
Runs ESLint checks

### Type Checking
```bash
./nx type-check web-ui
```
Runs TypeScript compiler in check mode

### Clean Build Artifacts
```bash
./nx clean web-ui
```
Removes `.next` and cache directories

### Install Dependencies
```bash
./nx install web-ui
```
Installs npm packages

## 📁 Project Structure

```
apps/web-ui/
├── app/                    # Next.js App Router
│   ├── page.tsx           # Dashboard (homepage)
│   ├── layout.tsx         # Root layout
│   └── globals.css        # Global styles & design tokens
├── components/            # React components
│   └── Sidebar.tsx        # Navigation sidebar
├── public/                # Static assets
├── project.json          # Nx project configuration
├── package.json          # Dependencies
└── tsconfig.json         # TypeScript config
```

## 🎯 Features

### ✅ Completed
- [x] Maritime dark theme design system
- [x] Responsive sidebar navigation with 7 main routes
- [x] Dashboard with real-time stats cards
- [x] Recent vessels list with status indicators
- [x] Alert notifications panel with severity levels
- [x] Nx monorepo integration

### 🚧 Planned
- [ ] Interactive map view with Leaflet.js
- [ ] WebSocket integration for real-time vessel positions
- [ ] REST API client for weather/port data
- [ ] Vessel details modal
- [ ] Additional pages (Map, Vessels, Alerts, Weather, Ports, Analytics)

## 🔌 Backend Integration

### WebSocket (Real-time)
- **Endpoint**: `ws://localhost:8082/ws`
- **Topics**:
  - `/topic/vessel-positions` - Real-time vessel location updates
  - `/topic/vessel-alerts` - Critical alerts and notifications

### REST API (Polling)
- **Weather**: `GET /api/weather/grid/:gridId`
- **Ports**: `GET /api/ports/:portId`

## 🛠️ Development

### Local Development (from web-ui directory)
```bash
cd apps/web-ui
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

### Available Scripts (in web-ui directory)
- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run start` - Start production server
- `npm run lint` - Run ESLint

## 📝 Notes

- The design follows the specifications from `design/vessel-monitoring.pen`
- All color values and spacing are defined in `app/globals.css` as CSS custom properties
- The application uses Next.js 14 App Router (not Pages Router)
- Tailwind CSS v4 uses `@theme inline` directive for custom theme configuration

## 🔗 Related Services

- **data-collector-service**: Collects vessel AIS data
- **data-persistence-service**: WebSocket bridge and data storage
- **stream-processor**: Real-time data processing with Flink

## 📚 Learn More

- [Next.js Documentation](https://nextjs.org/docs)
- [Nx Documentation](https://nx.dev)
- [Tailwind CSS v4](https://tailwindcss.com/docs)

