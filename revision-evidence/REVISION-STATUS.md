# Application revision evidence — 9 October 2026

Source repository: C:\Users\xxend\Documents\GitHub\A-Mobile-Machine-Learning-System-for-Toxic-Language-Detection-and-Self

Changes are in ToxicLanguageKeyboard and ToxicDetectionML; results are in revision-evidence. The thesis and original D: projects were not edited. Changes are uncommitted.

## Plan status

| Task | Completed | Remaining limit |
|---|---|---|
| 1. Privacy | Shared encrypted storage, legacy migration, no plaintext fallback, removed clipboard history, disabled backups/transfers, consent and authenticated UID-owned submissions. Eight live Firebase checks pass. Phone confirms encrypted persistence, empty legacy storage, no clipboard history and three saved dictionary entries. | Historical lost dictionary entries cannot be reconstructed. Synthetic Firebase test record remains. |
| 2. Parity | Original v55 export reproduced exactly from correct repository training.py. Corrected normalization, per-word character features, separate TF-IDF block normalization and raw entity flags; export preserves full vocabulary and precision. All 1,063 saved comparisons agree; maximum probability difference 1.45e-15; zero normalization disagreements. Permanent regression fixture included. | Professional-context adjustments are separate app behavior. |
| 3. Latency | Three matching version1.3.3 debug runs, each100 warm-up/600 recorded trials, on Xiaomi M2101K6G, nubia Z2465N and Samsung SM-A525F. Core medians1.236–5.183 ms and p951.985–7.292 ms. See DEVICE-BENCHMARK-COMPARISON.md for per-device measurements. | Core only; excludes loading, unknown lookup, dispatch,300 ms debounce and rendering. One run per phone; no verified low-end category or broad device-coverage claim. |
| 4. False positives | Separate model/rule results; fixed single-letter and ordinary-word skeleton triggers. On all 63,978 labeled test rows: classifier FPR7.20%, rule-only FPR18.66%, combined FPR20.29%, combined recall95.05%. 49 sanity cases retained separately. | Original expert replay samples and sufficient source/procedure details are unavailable. Disclose non-reproducibility and retain the historical34.8% finding; revised results are separate. See EXPERT-REPLAY-DISCLOSURE.md. Context/negation errors remain. |
| 5. Entity baseline | Independently refit TF-IDF-only classifier on identical training records/features, solver/C/weights/seed, with its own validation-only threshold. Both models evaluated on 63,978 labeled test rows; results below. | Binary person/group token indicators are not named-entity recognition or target classification. |
| 6. Provenance | Original export reproduced exactly. Train127,656/validation31,915; test63,978, with57,735 negative/6,243 positive under any-six-label target. Threshold selection validation-only. Zero ID/raw-text overlaps. Corrected export has40,002 full-precision features, threshold0.78. | Distinct normalized overlaps: train/validation179, train/test208, validation/test69. Do not claim zero overlap after preprocessing. |
| 7. Counters | Count displayed alerts, successful edits and explicit Continue once. Tested sequence:4 detections/1 edit/1 ignored; failed edits/dismissal do not count as edits/ignored. | Complete live UI counter sequence and participant statistics not evaluated. |
| 8. Correction UI | Informational unknown-word review on tap, one popup, stale results discarded, guarded suffix replacement. Fixed empty selections and duplicated space in buffer. User confirms cleanup works and background swipes are blocked on phone. Three dictionary entries retained in encrypted store. | Full editor/cursor/restart matrix unperformed; English-only scope remains. |
| 9. Paper evidence | This report and measured outputs replace earlier unresolved-source conclusions. Runtime is JSON logistic regression, not TensorFlow Lite. | Manuscript owner must synchronize claims and mark unavailable/unperformed checks accurately. |

## Independent classifier evaluation

All63,978 labeled test rows, any-six-label target; excludes explicit rules, personal overrides and professional adjustments. Each threshold chosen on validation.

| Model | Threshold | Accuracy | Precision | Recall | F1 | FPR |
|---|---:|---:|---:|---:|---:|---:|
| Independently refit TF-IDF only |0.70|90.82%|51.74%|87.52%|65.03%|8.83%|
| TF-IDF + binary entity flags |0.78|91.95%|55.82%|84.08%|67.09%|7.20%|

Entity model improves F1 and lowers FPR at its selected operating point, with lower recall. This does not establish target attribution. Earlier deployed Java scores used different feature calculations.

Original model SHA256:6652cbdb3a19e8359bdb052c71b9bc594919e043510b1136a72a4922f8a45fcd.
Corrected model SHA256:f0eeee70d671abe9e6c1e3c95306391178b830a7d3e1eac3c07e12ede53de16a.
Corrected export represents the same reproduced fitted model with complete vocabulary and precision; no new dataset or entity-model threshold. Historical v55 export preserved.

## Verification and results

Debug APK builds; six focused RevisionRegressionTest tests pass. Real-phone RevisionDeviceBenchmarkTest and RevisionDeviceStorageTest pass. Eight Firebase checks pass. Whole legacy test suite not claimed to pass.

Files in revision-evidence:

- android-device-benchmark.json: saved phone results.
- android-storage-verification.txt: synthetic storage verification, no dictionary text.
- firebase-live-verification.json: authorization checks, no tokens.
- python-java-parity-report.json: comparison totals.
- model-reproduction.json: exact original export reproduction.
- current-split-audit.json: split sizes/overlaps.
- entity-ablation-and-test-evaluation.json: independently trained baseline and feature-model confusion counts.
- sample-evaluation.csv and sample-evaluation-summary.json: model and combined sanity results.

Android benchmark also saves app-private files/revision-device-benchmark.json and logs under RevisionBenchmark in Android Studio Logcat. The copied JSON above is the readable saved result.

## Remote benchmark build — 10 October 2026

Version 1.3.3 adds Run Benchmark on both the welcome screen and Settings, plus a Share Results screen. Keyboard activation and overlay permission are not needed for this test. Instrumentation and the app screen now share one fixed benchmark implementation.

All six existing regression tests pass. RemoteBenchmarkTest passed on the Xiaomi after foregrounding the app for MIUI: it pressed Run Benchmark and verified the exported JSON, content URI/read permission, model hash, version and trial counts. Initial automated launches were blocked by MIUI background-activity restrictions; the final foreground test passed.

The same APK completed 100 warm-up and600 recorded trials on Xiaomi M2101K6G. Classifier median0.351 ms, mean0.388 ms, p950.588 ms; core detection median1.499 ms, mean1.443 ms, p952.803 ms. Full data: xiaomi-remote-benchmark.json. These are core timings, not visible keyboard-response timings. Three matching remote device results are now available; see DEVICE-BENCHMARK-COMPARISON.md.

Tester instructions: REMOTE-BENCHMARK.md. APK identity: remote-benchmark-build.json.


## Full test-set model and rules evaluation — 10 October 2026

Ran the production Java classifier and explicit SubstitutionEngine rules on the exact 63,978 labeled test rows. Threshold .78 was fixed using validation data. This run excludes per-user overrides and professional-context adjustment. The original expert replay remains unavailable, so this is a separate new evaluation of the revised version.

| Decision layer | False positives | True negatives | False-positive rate | Recall |
|---|---:|---:|---:|---:|
| Classifier alone | 4,155 | 53,580 | 7.20% | 84.08% |
| Explicit dictionary/rules with phrase suppression | 10,775 | 46,960 | 18.66% | 87.70% |
| Combined classifier and rules | 11,712 | 46,023 | 20.29% | 95.05% |

The explicit-rule layer raises false alarms on this test set, while improving combined recall. The 20.29% figure does not reproduce the experts' historical34.8% and should not be compared as though the samples and conditions were identical.

The JSON summary and text-free row-level predictions (record ID, target, classifier/rule/combined outputs) are in full-pipeline-test-evaluation.json and full-pipeline-test-predictions.csv. No test comment text was saved in the results. The run log and reusable evaluator are alongside them. Model and rules hashes and evaluation selection are recorded in the JSON.

