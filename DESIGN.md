---
version: alpha
name: Sheaf Ember
description: Sheaf Android app design system (Jetpack Compose, Material 3). Tokens mirror core/ui/.../theme/Theme.kt and Type.kt; the code wins if they ever differ.
colors:
  primary: "#E4713B"
  ember: "#E4713B"
  ember-deep: "#B5501F"
  on-ember-dark: "#3A1608"
  dark-background: "#15171C"
  dark-on-background: "#ECEEF2"
  dark-surface-variant: "#262933"
  dark-on-surface-variant: "#C3C6CC"
  dark-primary-container: "#5A2E17"
  dark-on-primary-container: "#FFDBCB"
  dark-secondary: "#C9C3B9"
  dark-outline: "#3A3E46"
  light-background: "#F5F6F8"
  light-surface: "#FFFFFF"
  light-on-surface: "#15171C"
  light-surface-variant: "#E7E8EC"
  light-on-surface-variant: "#44474E"
  light-primary-container: "#FFDBCB"
  light-secondary: "#6C6459"
  light-outline: "#C3C6CC"
typography:
  display-large:
    fontFamily: Hanken Grotesk
    fontWeight: 400
  headline-medium:
    fontFamily: Hanken Grotesk
    fontWeight: 400
  title-large:
    fontFamily: Hanken Grotesk
    fontWeight: 600
  title-medium:
    fontFamily: Hanken Grotesk
    fontWeight: 500
  body-large:
    fontFamily: Hanken Grotesk
    fontWeight: 400
  label-large:
    fontFamily: Hanken Grotesk
    fontWeight: 500
components:
  button-primary-dark:
    backgroundColor: "{colors.ember}"
    textColor: "{colors.on-ember-dark}"
  button-primary-light:
    backgroundColor: "{colors.ember-deep}"
    textColor: "{colors.light-surface}"
---

## Overview

Neutral slate surfaces and one warm ember accent. **The document is the hero**: chrome stays quiet so the
PDF or book page carries the screen. Chosen at milestone M2.

## Colors

- **Ember (#E4713B, dark) / Ember Deep (#B5501F, light):** the only accent. Primary actions, selection, progress, the active tab. Never large fills behind reading content.
- **Slate surfaces:** #15171C dark background, #F5F6F8 light background. Surface variants for sheets and toolbars.
- **Contrast (fixed 2026-11-19):** Ember Deep darkened from #C85A28 to #B5501F. White on it is 5.08:1 and it reads 4.70:1 as text on the light background, both WCAG AA.
- **Material You** is opt-in (`dynamicColor = false` by default). Ember is the default identity.

## Typography

Hanken Grotesk (bundled) across the whole Material 3 scale, keeping M3 sizes and metrics. Titles semibold or
medium, labels medium, body regular. Reading content uses the document's own fonts, not the app font.

## Rules for agents

- Use `MaterialTheme.colorScheme` and `MaterialTheme.typography` roles. Never hard-code hex values in composables.
- Use Material Symbols for icons.
- Every screen must work in light and dark, and at 200% font scale.
- New colour roles go into `Theme.kt` first, then this file.
