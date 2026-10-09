# Three-device benchmark comparison — 10 October 2026

All three submitted files match protocol revision-core-v1, debug APK version 1.3.3 (code 4), and the current repository model/rule SHA-256 hashes. Each run contains 100 warm-up and 600 recorded trials over the same six fixed inputs. Recorded trial counts total 1,800; percentile values below are per device, not pooled.

| Device | Android | Processor | Classifier mean / median / p95 (ms) | Core detection mean / median / p95 (ms) |
|---|---|---|---:|---:|
| nubia Z2465N | 16 | UMS9620S | 0.313 / 0.292 / 0.452 | 1.115 / 1.274 / 1.985 |
| Xiaomi M2101K6G | 13 | SM7150 | 0.282 / 0.263 / 0.404 | 1.109 / 1.236 / 2.057 |
| samsung SM-A525F | 14 | SM7125 | 1.407 / 1.271 / 2.107 | 4.276 / 5.183 / 7.292 |

Core median ranges from 1.236 to 5.183 ms; core p95 ranges from 1.985 to 7.292 ms. These demonstrate the measured core runtime on the three tested phones. They do not establish performance on every Android phone or satisfy a separate low-end-device claim. No market-tier labels are inferred from lag or timing.

Scope: shared classifier/rule core; excludes model loading, unknown-word lookup, IME dispatch, existing 300 ms debounce and popup rendering. Full visible keyboard-response latency remains unmeasured by this test.

One submitted run per phone; debug build; six short fixed inputs; remote environmental conditions were not recorded or controlled. Individual trial samples are unavailable, so no pooled p95, confidence intervals, or statistical significance claims are made. The Samsung run is slower in these measurements; its cause cannot be determined from the files.

Raw source files are preserved unchanged under device-benchmarks/. Exact values and hardware metadata are in device-benchmark-comparison.csv. Test completion timestamps are stored in UTC; all three occurred on 10 October 2026 in Singapore time.

The three submitted files supersede the earlier single Xiaomi run for this comparison; the earlier run remains archived.
