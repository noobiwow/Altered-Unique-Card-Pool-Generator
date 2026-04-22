# Card Pool Generator

A web application for generating randomized card pools from the Altered TCG. Built with a Spring Boot backend and Vue.js frontend.

## Features

- **Generate Card Pools**: Create randomized card pools with customizable filters
- **Filter Options**:
  - Faction (Ordis, Bravos, Yzmir, Muna, Axiom, Lyra)
  - Card Set
  - Sub Type / Card Type
  - Exclude Banned or Suspended cards
  - Cost range (min/max)
  - Text search (name, reference, effect)
- **Multi-language Support**: Available in German, English, Spanish, French, and Italian
- **Export**: Download generated pools as Excel files
- **Visualization**: View cards in a visual grid
- **Effect Analysis**: Upload Excel files to analyze card effects

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.5.13, Maven
- **Frontend**: Vue 3, TypeScript, Vite, Axios
- **External API**: Altered TCG Cards API (cards.alteredcore.org)

## Project Structure

```
CardPoolGenerator/
├── backend/                 # Spring Boot application
│   ├── src/main/java/
│   │   └── com/cardpool/backend/
│   │       ├── controller/ # REST API endpoints
│   │       ├── model/      # Data models (Card, CardFilter, etc.)
│   │       ├── service/   # Business logic
│   │       └── repository/ # Data access
│   └── pom.xml
├── frontend_vue/           # Vue.js application
│   ├── src/
│   │   ├── components/    # Vue components
│   │   ├── App.vue       # Main application
│   │   └── main.ts       # Entry point
│   └── package.json
└── README.md
```

## Running the Application

### Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.8+

### Backend

```bash
cd backend
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`

### Frontend

```bash
cd frontend_vue
npm install
npm run dev
```

The frontend will start on `http://localhost:5173` (default Vite port)

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/pool/generate` | Generate a card pool with filters |
| GET | `/api/pool/export` | Export generated pool as Excel |
| POST | `/api/pool/import/stats` | Analyze effects from Excel file |

## Usage

1. Start both backend and frontend
2. Open the frontend in your browser
3. Fill in the desired filters (faction, set, card type, etc.)
4. Select the number of cards and locale
5. Click "Generate" to create a pool
6. Use "Visualize" to view cards or "Export" to download as Excel
