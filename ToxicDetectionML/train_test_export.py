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
custom_stops = [
    'you', 'your', 'yours', 'me', 'my', 'mine', 
    'are', 'is', 'am', 'will', 'have', 'has', 'do', 'does',
    'the', 'a', 'an', 'this', 'that', 'it', 'to', 'was',
    'being', 'what', 'who', 'why', 'how']

vectorizer = TfidfVectorizer(
    max_features=30000, 
    ngram_range=(1, 3), 
    stop_words=custom_stops,
    min_df=5,
    sublinear_tf=True
)

X = vectorizer.fit_transform(df['comment_text'].apply(clean))
y = df['toxic']

# 4. TUNED LOGISTIC REGRESSION
# Using C=0.05 for stronger regularization
model = LogisticRegression(
    class_weight='balanced', 
    solver='liblinear', 
    C=0.05
)
model.fit(X, y)

# 5. EXPORTING ANALYZABLE WEIGHTS
feature_names = vectorizer.get_feature_names_out()
weights = model.coef_[0]

# Filter for relevant weights. Lowering threshold to 0.3 for better vocabulary.
weight_dict = {
    word: round(float(w), 4) 
    for word, w in zip(feature_names, weights) 
    if abs(w) > 0.3
}

# Neutralize "jumpy" words that are often dataset noise
features_to_kill = [
    'wikipedia', 'like', 'just', 'thanks', 'edit', 'page', 'article',
    'know', 'think', 'user', 'talk', 'actually', 'even', 'all', 'shut',
    'get', 'go', 'it', 'was', 'to'
]
for word in features_to_kill:
    if word in weight_dict:
        weight_dict[word] = 0.0

# THE MANUAL OVERRIDE (Phrase Blocklist)
manual_fixes = {
    "poop for brains": 5.5,
    "piece of crap": 4.8,
    "you are stupid": 6.0,
    "tangina": 4.5,
    "jajabol": 5.4
}

for phrase, weight in manual_fixes.items():
    weight_dict[phrase] = weight

# Sort Descending AFTER all fixes
sorted_weights = dict(sorted(weight_dict.items(), key=lambda item: item[1], reverse=True))

output = {
    "metadata": {
        "model_type": "Logistic Regression + Bigrams",
        "total_samples": len(df),
        "features_exported": len(sorted_weights),
        "regularization_C": 0.05,
        "weight_threshold": 0.3
    },
    "intercept": round(float(model.intercept_[0]), 4),
    "weights": sorted_weights
}

with open('optimized_weights_v9.json', 'w') as f:
    json.dump(output, f, indent=4)

print(f"Success! Model trained and weights exported to optimized_weights_v9.json.")
print(f"Total features: {len(sorted_weights)}")
