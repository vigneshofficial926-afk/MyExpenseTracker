# Vignesh Expense Tracker — Design System & Visual Guidelines (Impeccable)

Inspired by [pbakaus/impeccable](https://github.com/pbakaus/impeccable.git) — transforming generic AI-generated frontend design into bold, polished, and delightful user experiences.

---

## 1. Visual Identity & Brand Philosophy
- **Personality**: Modern, high-trust, luxury personal fintech companion tailored for daily Indian Rupee (₹) transactions.
- **Color Temperature**: Deep obsidian undertones with vibrant emerald accents (`#10B981` / `#059669`). Never dead black or sterile monochrome gray.
- **Micro-Delight**: Subtle specular reflections, glowing active halos, and tactile press micro-animations.

---

## 2. Design Tokens

### Color Palette
- **Primary / Brand Accent**: Emerald `#10B981` (Hover/Dark: `#059669`, Light: `#34D399`)
- **Primary Glow**: `rgba(16, 185, 129, 0.35)`
- **Income**: Emerald `#10B981` (`rgba(16, 185, 129, 0.14)` fill)
- **Expense**: Radiant Coral `#F43F5E` (`rgba(244, 63, 94, 0.14)` fill)
- **Warning**: Warm Amber `#F59E0B`
- **Transfer / Savings**: Royal Violet `#8B5CF6`

### Dark Theme Surfaces
- **App Canvas**: Deep Obsidian `#0A0E1A` with top radial illumination (`radial-gradient(circle at 50% 0%, #152038 0%, #030712 75%)`)
- **Card Surface**: Rich Slate `#121826`
- **Surface Variant**: Elevated Slate `#1A2234`
- **Interactive Elevated**: `#222D44`
- **Borders**: Sub-pixel micro-strokes (`rgba(255, 255, 255, 0.08)`) with subtle specular highlights

### Typography
- **Font Family**: Plus Jakarta Sans (`font-feature-settings: "cv02" 1, "cv03" 1, "cv04" 1;`)
- **Numerical Figures**: Tabular figures (`font-feature-settings: "tnum" 1;`) to guarantee optical number alignment and zero layout jitter.
- **Hierarchy**:
  - Hero Balance: `34px`, Weight `800`, Letter spacing `-1.2px`
  - Page Titles: `20px`, Weight `800`, Letter spacing `-0.5px`
  - Section Headers: `11px`, Weight `700`, Uppercase, Letter spacing `0.8px`
  - Card Values: `18px – 19px`, Weight `800`

---

## 3. Impeccable Anti-Patterns Eliminated
1. **No generic SaaS blue-to-purple gradients**: Replaced with luxury metallic emerald mesh gradient for the Hero Balance Card.
2. **No sterile gray-on-gray nesting**: Differentiated card layers with frosted glass effects and glowing accents.
3. **No flat uninspiring buttons**: Tactile 3D press response with ambient glow halos.
4. **No jarring page jumps**: Frosted glass navigation dock (`backdrop-filter: blur(20px)`) with active emerald pill indicator.
