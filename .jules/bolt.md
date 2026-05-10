## 2026-05-10 - Fast hex encoding
**Learning:** The manual bitwise hex encoding using `Character.forDigit` string builder appending is unnecessarily slow and memory intensive on recent Java versions.
**Action:** Use `java.util.HexFormat.of().formatHex(bytes)` instead.
