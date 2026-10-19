# 🫀 Samsung Health Companion & Calorie Tracker

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2029%2B-green.svg?style=flat&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Gemini AI](https://img.shields.io/badge/AI-Gemini%203.8%20Flash-4285F4.svg?style=flat&logo=google)](https://aistudio.google.com)
[![Model Context Protocol](https://img.shields.io/badge/Protocol-MCP%202.x-black.svg?style=flat)](https://modelcontextprotocol.io)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A privacy-focused Android companion application and **Model Context Protocol (MCP)** server that bridges your **Samsung Health** wearable vitals directly to AI assistants (Claude, Cursor, Codex, Antigravity) with an on-device **Gemini AI Calorie Tracker**, 7-Day Calorie Balance Dashboard, date-wise Food Diary, and raw CSV data export.

---

## 🌟 Key Features

- **📸 Gemini AI Food & Calorie Recognition:** Snap food photos directly in-app or type natural language meal descriptions (e.g. *"2 slices of pepperoni pizza and a coke"*) to receive instant AI estimation of meal type, calories, protein, carbs, and fat.
- **📊 7-Day Calorie Balance Dashboard:** Date-wise visual bar chart comparing **Calories Intake** vs. **Calories Burned** over the last 7 days with daily averages and net deficit/surplus tracking.
- **📅 Date-Wise Food Diary & History:** Complete food log with date navigation controls (`< Prev Day`, `Next Day >`, `Today`) allowing you to view and log food items for any past date.
- **📷 In-App Camera Integration:** Capture meal photos directly using in-app camera controls (`FileProvider`) or pick from your device gallery.
- **🛡️ Privacy-First & On-Device Storage:** Health vitals and food diary history are stored locally in Room SQLite database.
- **🤖 Model Context Protocol (MCP) Server:** Exposes live vitals (`get_live_vitals`, `get_health_summary`) as native MCP tools to Claude Desktop, Cursor, or other LLMs over stdio.
- **📦 Raw Data Exporter:** One-tap export to a structured `.zip` containing organized CSVs covering heart rate, sleep stages, workout HR series, GPS routes, SpO2, blood pressure, glucose, body composition, and temperature.

---

## 🏗️ Architecture & Data Flow

```mermaid
flowchart LR
    A["Samsung Galaxy Watch / Ring"] --> B["Samsung Health App"]
    B -->|"Health Data SDK (Read-Only)"| C["Android Companion App"]
    C --> D[("Local Room Database<br/>+ Gemini AI Estimator")]
    C -->|"One-Tap Export"| E["ZIP of Raw CSVs"]
    C -->|"HTTP POST /sync"| F["FastAPI Receiver :8080"]
    F --> G[("latest_health_data.json")]
    G --> H["Python MCP Server"]
    H --> I["AI Assistant (Claude / Cursor)"]
```

---

## 🛠️ Tech Stack

### Android Client
- **Language:** Kotlin 2.0 (with KSP)
- **UI:** Jetpack Compose + Material 3 Design
- **AI Integration:** Google Gemini AI API (`gemini-3.8-flash`, `gemma-4-26b-a4b-it`)
- **SDK:** Samsung Health Data SDK (`1.1.0`)
- **Database:** Room Database (SQLite with Flow coroutine streams)
- **Networking:** OkHttp 4 + Gson
- **Camera & File Provider:** AndroidX Activity Contracts + FileProvider

### MCP Server & Ingestion Bridge
- **Language:** Python 3.10+
- **Protocol:** Model Context Protocol (MCP SDK) over `stdio`
- **Receiver:** FastAPI + Uvicorn (HTTP POST endpoint with Bearer token authentication)

---

## 🚀 Getting Started

### 1. Prerequisites
- Physical Samsung Galaxy smartphone running **Samsung Health 6.30.2+** *(Emulators do not support Samsung Health Data SDK)*
- Android Studio Ladybug / Meerkat (or newer) with JDK 17+
- Free Gemini API key from [Google AI Studio](https://aistudio.google.com)
- Python 3.10+ (for the MCP server)

---

### 2. Android App Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/himeshsahoo/samsung-health-companion.git
   cd samsung-health-companion
   ```

2. **Add Samsung Health Data SDK:**
   - Download `samsung-health-data-api-1.1.0.aar` from the [Samsung Developer Portal](https://developer.samsung.com/health).
   - Place the file in `app/libs/`:
     ```bash
     app/libs/samsung-health-data-api-1.1.0.aar
     ```

3. **Enable Developer Mode in Samsung Health on your phone:**
   - Open **Samsung Health** on your phone.
   - Go to **Settings > About Samsung Health**.
   - Tap **Developer mode (Samsung Health Data SDK)** and turn **Developer Mode ON**.

4. **Configure Gemini AI API Key:**
   - Open **Food Diary & Calorie Tracker** in the app.
   - Tap the key icon in the top app bar to save your free Gemini API key from [aistudio.google.com](https://aistudio.google.com).

---

## 🔒 Security & Privacy

- **No Hardcoded Credentials:** Authentication tokens and API keys are stored locally in secure device preferences.
- **Proprietary Binaries Excluded:** Official Samsung Health SDK `.aar` binaries and personal data cache files are excluded from version control via `.gitignore`.
- **Local-First Processing:** Food logs, macros, and physiological calculations are processed on-device.

---

## 📄 License

This project is open-source under the [MIT License](LICENSE). 

*Note: Samsung Health and Samsung Health Data SDK are registered trademarks and property of Samsung Electronics Co., Ltd. This project is an independent community tool and is not officially affiliated with or endorsed by Samsung.*
