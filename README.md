# MapApp: Visual-GNSS Navigation & Safety Monitor

### 1. General Overview

**MapApp** is an advanced Android application engineered for real-time location tracking and safety monitoring. It combines classic Android fundamentals with modern Jetpack libraries, delivering continuous background location processing, speed monitoring, and real-time safety alerts based on a resilient Service-Oriented Architecture (SOA).

---

### 2. Technologies Used

* **Hybrid Foreground Service** (Started & Bound Service): Ensures persistent background execution.
* **Handler & Messenger**: Inter-Process Communication (IPC) mechanism.
* **Broadcast Receiver, System Notifications, Android Permissions**.


* **Jetpack Compose**: Declarative UI framework.
* **ViewModel & StateFlow/SharedFlow**: UI state management and reactive data streams.
* **WorkManager**: Background task scheduling and management.
* **Room Database**: Persistent storage for alert logs and speed violations.


* **OSMDroid**: Open-source mapping library supporting smooth rendering and offline map storage.
* **Retrofit**: Type-safe HTTP client for calling REST APIs in Android/Kotlin.

---

### 3. System Architecture & Layer Communication

The system follows a **Service-Centric MVVM** architecture, where `MainViewModel` serves as the central orchestrator coordinating data between the Service, UI, and WorkManager.

```
[ GPS ] ──► [ LocationForegroundService ] ──► [ MainViewModel ]
                    │                              │
                    ▼                              ├──► [ Location API ]
                [ Room DB ]                        │      (Address / Route)
                                                   │
                                                   ├──► [ WorkManager ]
                                                   │      (Periodic Tasks)
                                                   │
                                                   └──► [ Compose UI ]
                                                          (StateFlow)
```

#### Layer Communication Mechanics

**ViewModel ↔ Service Communication (Handler & Messenger):**
* `MainViewModel` connects to `LocationForegroundService` via a **Handler & Messenger** pair.
* Data (Location, Speed, Settings) is packaged into `Bundle` objects and sent via `Message` instances.
* **Bi-directional Commands**: ViewModel sends execution commands (Start/Stop service, Toggle simulation), while the Service streams live location/alert payloads back to the ViewModel.

**ViewModel ↔ WorkManager Communication:**
* `MainViewModel` directly communicates with and manages `WorkManager` to initialize, schedule, or cancel periodic tasks (e.g., triggering periodic safety check / anti-drowsiness sessions).

**ViewModel ↔ Location API Communication:**
* `MainViewModel` directly communicates with the `Location API` to retrieve location addresses and calculate routes from the start point to the destination.

**UI Updates (Reactive Stream):**
* `MainViewModel` converts data received from the Service into `StateFlow` / `SharedFlow` streams.
* `Jetpack Compose` UI observes these flows to automatically update the UI in real time.



#### Component Breakdown

1. **`LocationForegroundService`**: The core location engine. Manages GPS signal reception, calculates speed, generates simulated data (Simulation Mode), and displays system notifications.
2. **`MainViewModel`**: The central coordinator. Acts as a Messenger Client connecting to the Service, manages `WorkManager` invocations, and maintains the UI state.
3. **`WorkManager`**: Orchestrated by `MainViewModel` to execute periodic safety tasks independently of the UI lifecycle.
4. **`Location API`**: Orchestrated by `MainViewModel` to retrieve location addresses and calculate routes between the start and destination points.
5. **`db` (Room Database)**: Stores alert logs and speed violation records persistently.
6. **`ui` (Jetpack Compose)**: The declarative UI layer that receives data from `MainViewModel` and responds to user events.

---

### 4. Key Features

* **Persistent Background Tracking**: Continuous coordinate updates even when the app is minimized, powered by a Foreground Service.
* **Real-Time Dynamic Dashboard**: Live updates for coordinates, moving speed, GPS connection status, and visual alerts built on Jetpack Compose.
* **Speed Monitoring**: Compares live/simulated speed against user-configured limits, automatically triggering system notifications and in-app modal alert dialogs.
* **Anti-Drowsiness / Periodic Checks**: `MainViewModel` triggers `WorkManager` to coordinate periodic driver alertness checks.


* **Service-Side Data Simulation**: Enables testing of alert logic without real movement (automatically generates random speeds between 0–20 km/h directly inside the Service).
* **OSMDroid Open Maps Integration**: Renders location and movement trails smoothly on an open-source map view.
* **System Awareness**: Monitors system GPS provider status toggles (ON/OFF) in real time with immediate UI status feedback.