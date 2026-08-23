import json
import nltk
from nltk.corpus import wordnet as wn

def run_pipeline():
    print("Downloading WordNet resources...")
    nltk.download('wordnet', quiet=True)
    nltk.download('omw-1.4', quiet=True)

    print("Loading substitutions.json...")
    with open("substitutions.json", "r", encoding="utf-8") as f:
        raw_substitutions = json.load(f)

    # Filter out self-mappings (keep ONLY key != value)
    clean_toxic = {}
    for key, val in raw_substitutions.items():
        k_clean = str(key).strip().lower()
        v_clean = str(val).strip().lower()
        if k_clean != v_clean:
            clean_toxic[k_clean] = str(val).strip()

    with open("substitutions.json", "w", encoding="utf-8") as f:
        json.dump(clean_toxic, f, indent=2, ensure_ascii=False)
    print(f"✅ Cleaned 'substitutions.json': Saved {len(clean_toxic)} explicit toxic rules.")

    toxic_keys = set(clean_toxic.keys())

    print("Processing WordNet and purging toxic vocabulary...")
    rephrase_map = {}
    pos_tags = [wn.ADJ, wn.ADV, wn.NOUN, wn.VERB]

    for pos in pos_tags:
        for synset in wn.all_synsets(pos):
            lemmas = [l.name().lower().replace('_', ' ') for l in synset.lemmas() if l.name().isalpha()]
            unique_lemmas = list(dict.fromkeys(lemmas))

            if len(unique_lemmas) < 2:
                continue

            canonical_target = unique_lemmas[0]
            if canonical_target in toxic_keys:
                continue

            for candidate in unique_lemmas[1:]:
                if candidate in toxic_keys:
                    continue
                if len(candidate) >= 3 and candidate not in rephrase_map:
                    rephrase_map[candidate] = canonical_target

    with open("vocabulary.json", "w", encoding="utf-8") as f:
        json.dump(rephrase_map, f, indent=2, ensure_ascii=False)
    print(f"✅ Generated 'vocabulary.json': Saved {len(rephrase_map)} safe rephrase rules.")

if __name__ == "__main__":
    run_pipeline()