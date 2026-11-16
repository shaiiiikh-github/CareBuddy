# CareBuddy iOS 26 "Liquid Glass" Design System

## 1. Design Philosophy
Minimal, calm, high-translucency iOS 26 aesthetic designed for elderly companion reliability. Replaces generic Android Material 3 palettes, harsh elevation shadows, and celebratory emojis with tactile glass surfaces, human-centered copywriting, and fluid spring physics.

## 2. Tokens & Specifications
* **Palette**: Fixed (Dynamic Color disabled). Light: Background `#F2F2F7`, Surface Glass `rgba(255,255,255,0.68)`, Primary Label `#1C1C1E`, Secondary Label `#3C3C43` (60%). Dark: Background `#0B0B10`, Surface Glass `rgba(255,255,255,0.10)`, Primary Label `#FFFFFF`, Secondary Label `rgba(255,255,255,0.60)`.
* **Semantic Tints**: Accent (Blue `#007AFF` / `#0A84FF`), Success (Green `#34C759` / `#30D158`), Warning (Orange `#FF9500` / `#FF9F0A`), Danger (Red `#FF3B30` / `#FF453A`), Neutral Gray (`#8E8E93`).
* **Corner Radii**: Cards `28dp`, Rows/Inputs `22dp`, Buttons/Chips/Tab Bar full capsule (`999dp`).
* **Typography**: iOS hierarchy (Large Title `34sp/Bold`, Title `22sp/SemiBold`, Headline `17sp/SemiBold`, Body `17sp/Regular`, Footnote `13sp`, Caption `12sp`). Minimum body size `16sp`.
* **Motion & Springs**: Snappy spring (`dampingRatio = 0.8f, stiffness = 400f`), tactile press-scale (`0.97f`), haptic feedback on toggles and primary actions.

## 3. Glass Rules
1. **Translucent fill + Hairline gradient border**: 1dp vertical gradient (white 55% -> 8% in light, subtle highlight in dark) + ambient elevation (`4dp` light, `0dp` dark).
2. **Performance Budget**: Max 2–3 glass surfaces per screen. Never nest glass inside glass.
3. **Legibility Contract**: Text on glass strictly maintains >= 4.5:1 contrast against ambient background blobs.
4. **SOS Alert Exception**: Emergency SOS modal prioritizes instantaneous legibility with solid high-contrast crimson backgrounds (`#140202` / `#240A0A`), avoiding translucent glass.

## 4. Components & Usage Snippets

```kotlin
// Glass Card Container
GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { /* navigate */ }) {
    Text(text = "Next reminder", style = MaterialTheme.typography.titleLarge)
}

// Inset Grouped Table & ListRow
SectionHeader(text = "Companion Status")
InsetGroupedList {
    ListRow(
        title = "CareBuddy Device",
        subtitle = "Connected",
        icon = Icons.Outlined.Bluetooth,
        trailing = { StatusPill(label = "Ready", dotColor = CareBuddyTheme.colors.success) }
    )
}

// Interactive Capsule Button & iOS Switch
CapsuleButton(text = "Add Reminder", onClick = {}, style = ButtonStyle.Filled)
IosSwitch(checked = reminder.enabled, onCheckedChange = { viewModel.toggle(it) })
```

## 5. Do's and Don'ts
* **DO**: Use sentence case everywhere ("Next reminder", "Schedule time").
* **DO**: Provide clear, single-action focus per card with whitespace over nested borders.
* **DON'T**: Use emojis as icons (`🔔`, `🎉`, `🚨`). Use consistent `Icons.Outlined` vector symbols.
* **DON'T**: Use generic purple/blue "AI" gradients or harsh Material 3 elevation shadows.
