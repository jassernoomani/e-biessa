---
version: alpha
name: E-biessa
description: An automotive parts counter with readable catalogue and workspace surfaces.
colors:
  primary: '#b83d1b'
  ink: '#202529'
  muted: '#59646c'
  background: '#f4f6f7'
  surface: '#ffffff'
  border: '#d5dce0'
typography:
  body:
    fontFamily: "'Segoe UI', Arial, sans-serif"
    fontSize: '1rem'
    lineHeight: '1.5'
  display:
    fontFamily: "'Barlow Condensed', 'Arial Narrow', sans-serif"
rounded:
  DEFAULT: '12px'
  control: '8px'
spacing:
  page-inset: '5vw'
  panel: '20px'
components:
  button:
    backgroundColor: '#b83d1b'
    textColor: '#ffffff'
  card:
    backgroundColor: '#ffffff'
    textColor: '{colors.ink}'
    rounded: '{rounded.DEFAULT}'
---

# E-biessa design system

## Overview

An established Tunisian car-parts marketplace, refined rather than rebranded.
The signature remains the brake-assembly illustration and orange brand; product
surfaces prioritize parts, prices, vehicle fitment and direct seller contact.
Public browsing mixes brand expression with catalogue utility. Inventory,
messaging, profile and moderation screens are task-oriented. English is the
existing interface language; search also accepts French and Tunisian input.
Evidence: repository README and documentation/ARCHITECTURE.md.

## Colors

Runtime is canonical (ownership model B): `src/workshop.css` owns accepted tokens.
This file mirrors them, not a second theme generator. Light mode uses white,
cool-grey and burnt orange; dark mode uses graphite, off-white and pale orange.
No green, decorative gradients, neon or assistant-style glowing callouts.
Dark values: accent #ff916b, ink #f0f2f3, muted #b1bbc2, surface #222a31,
canvas #171d23, field #1c232a and border #39434c. Primary action background
remains #b83d1b in both themes to preserve white-label contrast.

## Typography

Self-hosted Barlow Condensed SemiBold carries the hero and primary page headings.
Segoe UI/Arial carries controls, prose, listing names and data; no display face
on form labels. Technical metadata uses readable sans rather than decorative
monospace. Body copy 15–16px; metadata 12–13px; listing titles 16px.
Tracking stops at -0.025em. Numerals in prices and statistics are tabular.
Font preload and optional display avoid a late font swap. The bundled font
license is `public/fonts/OFL.txt`.

## Layout

Full-bleed section backgrounds with one shared responsive content gutter
(`--page-gutter` in workshop.css), 20px card interior and 32px catalogue separation.
Header, hero, category strip, catalogue and footer align to the same content grid.
Only content is capped at approximately 1560px; background bands never stop short
of the viewport. Filters scroll with the document rather than inside a clipped
desktop sidebar. Metadata is restrained; controls and field labels use 600 weight.
Hero splits copy and the existing illustration; at 760px it becomes one column.
Catalogue transitions to two columns and then one at 440px. Mobile filters are
an existing inline disclosure, not a new modal. Tables retain horizontal scroll.
Themes change tokens, not component dimensions. Images reserve their geometry.
Responsive layout uses content-driven transitions at 1240px (listing contact
below gallery/details), 960px (tablet hero), 760px (stacked workspaces), and
480px (compact navigation/forms). Page content is capped at 1560px for wide
monitors. Tables scroll inside their own wrapper; they never force page overflow.
On phones messaging uses a bounded conversation list and thread message scroller,
with the composer in natural document flow, not a fixed 720px shell. Touch controls
have a 44px minimum target; editable mobile form text stays at 16px. Safe-area
insets protect overlay feedback and modal edges. No core action is removed.
The brake-assembly panel uses the active surface, ink and muted tokens. Its
single SVG source is inlined by BrakeAssemblyComponent, allowing annotation,
grid and leader-line colours to inherit the theme without duplicating artwork.

## Elevation & Depth

Borders separate working surfaces. Static listing cards have no shadow; hover
changes the border. Solid surfaces replace decorative blur and gradients.
Modal backdrops remain for protected focus; no new overlay behavior is added.

## Shapes

12px panel radius, 8px controls, restrained 4px metadata/status labels.
Active search modes use an underline; no nested floating panels.

## Components

| Documented role                             | Runtime owner                     | Consumers                                  |
| ------------------------------------------- | --------------------------------- | ------------------------------------------ |
| primary/ink/muted/background/surface/border | workshop.css semantic variables   | all application surfaces                   |
| body/display                                | --font-body / --font-display      | controls, catalogue, page headings         |
| panel/control radius                        | --radius-panel / --radius-control | cards, inputs, buttons                     |
| light/dark adapter                          | .dark-theme variables             | ThemeService, shared brand logo            |
| shared stroke icons                         | UiIconComponent                   | search, voice, theme, photo identification |
| scrollbars and focus                        | workshop.css global baseline      | every owned overflow region/control        |

Buttons keep visible hover, focus, selected, disabled and busy treatments.
Native select popups remain platform-owned intentionally. Product textareas have
adequate height and no resize handle. Selection, caret and scrollbar colours use
the same semantic palette; forced-colours delegates to system colours.
Existing 150–250ms feedback and reduced-motion support remain; no additional
animation engine or WebGL dependency is needed for this refinement.

## Do's and Don'ts

- Do keep actual listing data and compatibility cautions intact.
- Do reuse the global theme adapter rather than adding another override file.
- Don't invent inventory, seller verification or model-confidence claims.
- Don't add green, gradient cards, ornamental eyebrow labels or tiny data text.

### Migration scope

Catalogue layout is a deliberate exception to the centred reading gutter:
the desktop rail starts 24px from the window (16px on tablets), stays sticky,
and owns scrolling when its fields exceed the viewport. Results use an
auto-fill grid with a 250px preferred minimum (240px on tablets), so browser
zoom changes columns naturally without changing server pagination. Below
761px, filters become an inline two-column disclosure. Phone navigation uses
two rows and a compact labelled Join action; inventory rows become labelled
cards. Phone messages use a conversation-list/thread split with a visible
Conversations return action. Native controls and all role permissions remain.

Shared catalogue/workspace tokens and typography are refreshed together.
Older component CSS remains for geometry until a separate component refactor;
workshop.css is the sole accepted visual adapter. Backend roles, mutations,
navigation and model contracts are outside this visual refresh. Any existing
behavior gaps found by static tools are recorded, not disguised as compliance.
