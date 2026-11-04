# Atelier: Weather-Responsive Digital Closet & AI Stylist

Atelier is an editorial digital wardrobe and intelligent outfit studio that organizes your entire closet in the cloud, pairs live local or searched weather forecasts with AI-curated daily looks, and provides an interactive mix-and-match canvas and wear-history calendar.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and technical decisions reflect your confirmed preferences and our recommended implementation strategy.

- **Confirmed Decision 1 — Closet Item Capture**: **AI Auto-Tagging from Photos + Manual Editing**. Users can pick a garment photo (via the zero-permission Android Photo Picker or camera capture) and let Gemini (`gemini-3.8-flash`) automatically detect the garment's name, category, primary/secondary colors, warmth rating, water/weather resistance, formality/occasion tags, and season suitability—with full manual editing before and after saving.
- **Confirmed Decision 2 — Weather Integration**: **Automatic Local Weather + Manual City Search Fallback**. Uses Android Location services (`FusedLocationProviderClient` with runtime permission flow) paired with the free, zero-key **Open-Meteo Forecast & Geocoding APIs** for live temperature, feels-like, precipitation probability, wind speed, UV/condition code, and 7-day forecast—plus instant city search and quick-preset cities if location permission is declined or when planning for another city.
- **Confirmed Decision 3 — Core Styling & Planning Suite**:
  1. **AI Daily Style Suggestions**: Generates structured, weather-matched outfit combinations directly from your actual closet inventory for any selected occasion (Casual, Work/Office, Date Night, Outdoor/Active, Formal) with stylist rationale and layering tips.
  2. **Interactive Mix-and-Match Outfit Canvas**: A tactile studio where you layer Outerwear, Tops, Bottoms/Dresses, Footwear, and Accessories from your closet, preview a live **Weather Comfort & Compatibility Meter**, name the look, and save it.
  3. **Outfit Calendar & Wear History Tracker**: Log outfits worn on specific dates, schedule looks for upcoming forecast days, and automatically increment item wear counts and last-worn timestamps.
- **Confirmed Decision 4 — Cloud Sync & Authentication**: **Firebase Firestore + Google Sign-In (`CredentialManager`)**. All closet garments, saved outfits, and calendar wear logs sync across devices under your authenticated Google account (`/users/{userId}/...`).

---

## 1. Overview & Core Concept

- **What It Does**: Transforms getting dressed into a curated, effortless daily ritual. By indexing your real wardrobe with AI vision and cross-referencing live meteorological conditions (temperature, rain risk, wind chill, humidity), Atelier eliminates decision fatigue and ensures every outfit is both stylish and weather-appropriate.
- **Target Audience / Persona**: Style-conscious individuals who want to maximize their existing wardrobe, dress comfortably for shifting weather conditions, and plan outfits ahead of time without manually tagging hundreds of attributes.
- **Key Value**: Bridges the gap between static closet trackers and generic weather apps by grounding AI styling strictly in the actual items you own and the real forecast outside your door.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Authentication & Onboarding Gate**:
   - Launches into an editorial welcome screen featuring silent Google auto-sign-in via Jetpack `CredentialManager` (`GetGoogleIdOption`) and a prominent **"Sign in with Google"** button (`GetSignInWithGoogleOption`).
   - Once signed in, users with an empty closet can either add their first piece with AI Photo Scan or one-tap load a curated **"Starter Capsule Wardrobe"** to immediately explore weather styling and canvas mixing.
2. **Today / Weather Stylist Hub (Home)**:
   - Displays an atmospheric **Live Weather Header Card** showing current city, temperature, feels-like, condition badge, rain probability, wind speed, and a 7-day horizontal forecast strip (tapping any future day previews weather and styles outfits for that day). Includes a city search sheet and GPS refresh toggle.
   - Below the weather banner, users select an **Occasion Chip** (*Everyday Casual, Smart Office, Evening Out, Rainy Commute, Weekend Active, Travel*) and tap **"Curate Looks for This Weather"**.
   - Gemini analyzes the active weather metrics alongside the user's Firestore closet inventory and returns structured **AI Outfit Cards** showing the exact matched closet items, a **Weather Match Score (0–100%)**, layering guidance, and one-tap actions to **"Save Look"** or **"Log Worn Today"**.
3. **Digital Closet & AI Photo Auto-Tagger**:
   - Searchable, filterable wardrobe grid organized by category (*All, Tops, Bottoms, Outerwear, One-Pieces, Footwear, Accessories*), warmth level (*Light, Medium, Warm, Heavy Winter*), and weather tags (*Rainproof, Windproof, Breathable*).
   - Tapping **"+ Add Piece"** opens the Add/Edit Garment sheet: selecting a photo triggers **Gemini Multimodal Auto-Tagging**, populating the garment's title, category, color swatch, fabric/material note, warmth level (1–5), rain resistance, and occasion tags in seconds. Users can tweak any field and save to Firestore.
4. **Mix-and-Match Outfit Studio (Canvas Builder)**:
   - Interactive slot-based outfit composer with dedicated carousels/pickers for **Top / Layer 1**, **Outerwear / Layer 2**, **Bottom / One-Piece**, **Footwear**, and **Accessory**.
   - Features a real-time **Weather Comfort Gauge** that compares the combined thermal rating and rain protection of the selected pieces against the active weather forecast (e.g., *"Ideal for 54°F Breezy — Consider adding a wind-resistant outer layer"*), plus a **"Shuffle / Surprise Me"** button and **"Save Outfit"** action.
5. **Calendar & Wear History**:
   - Interactive monthly/weekly calendar strip paired with a timeline of logged outfits.
   - Tapping any date shows the outfit worn or scheduled for that day, the recorded weather snapshot, and lets the user assign a saved outfit or log a new wear—automatically updating each garment's `wearCount` and `lastWornAt`.

### Visual Identity & Theme

- **Aesthetic Direction**: **Warm Editorial Atelier** — inspired by tactile lookbooks, high-fashion studio moodboards, and architectural weather instruments. Clean, warm, high-contrast surfaces that let garment colors and textures stand out without sterile gray clutter.
- **Color Palette & Mood**:
  - **Light Mode**: Warm Alabaster background (`#FAF7F2`), Ivory Parchment cards (`#F3EFE6`), Espresso Ink primary text (`#1C1917`), Terracotta Clay primary accent (`#C85A32`), Sage Olive secondary accent (`#4A6B5D`), and Sky Slate weather accent (`#3B6E8C`).
  - **Dark Mode**: Obsidian Studio background (`#141210`), Charcoal Velvet surface (`#1F1C19`), Warm Linen text (`#F5F0E6`), Burnished Terracotta primary (`#E07A5F`), and Muted Eucalyptus secondary (`#81B29A`).
- **Typography & Hierarchy**:
  - **Display & Editorial Headers**: **Playfair Display** (locally bundled via `font-util`) for lookbook titles, temperature hero numerals, and section headers.
  - **Body & UI Controls**: **Plus Jakarta Sans** (locally bundled via `font-util`) for crisp category chips, garment metadata, and weather telemetry.
- **Component Styling & Layout**:
  - Full-bleed edge-to-edge layout (`enableEdgeToEdge()`) with safe drawing and navigation bar insets.
  - Adaptive layout using `BoxWithConstraints`: single-column editorial feed on compact phones; two-pane side-by-side layout (weather/canvas on left, closet/recommendations on right) on wide/tablet screens.
- **Interactive Feedback & Motion**:
  - Smooth spring transitions (`spring()`) when swapping items in the Mix-and-Match Canvas, animated weather score indicators, and clear loading states during Gemini photo analysis and outfit curation.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Compact Visual Representation in Cloud Firestore**
  - *Chosen Approach*: Store garment metadata, structured color hex codes, category silhouette identifiers, and downscaled base64 thumbnail data URIs (capped safely under 45 KB per item after bitmap compression) directly inside Firestore documents under `/users/{userId}/closetItems/{itemId}`.
  - *Why*: Provisioned Firebase environments support Firestore Enterprise and Google Sign-In directly without requiring separate Firebase Storage bucket configuration. Compressing picked photos into lightweight thumbnails ensures fast real-time sync across devices while keeping full visual previews in the closet and outfit canvas.
  - *Alternatives Considered*: External image hosting or device-only URI strings (which break when switching devices or clearing local cache).
- **Decision 2: Live Weather via Open-Meteo API + Geocoding**
  - *Chosen Approach*: Integrate Open-Meteo's `/v1/forecast` and `/v1/geocoding` REST endpoints via Retrofit + Moshi, paired with optional device GPS coordinates (`FusedLocationProviderClient`) and a manual city search bar.
  - *Why*: Open-Meteo provides accurate, real-time hourly/daily meteorological telemetry worldwide with zero API key setup friction, ensuring weather-based outfit suggestions work out of the box.
- **Decision 3: Structured Gemini Output (`responseSchema`) for AI Styling & Auto-Tagging**
  - *Chosen Approach*: Use Firebase AI Logic (`gemini-3.8-flash`) with strict `Schema.obj(...)` JSON schemas for both multimodal garment photo tagging and weather-aware outfit recommendations.
  - *Why*: Guarantees deterministic, machine-parseable JSON containing exact `closetItemIds` from the user's inventory, eliminating hallucinated items or broken markdown formatting.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        MainActivity (App Check + EdgeToEdge)                 │
│                                      │                                       │
│                         AuthGate (CredentialManager)                         │
│             ┌────────────────────────┴────────────────────────┐              │
│     [Unauthenticated]                                  [Authenticated]       │
│       SignInScreen                                     AtelierAppShell       │
└───────────────────────────────────────────────────────────────┬──────────────┘
                                                                │
       ┌───────────────────┬───────────────────┬────────────────┴───┐
       ▼                   ▼                   ▼                    ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐      ┌─────────────┐
│  Today & AI │     │   Digital   │     │ Mix & Match │      │  Calendar & │
│ Stylist Tab │     │ Closet Tab  │     │ Studio Tab  │      │ History Tab │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘      └──────┬──────┘
       │                   │                   │                    │
       └───────────────────┴─────────┬─────────┴────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                       AtelierViewModel (StateFlow / UiState)                 │
└───────────┬────────────────────────┬──────────────────────────┬──────────────┘
            ▼                        ▼                          ▼
┌──────────────────────┐  ┌─────────────────────┐  ┌───────────────────────────┐
│  ClosetRepository    │  │  WeatherRepository  │  │     AiStylistService      │
│ (Firebase Firestore  │  │ (Open-Meteo API +   │  │   (Firebase AI Logic      │
│  Custom Database ID) │  │  Location Services) │  │    gemini-3.8-flash)      │
└──────────────────────┘  └─────────────────────┘  └───────────────────────────┘
```

### Data Model & State

All user documents are isolated under `/users/{userId}/...` in the provisioned Firestore database (`R.string.firestore_database_id`) and validated by hardened `firestore.rules`:

1. **`ClosetItem` (`/users/{userId}/closetItems/{itemId}`)**:
   - `id` (`@DocumentId`), `userId`, `name`, `category` (`TOP`, `BOTTOM`, `OUTERWEAR`, `ONE_PIECE`, `FOOTWEAR`, `ACCESSORY`), `subCategory`, `colorName`, `colorHex`, `warmthLevel` (1–5 integer), `isRainResistant` (boolean), `isWindResistant` (boolean), `occasions` (list of strings), `seasons` (list of strings), `notes`, `imageBase64` (optional compressed thumbnail), `wearCount` (long), `lastWornEpochDay` (long), `isFavorite` (boolean), `createdAt` (`@ServerTimestamp`), `updatedAt` (`@ServerTimestamp`).
2. **`Outfit` (`/users/{userId}/outfits/{outfitId}`)**:
   - `id` (`@DocumentId`), `userId`, `name`, `itemIds` (list of `ClosetItem` IDs), `occasion`, `targetWeatherSummary`, `weatherComfortScore` (int), `stylistNotes`, `isAiGenerated` (boolean), `isFavorite` (boolean), `wearCount` (long), `createdAt` (`@ServerTimestamp`), `updatedAt` (`@ServerTimestamp`).
3. **`WearLog` (`/users/{userId}/wearLogs/{logId}`)**:
   - `id` (`@DocumentId`), `userId`, `dateIso` (`YYYY-MM-DD`), `outfitId`, `outfitName`, `itemIds` (list of strings), `occasion`, `weatherTempC` (double), `weatherCondition`, `notes`, `createdAt` (`@ServerTimestamp`), `updatedAt` (`@ServerTimestamp`).

### Interactive Component & State Mapping

- **Weather Bar & City Selector**:
  - On startup, checks location permission; if granted, queries GPS coordinates and fetches Open-Meteo current + 7-day forecast; if denied or unavailable, loads default city or user's last selected city and opens a quick city search dialog powered by Open-Meteo Geocoding.
  - Selecting any day on the 7-day forecast strip updates `activeWeatherDay` in `AtelierViewModel`, dynamically recalculating both AI outfit prompts and the Mix-and-Match Canvas comfort score.
- **AI Photo Auto-Tagger (`AddEditItemSheet`)**:
  - Launching `PickVisualMedia` loads the chosen image, resizes it to a crisp thumbnail bitmap, and invokes `AiStylistService.analyzeGarmentPhoto(bitmap)`.
  - Populates form state reactively so the user can review or adjust any attribute before committing `repository.addClosetItem(...)`.
- **AI Daily Stylist (`TodayStylistScreen`)**:
  - Serializes the user's current `ClosetItem` list (IDs, names, categories, warmth, rain resistance, colors, occasions) + active weather metrics + chosen occasion into a structured prompt for `gemini-3.8-flash`.
  - Parses the JSON response into interactive recommendation cards; tapping **"Save to Outfits"** writes a new `Outfit` document to Firestore, and tapping **"Wear Today"** records a `WearLog` for the selected date and increments `wearCount` on each included `ClosetItem`.
- **Mix-and-Match Studio (`OutfitCanvasScreen`)**:
  - Maintains selected slot IDs (`selectedTopId`, `selectedBottomId`, `selectedOuterwearId`, `selectedOnePieceId`, `selectedFootwearId`, `selectedAccessoryIds`).
  - Computes real-time thermal insulation vs. active temperature and rain/wind protection alerts, allowing one-tap randomize, reset, or save to Firestore.
