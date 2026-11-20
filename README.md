# Card Pool Generator

A web application for generating randomized card pools from the Altered TCG. Built with a Spring Boot backend and Vue.js frontend.

## Features

- **Generate Card Pools**: Create randomized card pools with customizable filters
- **Balanced Faction Pools**: Every generation creates one pool per faction and combines them into a single global pool (JSON/Excel export unchanged)
- **Filter Options**:
  - Card Set
  - Sub Type / Card Type
  - Exclude Banned or Suspended cards
  - Hand Cost and Reserve Cost (for example `4+` for 4 or more, `2-` for 2 or less)
  - Text search (name, reference, effect)
- **Multi-language Support**: Available in German, English, Spanish, French, and Italian
- **Export**: Download generated pools as Excel files
- **Saved pool**: the current pool (with its replacements) is saved by the backend, so it survives page changes,
  reloads and restarts; generating a new pool asks before replacing it
- **Visualization**: View cards in a visual grid
- **Pool Analysis** (see below): flag dangerous effects, record team decisions, replace cards and share review reports

## Pool Analysis

Open a pool, then **Analyze Effects**. The **Table** view lists one card per row with its effects, the parts matched
by rules highlighted by severity, and the reasons; the **Cards** view shows each card as a tile with its text and warnings.

- **Rules** describe effects that may be dangerous for a pool: label, severity (HIGH/MEDIUM/LOW), category, reason and
  one or more conditions that must all match (name, main/echo effect, keyword, trigger, hand/reserve cost, card type,
  sub type, set; *contains*, *is exactly* or *regex*, optionally negated), optionally restricted to some factions.
  Use **Flag…** on a card to create a rule from one of its abilities, and **Test on this pool** to preview it.
- **Ability combinations**: the *Ability (trigger + condition + effect)* condition matches one ability whose trigger,
  condition and effect each meet an optional test (*contains*, *is exactly*, *matches regex* or *is empty*), all on the
  **same** ability, in the main effect, the echo effect or both. Example: trigger is exactly `{H}` + condition is empty +
  effect contains `draw a card`. On a card, **Flag… → Combination** pre-fills the three parts from one of its abilities.
  In the table view, abilities are shown as trigger · condition · effect, and an ability matched by a combination is
  outlined with the matching parts highlighted.
- **Team decisions**: *Approve* keeps a card (it is not flagged again unless a new rule matches it); *Reject* removes it
  from future generated pools (option "Exclude cards rejected by the team" in the generator).
- **Replace** proposes cards of the same faction and set that match the pool's generation filters excluding cards already in the pool, rejected cards and cards with unreviewed warnings.
- **Create report** saves a snapshot of the flagged cards with reasons and decisions, viewable at `/report/<id>`
  (listed on the home page) and downloadable as PDF for in-person review.

Rules, decisions, reports and the current pool (`current-pool.json`) are stored as JSON in the data directory (`cardpool.data-dir`, default `data` relative to
the backend working directory, i.e. `backend/data/` with `mvn spring-boot:run`; mounted as a volume in Docker). On first
start, `rules.json` is seeded from `effect-warnings.json`. Committing `backend/data/` keeps a history of rule changes.

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.5.13, Maven
- **Frontend**: Vue 3, TypeScript, Vite, Axios
- **Card data**: [CardsData](https://github.com/Altered-Re-Union/CardsData) CSV export

## Card data

The backend loads every unique card of the [CardsData](https://github.com/Altered-Re-Union/CardsData) export from
`backend/cardsdata/` (`cardpool.cardsdata-dir`) in memory at startup: about 5.5M cards in ~4 s and ~450 MB of heap. Generating a pool then takes a few hundred
milliseconds. This folder is not committed: `./update-cardsdata.sh` downloads only the files the backend reads (for each set
`Unique/<SET>/UniquePrints.csv` and `UniquePrintEffectFragments.csv`, plus the reference tables `EffectFragments`,
`Factions`, `Sets`, `Keywords`, `CardTypes`, `CardSubTypes`, `CardFamilies`, `CardFamilySubTypes`), about 750 MB.
Run it again to update the cards (`./update-cardsdata.sh <tag>` for a given version; `backend/cardsdata/VERSION` tells
which CardsData commit is used), then restart the backend.

Effect texts are rebuilt from the effect fragments: keywords use single brackets (`[Anchored]`) and abilities follow
the CardsData display order. Analysis rules ignore the bracket style (`[[Anchored]]` matches too).

## Project Structure

```
CardPoolGenerator/
├── backend/                 # Spring Boot application
│   ├── src/main/java/
│   │   └── com/cardpool/backend/
│   │       ├── controller/ # REST API endpoints
│   │       ├── model/      # Data models (Card, CardFilter, rules, reports…)
│   │       ├── service/    # Business logic (draws, analysis, storage)
│   │       └── source/     # CardsData CSV loading
│   ├── data/               # Rules, decisions, reports and the current pool (JSON)
│   ├── cardsdata/          # Card data CSV (not committed, see update-cardsdata.sh)
│   └── pom.xml
├── frontend_vue/           # Vue.js application
│   ├── src/
│   │   ├── pages/        # Home, Generator, Pool, Report
│   │   ├── components/   # Vue components (analysis/ for the analysis panel)
│   │   ├── analysis/     # Types and helpers of the analysis
│   │   ├── App.vue       # Main application
│   │   └── main.ts       # Entry point
│   └── package.json
└── README.md
```

## Running the Application

### Prerequisites

- Java 17+, Maven 3.8+, Node.js 18+
- The card data in `backend/cardsdata/`: run `./update-cardsdata.sh` once (`./dev.sh` does it when the folder is missing)

### Development (live reload)

```bash
./dev.sh
```

Starts the backend on `http://localhost:8080` and the Vite dev server on `http://localhost:5173` (open this one: it
proxies `/api` to the backend). Ctrl+C stops both. To run them separately: `cd backend && mvn spring-boot:run` and
`cd frontend_vue && npm run dev`.

### Docker (one command, no local toolchain)

```bash
docker compose up --build
```

Builds the frontend and the backend into a single image (Spring Boot serves the Vue app and the API) and runs it on
`http://localhost`. Rules, decisions and reports are kept in `backend/data/`; card data is mounted read-only from
`backend/cardsdata/` (run `./update-cardsdata.sh` first). Builds run natively on Apple Silicon and cache npm/Maven downloads between builds.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/pool/generate?size=N` | Generate a pool of N cards per faction with filters |
| GET | `/api/form/formValues` | Factions, sets (with default weights) and sub types for the generator |
| POST | `/api/pool/export` | Export a pool as Excel |
| POST | `/api/pool/analyze` | Analyze a pool against the rules and team decisions |
| POST | `/api/pool/replacements` | Replacement candidates for a card of a pool |
| GET/POST/PUT/DELETE | `/api/rules[/{id}]` | Manage dangerous-effect rules |
| POST | `/api/rules/preview` | Test an unsaved rule on a list of cards |
| GET/PUT/DELETE | `/api/verdicts[/{reference}]` | Team decisions (approve/reject) per card |
| GET/POST/DELETE | `/api/reports[/{id}]` | Analysis reports |
| GET/PUT/DELETE | `/api/current-pool` | The saved current pool (`/summary` without its cards) |
| GET | `/api/form/fragments` | Effect fragments, for rules matching fragment ids |

## Usage

1. Start the application (see above) and open it in your browser
2. Fill in the desired filters (set, sub types, costs, text search, set weights…)
3. Select the number of cards per faction and click "Generate" to create the combined pool
4. Use "Visualize" to view cards, "Analyze Effects" to review dangerous effects, or "Export Excel" to download the pool
