// ============================================================================
// Javi User Manual - Style Module
// ============================================================================
// Separates formatting and visual constants from content.
//
// Usage:
//   #import "javi_style.typ": *
//
// ============================================================================

// ============================================================================
// DOCUMENT SETUP
// ============================================================================

#let setup(body) = {
  set text(font: "Times New Roman", size: 10pt)
  set heading(numbering: "1.1")
  set par(leading: 0.55em, justify: true)
  body
}

// ============================================================================
// THEME CONFIGURATION
// ============================================================================

// --- Color Palette ---
#let theme-colors = (
  // Semantic colors
  info: blue.lighten(95%),
  info-stroke: blue.lighten(80%),
  note: yellow.lighten(95%),
  note-stroke: yellow.darken(20%),
  warning: red.lighten(95%),
  warning-stroke: red.lighten(70%),
  success: green.lighten(95%),
  success-stroke: green.lighten(80%),
  // Key/command highlight
  key-bg: gray.lighten(92%),
  key-stroke: gray.lighten(60%),
  cmd-bg: blue.lighten(93%),
  cmd-stroke: blue.lighten(70%),
  // Table colors
  header-bg: gray.lighten(85%),
  alt-row: gray.lighten(95%),
)

// --- Typography ---
#let theme-font-sizes = (
  body: 10pt,
  small: 8pt,
  medium: 9pt,
  large: 11pt,
  title: 18pt,
  subtitle: 13pt,
)

// --- Spacing ---
#let theme-spacing = (
  small: 4pt,
  medium: 8pt,
  large: 12pt,
  xlarge: 16pt,
)

// --- Strokes ---
#let theme-strokes = (
  thin: 0.5pt,
  normal: 1pt,
)

// ============================================================================
// TITLE PAGE
// ============================================================================

#let title-page(title: "", subtitle: "", author: "", date: "") = {
  align(center + horizon)[
    #text(size: theme-font-sizes.title, weight: "bold")[#title]
    #v(theme-spacing.medium)
    #text(size: theme-font-sizes.subtitle, style: "italic")[#subtitle]
    #v(theme-spacing.xlarge)
    #text(size: theme-font-sizes.body)[#author]
    #v(theme-spacing.small)
    #text(size: theme-font-sizes.small, fill: gray)[#date]
  ]
  pagebreak()
}

// ============================================================================
// INFORMATION BLOCKS
// ============================================================================

#let info-block(content, type: "info") = {
  let (block-fill, block-stroke) = if type == "warning" {
    (theme-colors.warning, theme-colors.warning-stroke + theme-strokes.thin)
  } else if type == "note" {
    (theme-colors.note, theme-colors.note-stroke + theme-strokes.normal)
  } else if type == "success" {
    (theme-colors.success, theme-colors.success-stroke + theme-strokes.normal)
  } else {
    (theme-colors.info, theme-colors.info-stroke + theme-strokes.normal)
  }

  block(
    fill: block-fill,
    inset: theme-spacing.medium,
    radius: 4pt,
    stroke: block-stroke,
    width: 100%,
    content,
  )
}

#let note-box(content) = info-block(content, type: "note")
#let warning-box(content) = info-block(content, type: "warning")
#let tip-box(content) = info-block(content, type: "success")
#let info-box(content) = info-block(content, type: "info")

// ============================================================================
// COMMAND AND KEY FORMATTING
// ============================================================================

// Format a key name inline (e.g., Ctrl-F, F8, Escape)
#let key(name) = {
  box(
    fill: theme-colors.key-bg,
    inset: (x: 3pt, y: 1.5pt),
    radius: 2pt,
    stroke: theme-colors.key-stroke + 0.5pt,
    text(size: theme-font-sizes.small, font: "Courier New", name),
  )
}

// Format a colon command inline (e.g., :help, :w, :ai chat)
#let cmd(name) = {
  box(
    fill: theme-colors.cmd-bg,
    inset: (x: 3pt, y: 1.5pt),
    radius: 2pt,
    stroke: theme-colors.cmd-stroke + 0.5pt,
    text(size: theme-font-sizes.small, font: "Courier New", ":" + name),
  )
}

// Format literal text / typed input
#let lit(content) = {
  text(font: "Courier New", size: theme-font-sizes.small, content)
}

// ============================================================================
// COMMAND TABLES
// ============================================================================

// Two-column command reference table (command, description)
#let cmd-table(..rows) = {
  let data = rows.pos()
  table(
    columns: (auto, 1fr),
    inset: (x: 6pt, y: 4pt),
    stroke: theme-strokes.thin + gray.lighten(70%),
    fill: (_, row) => if row == 0 { theme-colors.header-bg } else { none },
    align: (left, left),
    [*Command*], [*Description*],
    ..data.flatten(),
  )
}

// Three-column key reference table (key, vi equivalent, description)
#let key-table(..rows) = {
  let data = rows.pos()
  table(
    columns: (auto, auto, 1fr),
    inset: (x: 6pt, y: 4pt),
    stroke: theme-strokes.thin + gray.lighten(70%),
    fill: (_, row) => if row == 0 { theme-colors.header-bg } else { none },
    align: (left, left, left),
    [*Key*], [*Vi*], [*Description*],
    ..data.flatten(),
  )
}

// Two-column config table (setting, description)
#let config-table(..rows) = {
  let data = rows.pos()
  table(
    columns: (auto, 1fr),
    inset: (x: 6pt, y: 4pt),
    stroke: theme-strokes.thin + gray.lighten(70%),
    fill: (_, row) => if row == 0 { theme-colors.header-bg } else { none },
    align: (left, left),
    [*Setting*], [*Description*],
    ..data.flatten(),
  )
}

// ============================================================================
// SCREENSHOT PLACEHOLDER
// ============================================================================

// Placeholder for a future screenshot or diagram.
// Replace with: #figure(image("path.png", width: 80%), caption: [...])
#let screenshot-placeholder(caption) = {
  block(
    fill: gray.lighten(93%),
    inset: theme-spacing.medium,
    radius: 4pt,
    stroke: (dash: "dashed", paint: gray.lighten(60%), thickness: theme-strokes.thin),
    width: 100%,
    align(center)[
      #text(size: theme-font-sizes.small, fill: gray.darken(30%), style: "italic")[
        _Screenshot:_ #caption
      ]
    ],
  )
}

// ============================================================================
// SECTION FORMATTING
// ============================================================================

#let section-note(content) = {
  text(size: theme-font-sizes.medium, style: "italic", fill: gray.darken(20%), content)
}
