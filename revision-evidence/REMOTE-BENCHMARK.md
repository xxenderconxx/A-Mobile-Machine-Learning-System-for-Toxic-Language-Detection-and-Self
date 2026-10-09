# Remote phone benchmark — version 1.3.3

Requires Android 8.0 or newer. Send the same ToxicKeyboard-benchmark-1.3.3.apk to all testers.

1. Install and open the app.
2. On the welcome screen, tap **Run Benchmark**. If already set up, scroll to **Run Benchmark** in Settings.
3. Close other apps before starting. On the Phone Benchmark screen, tap **Run Benchmark** and keep the screen open until it finishes.
4. Tap **Share Results**, choose your messaging or file-sharing app, and send the attached JSON file to the researcher.

No keyboard activation, overlay permission, Android Studio, computer or test companion is needed. Do not send just a screenshot: the JSON file includes the complete results and version information.

The benchmark uses six fixed synthetic inputs, 100 warm-up trials and 600 recorded trials. It reports classifier and shared core model/rule timing (mean, median and p95), phone model, Android version, RAM, processor, completion time, app/build version and model/rule hashes. It does not read or export messages, clipboard, dictionaries, participant counters or account identifiers. Sharing happens only when the tester chooses a destination.

This measures core detection speed, excluding model loading, unknown-word lookup, keyboard dispatch, the 300 ms delay and popup rendering. It is not a full keyboard-response benchmark.

Results stay on the phone and can be shared again after reopening the benchmark screen. Leaving the screen during a run cancels that run; rerun it with the screen open.
