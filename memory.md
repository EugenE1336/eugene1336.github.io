# Memory

This file is the working project memory for AI agents.

Eligibility, routing between this file and `1c-templates-mcp` (`remember` / `recall`),
fallback when the MCP server is unavailable — see `AGENTS.md → Project memory`.
There are no permanent entries yet.

Entry format (one entry = one self-contained rule). Use English for narrative,
preserve original 1C identifiers (objects, modules, attributes) as-is:

<!--
## YYYY-MM-DD — <short rule title>

- **Scope:** module / subsystem / object where the rule applies (e.g. `Документ.РеализацияТоваровУслуг`).
- **Rule:** what must / must not be done.
- **Why:** consequence of violation (production breakage / data loss / regulatory / data leak).
- **Source:** user request, incident, or external document that established the rule.
-->

## Captured during work (no remember available)

<!-- Populated only when `1c-templates-mcp` is offline; migrate to `remember` once it is back. -->

- **2026-06-04 — Clarify ambiguous requests:** User asked to always clarify their request when necessary; stored in `USER-RULES.md → Communication with the user`.

- **2026-06-05 — EPF/ERF export naming with version suffix:** When collecting/exporting 1C external processors (`*.epf`) or reports (`*.erf`), always save as a **new file** — never overwrite the existing one. Preserve the original base name and append the current version suffix. Example pattern already used in the project: `ЗагрузкаОтпуска_20260601_01.epf` (base name + `_YYYYMMDD_NN`). If a same-day export already exists, increment the sequence number (`_02`, `_03`, …).

- **2026-06-08 — ЗагрузкаОтпуска_Курсор release workflow (mandatory):** On **any** change to `Конфигурации/ЦБУ/Отпуск/Обработки/ЗагрузкаОтпуска_Курсор/`: (1) bump `ВерсияОбработки()` in `src/ExternalDataProcessor.obj.bsl`; (2) build EPF via `python -m v8unpack -B src releases/ЗагрузкаОтпуска_Курсор_ГГГГММДД_NN.epf` from the processing root; (3) **commit + push** to `origin/main` including `src/`, `README.md`, and the new file under `releases/` (EPF in `releases/` is tracked; EPF copies in `Обработки/` root stay gitignored). Source: user request 2026-06-08.

