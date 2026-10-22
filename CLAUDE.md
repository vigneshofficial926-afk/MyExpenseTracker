# CLAUDE.md - Vignesh Expense Tracker

This document provides guidance for Claude Code CLI and AI agents working in this repository.

## Project Overview
**Vignesh Expense Tracker** is a personal finance management application offering a dual architecture:
1. **Web Dashboard (`web/`)**: Offline-first, responsive single-page web app built with Vanilla HTML5, Vanilla CSS, and Vanilla JavaScript, persisting data via `localStorage`.
2. **Android Application (`app/`)**: Native Android app written in Kotlin using Jetpack Compose, Material 3, Room database, and MVVM architecture.

---

## Quick Reference Commands

### Web Dashboard
- **Run local server**:
  ```bash
  # Windows PowerShell / CMD
  py -m http.server 8085 --directory web
  # Or with Node.js
  npx serve web -p 8085
  ```
- **Access URL**: `http://localhost:8085/`

### Android Native App
- **Compile Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Run Unit Tests**:
  ```bash
  ./gradlew test
  ```
- **Check Lint**:
  ```bash
  ./gradlew lint
  ```

### Claude Code CLI
- **Launch Claude Code**:
  ```bash
  claude
  ```

---

## Repository Structure

```
MyExpenseTracker/
├── .github/
│   └── workflows/
│       ├── build-apk.yml      # Automated Android debug APK build & artifact upload
│       └── claude.yml         # Claude Code GitHub Action (@claude PR & Issue assistant)
├── app/                       # Android native project module
│   ├── src/main/java/com/expensetracker/app/
│   │   ├── data/              # Room DB entities, DAOs, repositories
│   │   ├── ui/                # Jetpack Compose screens, theme, components
│   │   └── MainActivity.kt    # Android entry activity
│   └── build.gradle.kts       # Android build configuration
├── web/                       # Web application dashboard
│   ├── css/
│   │   └── style.css          # Design system, Impeccable dark theme tokens
│   ├── js/
│   │   └── app.js             # State management, FinancialCalculator, views, Claude AI Assistant
│   ├── index.html             # Shell & single-page view structure
│   └── VigneshExpenseTracker.apk # Prebuilt Android APK for web download
├── CLAUDE.md                  # This file (guidelines for Claude Code)
├── DESIGN.md                  # Impeccable Design System specifications
└── README.md                  # Project documentation
```

---

## Design System & UX Principles

The UI strictly adheres to **[DESIGN.md](file:///C:/Users/VigneshThangamuthu/.gemini/antigravity-ide/scratch/MyExpenseTracker/DESIGN.md)** based on Paul Bakaus's **Impeccable** design philosophy:

- **Theme**: Luxury Neobank Dark Palette.
  - Background Canvas: `#0A0E1A` with subtle radial lighting.
  - Card Surfaces: Layered depth (`#121826`, `#1A2234`) with `rgba(255, 255, 255, 0.08)` borders.
  - Primary Accent: Radiant Emerald (`#10B981` / `#059669`).
  - Expense / Danger: Rose Coral (`#F43F5E` / `#E11D48`).
  - Claude AI Accent: Terracotta Warm Amber (`#D97706` / `#F59E0B`).
- **Typography**: `Plus Jakarta Sans` with `font-feature-settings: "tnum" 1` for tabular financial numerals.
- **Anti-Patterns to Avoid**:
  - No generic AI-slop purple-to-blue gradients.
  - No nested cards inside cards without visual elevation change.
  - No light gray text on light backgrounds or low-contrast text.
  - No unstyled browser inputs.

---

## Financial Logic & Rules

When modifying or adding financial tracking features:
1. **Net Balance Calculation**:
   $$\text{Total Balance} = \sum \text{Account Balances} + \sum \text{Income} - \sum \text{Expenses}$$
   *(Note: Credit card bill payments must be excluded from double-counting expenses).*
2. **Transaction Types**:
   - `EXPENSE`: Decreases balance, categorized by spending category.
   - `INCOME`: Increases balance.
   - `TRANSFER`: Moves funds between accounts without altering net worth.
3. **Budget Rules**:
   - Monthly budgets reset by month key (`MM-YYYY`).
   - Budget alerts trigger at 80% (Warning) and 100% (Critical).
4. **Offline First**:
   - Web application must function 100% offline via `localStorage`.
   - Claude AI Financial Assistant features must provide full local intelligence fallback without requiring external API keys.
