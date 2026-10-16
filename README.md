# 📱 A Mobile Machine Learning System for Toxic Language Detection and Self-Moderation

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-Android%20SDK-ED8B00?logo=openjdk&logoColor=white)](https://developer.android.com/)
[![Android Studio](https://img.shields.io/badge/Android%20Studio-Hedgehog%2B-3DDC84?logo=androidstudio&logoColor=white)](https://developer.android.com/studio)
[![TensorFlow Lite](https://img.shields.io/badge/ML-TensorFlow%20Lite-FF6F00?logo=tensorflow&logoColor=white)](https://www.tensorflow.org/lite)
[![Firebase](https://img.shields.io/badge/Database-Firebase%20Firestore-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Python](https://img.shields.io/badge/Python-3.9%2B-blue.svg?logo=python&logoColor=white)](https://www.python.org/)

An Android-based machine learning application that detects toxic language in real time and encourages healthier online communication through self-moderation. The system performs low-latency, on-device inference to provide immediate feedback while prioritizing user privacy and collaborative model refinement.

---

## 📌 Overview

Online toxicity has become increasingly common across messaging and social media platforms. This project aims to promote respectful digital communication by detecting toxic text before it is sent.

The application utilizes an on-device Natural Language Processing (NLP) model integrated into a custom Android interface to classify text as toxic or non-toxic. When potentially harmful language is flagged, the app prompts users to self-moderate, tracking self-correction rates and gathering crowdsourced feedback to continuously refine future model iterations.

---

## ✨ Features

- 🔍 **Real-Time Detection:** Instant, on-device text evaluation with low-latency feedback.
- 💬 **Self-Moderation Prompts:** Intercepts flagged content and encourages users to revise toxic input before sending.
- 🔒 **Privacy-First Design:** Core ML inference runs locally without sending raw user messages to an external server.
- 🤝 **Crowdsourced Refinement:** Opt-in system allowing users to submit flagged edge cases (False Positives/Missed Toxicity) to a secure backend to aid retrain operations.
- 📊 **Behavioral Analytics:** Tracks user self-correction rates and active interaction metrics to measure behavioral moderation impact.
- 💡 **Interactive Onboarding:** Guided feature discovery using smooth UI targets to introduce users to self-moderation mechanisms.

---

## 🏗️ System Architecture

```
┌─────────────────────┐      ┌───────────────────────────┐
│     USER INPUT      │      │     SECURITY FILTERS      │
│ (Raw Keystrokes)    ├─────►│ • EditorInfo (Passwords)  │
└──────────┬──────────┘      │ • Regex (Emails/URLs)     │
          │                  └─────────────┬─────────────┘
          ▼                                │
┌─────────────────────┐                    │ Bypass if match
│    INPUT BUFFER     │◄───────────────────┘
│ (StringBuilder)     │
└──────────┬──────────┘
           │
           ▼
┌───────────────────────────┐      ┌───────────────────────────┐
│   TOXICITY CLASSIFIER     │      │    SUBSTITUTION ENGINE    │
│                           │      │                           │
│ 1. Normalization          │      │ 1. Word/Skeleton Lookup   │
│ 2. Feature Extraction     ├─────►│ 2. Phrase Matching        │
│    (TF-IDF / Entity)      │      │ 3. Sentence Reframing     │
│ 3. Logistic Regression    │      │ 4. Grammar Post-Process   │
└───────────────────────────┘      └──────────┬─────────────┘
                                              │
                                              ▼
                       ┌─────────────────────────────────────────────┐
                       │                                             │
                       ▼                                             ▼
            [User Accepts / Revises]                   [User Corrects / Flags]
                       │                                             │
                       ▼                                             ▼
          ┌────────────────────────┐                    ┌────────────────────────┐
          │ Behavioral Analytics   │                    │ Encrypted Local Queue  │
          │  (Correction Metrics)  │                    └───────────┬────────────┘
          └────────────────────────┘                                │
                                                                    ▼
                                                        ┌────────────────────────┐
                                                        │ Firebase Firestore     │
                                                        │ (Crowdsourced Dataset) │
                                                        └────────────────────────┘
```

---

## 🛠️ Tech Stack

### Mobile Frontend & Storage
- **Language:** Java
- **IDE & Build:** Android Studio | Gradle
- **UI & Discovery:** AndroidX, TapTargetView
- **Security:** EncryptedSharedPreferences (Android KeyStore)

### Machine Learning & NLP
- **Engine:** TensorFlow Lite (TFLite)
- **Model Training:** Python, TensorFlow, Scikit-learn, Pandas, NumPy
- **Pipeline:** Tokenization, Sequence Padding, Text Classification

### Backend & Data Collection
- **Cloud Database:** Google Firebase Firestore

---

## 📂 Project Structure

```
.
├── ToxicDetectionML/           # Python ML training & dataset module
│   ├── optimized_weights_v9.json # Production pruned weights payload
│   ├── sample_submission.csv
│   ├── test.csv                # Testing dataset samples
│   ├── test_labels.csv         # Ground-truth evaluation labels
│   ├── train.csv               # Training dataset samples
│   └── train_test_export.py    # Cleaning, TF-IDF training, weight pruning, & JSON export
├── ToxicLanguageKeyboard/      # Android IME application codebase (Java / Gradle)
│   ├── .idea/
│   ├── app/                    # IME Application source code, resources, assets
│   ├── gradle/
│   ├── .gitignore
│   ├── build.gradle.kts        # Project Gradle build configuration
│   ├── firestore.rules         # Firebase Firestore security rules
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   └── settings.gradle.kts     # Gradle settings configuration
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (Hedgehog or newer recommended)
- JDK 17
- Android SDK (API Level 24+)
- Python 3.9+ (for model training/experiments)

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/xxenderconxx/A-Mobile-Machine-Learning-System-for-Toxic-Language-Detection-and-Self.git
   cd A-Mobile-Machine-Learning-System-for-Toxic-Language-Detection-and-Self
   ```

2. **Configure Firebase:**
   - Create a project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android app matching package name: `com.example.toxiclanguagekeyboard`.
   - Download `google-services.json` and place it in the `app/` directory.
   - Enable **Cloud Firestore** in test or production rules.

3. **Build Application:**
   - Open the cloned repository in Android Studio.
   - Let Gradle sync dependencies automatically.
   - Connect an Android device or launch an emulator.
   - Run the project (`Shift + F10` / `Cmd + R`).

---

## 🐳 Docker Repository

The project Docker image is published on [Docker Hub](<https://hub.docker.com/r/xxenderconxx/toxic-language-detection-thesis>). The repository includes the Docker build files and the project folders shown below:

```text
.dockerignore
.github/
Dockerfile
LICENSE.md
README.md
requirements-docker.txt
revision-evidence/
ToxicDetectionML/
ToxicLanguageKeyboard/
ToxicRephrasing/
```

### Pull the published image

Make sure Docker Desktop or Docker Engine is running, then pull the published tag:

```bash
docker pull xxenderconxx/toxic-language-detection-thesis:sha-0c07262
```

Confirm that Docker downloaded it:

```bash
docker image ls xxenderconxx/toxic-language-detection-thesis
```

### Build from the repository files

If you have cloned or downloaded the repository containing the `Dockerfile`, run this command from that repository's root folder:

```bash
docker build -t toxic-language-detection-thesis:local .
```

The Dockerfile and `requirements-docker.txt` define the container build. The Docker Hub page links to the published image; the root `README.md` in that repository provides the project-specific container usage details.

---

## 🧠 Machine Learning & Feedback Pipeline

* **Model Pipeline:** Trained on standard toxicity datasets via sequence tokenization, embedding, and export to an optimized `.json` model format.
* **Inference Latency:** On-device processing delivers predictions in **< 50ms**, ensuring real-time responsiveness without delaying user typing.

---

## 📊 Performance & Metrics

| Metric | Score |
|---|---:|
| **Inference Latency** | 0.23 ms |
| **Accuracy** | 93.98% |
| **Precision** | 68.98% |
| **Recall** | 69.68% |
| **F1-Score** | 69.33% |

---

## 📸 Screenshots

```
screenshots/
├── onboarding.png      # Guided onboarding (TapTargetView)
├── keyboard_input.png  # Real-time text evaluation
├── self_moderation.png # Self-correction warning prompt
└── research_stats.png  # User correction metrics & milestones
```

---

## 🤝 Contributing

Contributions are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/NewFeature`)
3. Commit your changes (`git commit -m 'Add NewFeature'`)
4. Push to the branch (`git push origin feature/NewFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Endercon**  
GitHub: [@xxenderconxx](https://github.com/xxenderconxx)
