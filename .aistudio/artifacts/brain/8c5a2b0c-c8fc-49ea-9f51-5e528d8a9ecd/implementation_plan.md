# Implementation Plan - Minimalist Clean Light Redesign

## Goal
Redesign the application with a minimalist clean light aesthetic featuring subtle soft gray cards (`#F1F5F9`), clean white background, refined typography, and neutral professional colors, replacing the dark card design.

## Proposed Changes

### 1. Theme & Color Palette (`Neumorphic.kt`)
- Update color constants:
  - `NeuBackground`: Clean pure white (`Color.White`) or soft light gray (`Color(0xFFF8FAFC)`).
  - `NeuSurface`: Subtle soft light gray (`Color(0xFFF1F5F9)`).
  - `NeuSurfaceRaised`: Elevated clean light surface (`Color(0xFFE2E8F0)`).
  - `NeuTextPrimary`: Dark slate text (`Color(0xFF0F172A)`).
  - `NeuTextSecondary`: Medium slate text (`Color(0xFF475569)`).
  - `NeuAccentBlue`: Professional clean blue (`Color(0xFF2563EB)`).

### 2. Receipt & Invoice View (`InteractiveReceiptView.kt`)
- Update receipt paper background to pure white / soft subtle paper (`Color(0xFFFFFFFF)`).
- Update cell backgrounds to clean light gray / white with subtle clean borders (`Color(0xFFE2E8F0)`).
- Ensure dark readable text (`Color(0xFF0F172A)`).

### 3. Screen Redesign across all screens
- Ensure `NewInvoiceScreen`, `InvoiceHistoryScreen`, `ProductsCatalogScreen`, `CustomersScreen`, `CustomerAccountStatementScreen`, `PrinterSetupScreen`, and `StoreSettingsScreen` all adopt the minimalist clean light style with subtle gray cards and crisp dark text.

## Verification Plan
- Compile applet with `compile_applet`.
- Verify successful build and clean aesthetic.
