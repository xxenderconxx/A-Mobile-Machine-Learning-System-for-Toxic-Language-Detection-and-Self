import json
import hashlib
import joblib
import re
import numpy as np
import pandas as pd
from scipy.sparse import hstack, csr_matrix
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import f1_score, precision_score, recall_score, accuracy_score, classification_report
from sklearn.model_selection import train_test_split

PERSON_TOKENS = {"you", "u", "he", "she", "him", "her", "yourself"}
GROUP_TOKENS = {"they", "them", "people", "everyone", "group"}

def clean_text(text):
    if not isinstance(text, str):
        return ""
    text = text.lower()
    # Normalize leetspeak matching Java tokenizer
    text = (text.replace("@", "a")
                .replace("0", "o")
                .replace("1", "i")
                .replace("3", "e")
                .replace("$", "s")
                .replace("!", "i"))
    # Clean repeated characters (e.g., "fuuuuck" -> "fuck")
    text = re.sub(r"(.)\1{2,}", r"\1\1", text)
    # Remove special characters keeping basic tokens
    text = re.sub(r"[^a-z0-9\s]", " ", text)
    return " ".join(text.split())

def extract_entity_features(texts):
    """Extracts binary entity flags matching Java tokenizer logic."""
    rows = len(texts)
    has_person = np.zeros((rows, 1), dtype=np.float64)
    has_group = np.zeros((rows, 1), dtype=np.float64)

    for i, text in enumerate(texts):
        tokens = set(text.split())
        if tokens.intersection(PERSON_TOKENS):
            has_person[i, 0] = 1.0
        if tokens.intersection(GROUP_TOKENS):
            has_group[i, 0] = 1.0

    return csr_matrix(has_person), csr_matrix(has_group)

def main():
    print("Loading data...")
    df = pd.read_csv("train.csv")

    toxic_cols = ["toxic", "severe_toxic", "obscene", "threat", "insult", "identity_hate"]
    df["target"] = (df[toxic_cols].sum(axis=1) > 0).astype(int)

    print("Cleaning text...")
    df["comment_text"] = df["comment_text"].fillna("").apply(clean_text)

    # Stratified Train/Validation Split
    train_df, val_df = train_test_split(df, test_size=0.2, random_state=42, stratify=df["target"])
    print(f"Train samples: {len(train_df)} | Validation samples: {len(val_df)}")

    print("Extracting Word & Character TF-IDF features...")
    # Word level features (Unigrams & Bigrams)
    word_vec = TfidfVectorizer(
        ngram_range=(1, 2),
        max_features=18000,
        sublinear_tf=True,
        min_df=2
    )
    X_word_train = word_vec.fit_transform(train_df["comment_text"])
    X_word_val = word_vec.transform(val_df["comment_text"])

    # Char level features (3-5 n-grams for misspellings / obscured words)
    char_vec = TfidfVectorizer(
        ngram_range=(3, 5),
        analyzer="char_wb",
        max_features=22000,
        sublinear_tf=True,
        min_df=3
    )
    X_char_train = char_vec.fit_transform(train_df["comment_text"])
    X_char_val = char_vec.transform(val_df["comment_text"])

    # Extract Entity-Aware features
    X_person_train, X_group_train = extract_entity_features(train_df["comment_text"].values)
    X_person_val, X_group_val = extract_entity_features(val_df["comment_text"].values)

    X_train = hstack([X_word_train, X_char_train, X_person_train, X_group_train])
    X_val = hstack([X_word_val, X_char_val, X_person_val, X_group_val])

    print("Training Balanced Logistic Regression...")
    model = LogisticRegression(
        C=0.8,
        class_weight="balanced",
        max_iter=1000,
        solver="liblinear",
        random_state=42
    )
    model.fit(X_train, train_df["target"])

    # Calibrate optimal decision threshold on validation set
    val_probs = model.predict_proba(X_val)[:, 1]
    best_threshold = 0.5
    best_f1 = 0.0

    print("\n--- Calibrating Threshold ---")
    for t in np.arange(0.40, 0.85, 0.02):
        preds = (val_probs >= t).astype(int)
        score = f1_score(val_df["target"], preds)
        if score > best_f1:
            best_f1 = score
            best_threshold = round(float(t), 2)

    val_preds = (val_probs >= best_threshold).astype(int)
    print(f"\nCalibrated Optimal Threshold: {best_threshold}")
    print(f"Validation Accuracy : {accuracy_score(val_df['target'], val_preds)*100:.2f}%")
    print(f"Validation Precision: {precision_score(val_df['target'], val_preds)*100:.2f}%")
    print(f"Validation Recall   : {recall_score(val_df['target'], val_preds)*100:.2f}%")
    print(f"Validation F1-Score : {best_f1*100:.2f}%")

    print("\nMapping model weights...")
    feature_names = []
    feature_names.extend(word_vec.get_feature_names_out().tolist())

    # Matching Java tokenizer feature format
    for c in char_vec.get_feature_names_out():
        feature_names.append(f"char_{c}")

    feature_names.append("__ent_person__")
    feature_names.append("__ent_group__")

    # IDF vector: Word IDFs + Char IDFs + 1.0 for entity flags
    idfs = np.concatenate([word_vec.idf_, char_vec.idf_, np.array([1.0, 1.0])])
    coefs = model.coef_[0]

    weights_dict = {}
    for feat, coef, idf in zip(feature_names, coefs, idfs):
        # Keep every IDF in the normalization vocabulary, including near-zero coefficients.
        weights_dict[feat] = {"w": float(coef), "idf": float(idf)}

    export_data = {
        "feature_pipeline_version": 2,
        "intercept": float(model.intercept_[0]),
        "threshold": best_threshold,
        "weights": weights_dict
    }

    output_filename = "model_weights_v56.json"
    print(f"Saving model ({len(weights_dict)} features) to {output_filename}...")
    with open(output_filename, "w", encoding="utf-8") as f:
        json.dump(export_data, f, indent=2)

    # Preserve the fitted Python reference and exact records used for calibration.
    joblib.dump({"model": model, "word_vec": word_vec, "char_vec": char_vec,
                 "best_threshold": best_threshold}, "reference_pipeline.joblib")
    for filename, split in [("training_split_ids.csv", train_df), ("validation_split_ids.csv", val_df)]:
        split[["id", "target"]].to_csv(filename, index=False)
    with open("model_provenance.json", "w", encoding="utf-8") as f:
        json.dump({"train_csv_sha256": hashlib.sha256(open("train.csv", "rb").read()).hexdigest(),
                   "label_definition": "any of the six toxicity labels", "random_state": 42,
                   "threshold_selection": "validation only", "threshold": best_threshold,
                   "feature_pipeline_version": 2}, f, indent=2)

    # =========================================================================
    # JAVA PARITY VERIFICATION ON TEST.CSV
    # =========================================================================
    print("\n--- Running Python Verification on test.csv ---")
    try:
        test_df = pd.read_csv("test.csv")
        labels_df = pd.read_csv("test_labels.csv")

        merged = test_df.merge(labels_df, on="id")
        merged = merged[merged["toxic"] != -1]

        y_true = (merged[toxic_cols].sum(axis=1) > 0).astype(int)

        cleaned_test = merged["comment_text"].fillna("").apply(clean_text)
        X_test_w = word_vec.transform(cleaned_test)
        X_test_c = char_vec.transform(cleaned_test)
        X_test_p, X_test_g = extract_entity_features(cleaned_test.values)

        X_test = hstack([X_test_w, X_test_c, X_test_p, X_test_g])

        test_probs = model.predict_proba(X_test)[:, 1]
        test_preds = (test_probs >= best_threshold).astype(int)

        print(f"Kaggle test.csv Samples: {len(merged)}")
        print(classification_report(y_true, test_preds, digits=4))

    except Exception as e:
        print(f"Skipped test.csv verification: {e}")

    print("Done!")

if __name__ == "__main__":
    main()
