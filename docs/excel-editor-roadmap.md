# KP-Data-Sync In-App Excel Editor Roadmap

## Frozen baseline
The existing in-app `.xlsx` flow remains the compatibility baseline:
- open shared XLSX inside the app
- edit cells
- save to the same shared R2-backed file
- version-guarded saves
- Admin/Cluster Head publish to Reports
- published files lock normal users

## Feature phases
### Phase 1
Copy/cut/paste, insert/delete rows and columns, merge/unmerge, undo/redo, clear cells, basic formatting, row height and column width.

### Phase 2
Formula support for SUM, MIN, AVERAGE, arithmetic (+, -, *, /) and percentage, with basic recalculation for supported formulas.

### Phase 3
Find/search, find/replace, fill series, number/date/percentage formats, wrap text, freeze header, sort and filter helpers.

### Phase 4
Borders, font size/name, underline, hide/unhide rows/columns, duplicate/rename/delete sheets, basic date functions and simple conditional formatting.

## Compatibility rule
This is a targeted spreadsheet feature set for KP-Data-Sync, not a full Excel clone. Existing workbook entries are preserved where possible, and unsupported advanced Excel constructs are not silently converted into unrelated data.
