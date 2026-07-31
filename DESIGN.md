# BrautCloud Design System

BrautCloud is a private wedding-gallery product. Its design should feel as considered as a wedding album without making routine work feel ceremonial or slow. This document is the durable source of truth for every user-facing frontend decision.

## Design inputs and provenance

The system reconciles two inputs rather than treating either one as a page to copy:

1. **The merged luxury landing page** — merge commit `94bfe20` / PR #9, implemented in `brautcloud-frontend/src/app/pages/landing/` and `brautcloud-frontend/src/styles.css`. This is the authoritative rendered reference. It established the editorial serif/sans contrast, charcoal–ivory–gold palette, restrained ornament, framed wedding imagery, fine borders, generous marketing rhythm, clear focus states, responsive reflow, and reduced-motion treatment.
2. **`brautcloud-frontend/BrautCloud Landing Page` reference source** — the launch brief names this generated reference folder. It is not versioned in this isolated checkout, any Git history, or any remote branch. PR #9 records it as the captain's read-only external reference folder. Its retained application-side translation was inspected at `daecd3b^`: the pre-redesign landing and global `@theme` used Playfair Display/Inter, `mainrose` gold, silk, black, a full-bleed wedding image, centered serif headings, gold-edged calls to action, and a fixed mobile conversion action. The original export must remain a reference, not become a runtime dependency or a source of copied generated artifacts.

The merged page wins where the sources differ because it is the reviewed, accessible product implementation. The old translation contributes the simple gold/silk/black roles and direct interaction model. Cormorant Garamond and Jost replace the older Playfair/Inter pairing so the app and marketing surfaces use one type system.

## Product and aesthetic principles

1. **The photographs are precious; the interface is quiet.** UI frames memories instead of competing with them.
2. **Luxury comes from proportion, not decoration.** Use typography, whitespace, alignment, and fine rules before adding ornaments or shadows.
3. **Editorial for invitation, practical for action.** Marketing may use asymmetry, oversized type, monograms, and layered imagery. Application pages use a disciplined grid, compact hierarchy, and obvious state feedback.
4. **Gold is punctuation.** Reserve metallic gold for primary actions, focus, small accents, and decorative rules. Never create an all-gold interface.
5. **Warm, never sterile.** Ivory replaces pure white; noir replaces pure black; imagery is gently desaturated rather than heavily filtered.
6. **Private should feel trustworthy.** Forms, loading, empty, error, and disabled states must be explicit and calm.

### Intentional risks

- **A high-contrast editorial serif in a software workspace** gives BrautCloud a memorable wedding identity. The cost is lower density, so serif is limited to headings, names, and expressive numbers.
- **A restrained metallic accent instead of a conventional app blue** reinforces the premium positioning. Accessible dark-gold is required for text on light surfaces; bright gold is decorative or used behind dark text.

## Foundations

The implementation source is `brautcloud-frontend/src/styles/`:

- `tokens.css` — raw palette, semantic variables, type, spacing, radius, shadows, motion, and Tailwind compatibility aliases.
- `foundations.css` — font loading, reset, focus, selection, typography helpers, skip links, and reduced motion.
- `primitives.css` — page, surface, button, form, status, loading, empty-state, and brand patterns.
- `application.css` — shared dashboard, gallery, upload, and onboarding compositions.
- `src/styles.css` — import-only entry point. Do not put page rules here.

Page-level CSS is acceptable for unique marketing composition. It must consume semantic tokens and shared controls rather than redefine colors, buttons, forms, or state patterns.

## Typography

| Role       | Family                             | Usage                                                                  |
| ---------- | ---------------------------------- | ---------------------------------------------------------------------- |
| Display    | Cormorant Garamond, Georgia, serif | H1–H3, couple names, expressive statistics, short editorial statements |
| Body/UI    | Jost, Trebuchet MS, sans-serif     | Body copy, controls, navigation, forms, status, and data               |
| Small caps | Cormorant SC, Georgia, serif       | Rare ceremonial labels only; never body copy                           |
| Icons      | Material Symbols Outlined          | Familiar actions with text or accessible names                         |

Type scale tokens range from `--bc-font-size-xs` (12px) to fluid `--bc-font-size-3xl` (40–76px). Application body copy remains 14–18px. Marketing may use larger component-local fluid display sizes. Headings use tight line height and slight negative tracking; body copy uses `1.65` line height. Tabular statistics use `font-variant-numeric: tabular-nums`.

**Rules**

- Use sentence case for headings and buttons. Uppercase plus tracking is reserved for short eyebrows, navigation, and status labels.
- Keep prose line length near 42–70 characters.
- Do not use display type for form values, long instructions, or dense data.
- Fonts load with `font-display: swap`; Georgia/Trebuchet fallbacks preserve the intended contrast.

## Color

### Semantic roles

| Token                           | Value     | Use                                           |
| ------------------------------- | --------- | --------------------------------------------- |
| `--bc-color-canvas`             | `#f8f5ef` | Default page background                       |
| `--bc-color-surface`            | `#fffdfa` | Cards, fields, raised light surfaces          |
| `--bc-color-surface-muted`      | `#ede8df` | Quiet grouping and image placeholders         |
| `--bc-color-surface-inverse`    | `#161412` | Focused, ceremonial, and secure dark surfaces |
| `--bc-color-text`               | `#292521` | Default text                                  |
| `--bc-color-text-muted`         | `#70675f` | Supporting copy on light surfaces             |
| `--bc-color-text-inverse`       | `#f8f5ef` | Primary text on inverse surfaces              |
| `--bc-color-text-inverse-muted` | `#bdb4a8` | Supporting copy on inverse surfaces           |
| `--bc-color-accent`             | `#c9a869` | Primary control fills and decoration          |
| `--bc-color-accent-soft`        | `#ead8ae` | Highlights and inverse accents                |
| `--bc-color-accent-strong`      | `#765329` | Small gold-toned text on light surfaces       |
| `--bc-color-border`             | `#dcd5ca` | Default fine rules                            |
| `--bc-color-success`            | `#2f6b50` | Completed/live success                        |
| `--bc-color-warning`            | `#87571d` | Recoverable warning                           |
| `--bc-color-danger`             | `#9b3d38` | Errors and destructive actions                |
| `--bc-color-info`               | `#35627a` | Neutral system information                    |

Bright gold fails normal-text contrast on ivory; use `accent-strong` for text. Semantic colors convey status with words/icons, never by color alone. Pure white and pure black are not brand surfaces.

BrautCloud does not currently expose a global dark-mode toggle. Inverse surfaces are purpose-designed modes for marketing, authentication imagery, onboarding, and QR presentation. Do not mechanically invert the light palette.

## Spacing and layout

The spacing foundation is 4px, exposed from `--bc-space-2xs` (4px) through `--bc-space-4xl` (96px). Most component gaps use 8, 12, 16, 24, or 32px. Marketing section rhythm may use 64–136px through fluid `clamp()` values.

- **Content container:** 1216px / `--bc-container-content`
- **Wide marketing container:** 1408px / `--bc-container-wide`
- **Form container:** 512px / `--bc-container-narrow`
- **Page gutter:** fluid 16–64px / `--bc-gutter`
- **Application grid:** disciplined one or two columns; no decorative overlap
- **Marketing grid:** editorial two-column compositions that collapse in source order
- **Radius:** 2px controls, 4–8px cards/media, 14–22px only for elevated containers, fully round only for icon/status controls

Do not apply the same large radius to every element. Alignment and consistent edge relationships matter more than extra containers.

## Surfaces, borders, and shadows

- Light cards use a one-pixel warm border and `--bc-shadow-sm` only when elevation aids grouping.
- Inverse cards use a low-opacity gold border and deeper shadow against dark backgrounds.
- Inputs always have a visible border before interaction.
- Dashed borders indicate an empty/drop target, not a normal card.
- Use `--bc-shadow-md` for floating editorial captions and `--bc-shadow-inverse` for high-emphasis inverse panels only.
- Decorative rules are one pixel. Avoid glassmorphism except the sticky navigation backdrop, where it preserves context.

## Imagery and iconography

- Wedding photography is editorial: natural crops, restrained saturation, and no generic stock-photo replacement where a real product asset exists.
- Preserve useful subjects at every breakpoint with deliberate `object-position`.
- Content images require descriptive alt text. Repeated gallery items use concise ordered descriptions until user-authored captions exist. Decorative crops and ornaments are hidden from assistive technology.
- Use one Material Symbols outlined icon family. Icons support a label; icon-only actions require `aria-label` and a minimum 44px target.
- Diamonds, monograms, circles, and gold geometry belong to marketing or focused setup moments. Do not scatter them through dashboard cards.

## Shared components and patterns

### Angular layouts

- `app-shell` (`src/app/components/app-shell/`) owns authenticated workspace branding, desktop navigation, mobile bottom navigation, safe-area spacing, and the workspace skip target. Overview, gallery, and upload pages project content into it.
- `app-auth-shell` (`src/app/components/auth-shell/`) owns the split editorial/form composition, responsive single-panel fallback, brand links, title association, and skip target for sign-in, sign-up, and reset-password pages.

### CSS primitives

- `.bc-page`, `.bc-page-header`, `.bc-display`, `.bc-heading`, `.bc-lede`, `.bc-eyebrow`
- `.bc-surface`, `.bc-card`, `.bc-card-header`, `.bc-card-title`
- `.bc-button` plus `--primary`, `--secondary`, `--inverse`, `--quiet`, `--danger`, `--icon`, and `--wide`
- `.bc-field`, `.bc-field-label`, `.bc-input`, `.bc-field-hint`, `.bc-field-error`
- `.bc-status`, `.bc-empty-state`, `.bc-loading-state`, `.bc-spinner`
- `.bc-brand-lockup`, `.bc-brand-mark`, `.bc-skip-link`, `.bc-sr-only`

Use native links for navigation and native buttons for actions. A link styled as a button still remains an anchor. Never add click handlers to non-interactive text.

## Page-family inventory and adaptation

| Family              | Routes                           | Design adaptation                                                                                                                            | Required states                                                                                |
| ------------------- | -------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Marketing           | `/`                              | Full editorial expression: large type, framed imagery, assurance strip, story sections, restrained ornaments, fixed mobile conversion action | Desktop/tablet/mobile navigation, keyboard focus, reduced motion                               |
| Authentication      | `/auth/sign-in`, `/auth/sign-up` | Calm split layout: editorial image establishes brand; practical light form panel carries the task                                            | Default, validation, server error, pending/disabled, mobile single panel                       |
| Password recovery   | `/auth/reset-password`           | Honest unavailable/empty state inside the auth shell; no fake form                                                                           | Informational state and route back to sign-in                                                  |
| Couple overview     | `/app/home`                      | Disciplined dashboard using photography, statistics, live status, and QR card                                                                | Missing event fallback, mobile navigation, compact cards                                       |
| Gallery             | `/app/gallery`                   | Quiet image-first masonry within the workspace shell                                                                                         | Loading, empty, request error, populated/lazy images                                           |
| Upload/contribution | `/app/upload`                    | Obvious drop target and progressive task sections, not a marketing circle CTA                                                                | Empty, selected previews, uploading/disabled, partial/full error, uploaded, destructive action |
| Onboarding          | `/app/onboarding`                | Focused inverse setup experience; serif only for the short invitation, clear form controls for work                                          | Step progress, validation, disabled continue, back, desktop/mobile                             |
| Fallback            | `**`                             | Existing redirects remain authoritative                                                                                                      | Unknown public path → landing; unknown app path → overview                                     |

## Responsive behavior

Design mobile-first in meaning even when desktop has more composition.

- **Below 544px:** one-column content, full-width primary controls where helpful, one-column gallery, two-column thumbnail grids, stacked onboarding actions, safe-area-aware fixed navigation.
- **544–736px:** two-column gallery and three-column thumbnail previews when space permits.
- **736–992px:** split cards may remain, but dashboard and onboarding collapse before text or controls become cramped.
- **Above 992px:** application two-column grids and editorial split layouts are allowed within the max-width container.

Never rely on horizontal scrolling for page layout. Media may crop, not squeeze text. Source order must remain logical when grids collapse. Fixed mobile controls must add bottom safe-area/content clearance.

## Interaction and motion

Motion is intentional and functional:

- Fast feedback: 120ms; standard hover/focus: 180ms; image/state movement: 280ms.
- Controls may move up by one pixel on hover and return on press. Avoid scaling buttons because it shifts nearby layout.
- Image hover zoom is at most 2.5% and never required to understand content.
- Loading uses a small spinner plus readable status text and `aria-live`/`aria-busy` where appropriate.
- Smooth scrolling and transitions are disabled under `prefers-reduced-motion: reduce`; Lenis is not initialized for those users.
- No autoplay, scroll-jacking, parallax, or decorative entrance choreography in application flows.

## Accessibility requirements

1. Target WCAG 2.2 AA contrast. Use dark gold for text on ivory and ivory/muted ivory on noir.
2. Every page family has a skip target. Workspace and authentication layout components own theirs.
3. Keyboard focus is always visible with a three-pixel ring and offset. Do not remove outlines without an equivalent.
4. Interactive targets are at least 44px high/wide. Mobile navigation respects safe-area insets.
5. Form controls have persistent labels, appropriate autocomplete, invalid state, and nearby error text with alert semantics.
6. Loading, empty, error, disabled, and destructive states are distinguishable by copy and structure, not only color.
7. Heading order and landmarks follow page meaning. Do not use headings merely to obtain a font size.
8. Images have useful alt text; decorative icons use `aria-hidden`; icon-only buttons have an accessible name.
9. Content remains usable at 320px and at 200% zoom without horizontal page overflow.
10. Reduced-motion preferences are honored globally.

## Usage guidance

### Do

- Choose a semantic token or primitive before writing a new declaration.
- Keep page CSS for composition that truly belongs to that page.
- Reuse `app-shell` or `app-auth-shell` for pages in those families.
- Pair an expressive heading with straightforward UI copy.
- Add every asynchronous feature's loading, empty, success, and error treatment in the same change.
- Verify representative desktop and mobile layouts with keyboard and overflow checks.

### Do not

- Copy generated reference-folder markup or add it to the runtime build.
- Reintroduce Playfair/Inter or raw `mainrose` values for new UI.
- Use bright gold as small text on ivory.
- Turn dashboard sections into landing-page hero blocks.
- Add pill radii, shadows, ornaments, or uppercase tracking to every element.
- Add page-specific button/input implementations when a primitive exists.
- Use color alone for status, or animation alone for feedback.

## Change checklist

Before merging visual work:

1. Read this document and identify the page family.
2. Use tokens and existing primitives; update the system only for a genuinely reusable need.
3. Exercise default plus empty/loading/error/disabled states that apply.
4. Check keyboard order, focus, labels, alt text, contrast, reduced motion, and 320px layout.
5. Run from `brautcloud-frontend`: `pnpm run format:check`, `pnpm run typecheck`, `pnpm test -- --watch=false`, `pnpm run build`, and `pnpm run security:audit`.
6. `qrcode` is the only allowed CommonJS dependency: `ng-qrcode@21` uses it transitively for QR generation and has no ESM replacement compatible with Angular 21. New CommonJS warnings are not accepted.

## Decisions log

| Date       | Decision                                                                              | Rationale                                                                                                             |
| ---------- | ------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| 2026-07-25 | Cormorant Garamond + Jost become the sole display/body pairing                        | It preserves the reviewed merged landing's personality and removes the competing reference-era Playfair/Inter system. |
| 2026-07-25 | Semantic tokens wrap the ivory/noir/gold palette                                      | Components can express intent and preserve contrast instead of repeating raw colors.                                  |
| 2026-07-25 | Marketing, auth, app, and onboarding use different compositions on shared foundations | The luxury language remains coherent without making software workflows resemble a sales page.                         |
| 2026-07-25 | Workspace and authentication shells own shared navigation/layout                      | Page templates retain content semantics while routing, mobile behavior, and accessibility stay consistent.            |
| 2026-07-25 | Inverse surfaces are contextual, not a global dark mode                               | Purpose-designed dark areas maintain hierarchy and avoid a mechanical palette inversion.                              |
