# VidGrab 3.1 design

The previous design used oversized headings, sparse green surfaces and mostly default controls. This revision adopts a compact media-studio layout: obsidian/violet surfaces, a branded illustrated Home card, artwork-led results, grouped quality tiles, custom floating navigation and denser library rows.

The result screen reduces the link input to a source strip so artwork, format choice and the download action get priority. Approximate output sizes use a visible tilde; unknown output sizes stay unknown. Connection work uses indeterminate progress; a percentage appears only after measured bytes arrive and a usable whole-transfer total exists. Video and audio bytes are aggregated rather than resetting between streams.

Controls use short spring feedback and bounded transitions. There are no continuous decorative idle animation loops or expensive full-screen live blurs. Large text is allowed to wrap in quality cards, and interactive controls retain at least 48dp targets.

Primary references consulted:

- Apple Human Interface Guidelines — [Layout](https://developer.apple.com/design/human-interface-guidelines/layout) and [Design principles](https://developer.apple.com/design/human-interface-guidelines/design-principles/): hierarchy, grouping and content priority.
- Google — [Material Expressive research](https://design.google/library/expressive-material-design-google-research): action containment, color/shape hierarchy and navigation familiarity.
- Android — [ABIs](https://developer.android.com/ndk/guides/abis): CPU architecture is separate from Android OS version.

This is an original Compose implementation. Actual perceived polish and frame timing still depend on the display, device and user preference; a software emulator cannot establish phone FPS performance.
