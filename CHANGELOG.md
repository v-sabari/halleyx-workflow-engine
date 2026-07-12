# Changelog

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed
- Renamed top-level project folders for clarity: `workflow-engine/` → `backend/`, `FrontEnd/` → `frontend/`. No source code was modified as part of this rename.
- Removed accidentally-committed artifacts that didn't belong in version control: upload zips inside the frontend folder (`eslint.config.zip`, `package-lock.zip`, `src.zip`), an unrelated image committed under frontend styles, a stray empty root-level `package-lock.json`, and the default Vite-scaffold `README.md`/`.gitignore` inside the frontend folder (superseded by the root-level versions).

### Security
- `POST /api/v1/keys/issue` now requires a valid `X-Bootstrap-Secret` header (per the fix documented directly in `ApiKeyManagementController`'s code comments — this endpoint was previously unauthenticated).

## [1.0.0]

No dated release history was provided in any uploaded file. `pom.xml` declares `<version>1.0.0</version>` — use this section going forward to record real changes as they ship.
