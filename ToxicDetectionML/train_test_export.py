import pandas as pd
import re
import json
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

# 1. LOAD AND MERGE DATA
train = pd.read_csv('train.csv')
test = pd.read_csv('test.csv')
test_labels = pd.read_csv('test_labels.csv')

merged_test = pd.merge(test, test_labels, on='id')
clean_test = merged_test[merged_test['toxic'] != -1]
df = pd.concat([train, clean_test], axis=0).reset_index(drop=True)

# 2. ENHANCED CLEANING & NORMALIZATION
def clean(text):
    text = str(text).lower()
    # Remove HTML/CSS noise and Wikipedia timestamps often found in this dataset
    text = re.sub(r'\d+px|style=.*|cellpadding=.*|#\w+|color\s\w+', '', text)
    text = re.sub(r'\d{2}:\d{2}, \d+ \w+ \d{4} \(utc\)', '', text)
    # Normalize slang: "haaaaate" -> "hate"
    text = re.sub(r'(.)\1{2,}', r'\1', text) 
    text = re.sub(r'[^a-z\s]', '', text)
    return text.strip()

# 3. VECTORIZATION WITH CONTEXT (N-GRAMS)
# Including bigrams allows the model to differentiate "not toxic" from "toxic".
# min_df=5 filters out rare typos that can act as "noise" features.
vectorizer = TfidfVectorizer(
    max_features=10000, # Increased slightly to accommodate bigrams
    stop_words='english', 
    ngram_range=(1, 2), 
    min_df=5 
)

X = vectorizer.fit_transform(df['comment_text'].apply(clean))
y = df['toxic']

# 4. TUNED LOGISTIC REGRESSION
# Using C=0.5 (stronger regularization) to lower the weights of neutral words.
# 'balanced' class_weight helps if your dataset has fewer toxic than benign samples.
model = LogisticRegression(
    class_weight='balanced', 
    solver='liblinear', 
    penalty='l2', 
    C=0.5 
)
model.fit(X, y)

# 5. EXPORTING ANALYZABLE WEIGHTS
feature_names = vectorizer.get_feature_names_out()
weights = model.coef_[0]

# Filter for relevant weights to keep the JSON manageable[cite: 8]
weight_dict = {
    word: round(float(w), 4) 
    for word, w in zip(feature_names, weights) 
    if abs(w) > 0.5 # Increased threshold to find the most influential features
}

# Sort Descending: Highest positive weights (Toxic) at the top
sorted_weights = dict(sorted(weight_dict.items(), key=lambda item: item[1], reverse=True))

output = {
    "metadata": {
        "model_type": "Logistic Regression + Bigrams",
        "total_samples": len(df),
        "features_exported": len(sorted_weights),
        "regularization_C": 0.5
    },
    "intercept": round(float(model.intercept_[0]), 4),
    "weights": sorted_weights
}

with open('optimized_weights_v2.json', 'w') as f:
    json.dump(output, f, indent=4)

print(f"Success! Model trained and weights exported to optimized_weights_v2.json.")