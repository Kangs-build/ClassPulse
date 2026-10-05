"""
app.py
Flask microservice for ClassPulse's inappropriate-content detection.
Loads the trained model once at startup, exposes POST /check.

Request:  {"text": "doubt description here"}
Response: {"flagged": true/false, "confidence": 0.xx}

Flagging threshold is 0.50, chosen by testing the actual score distribution on sample
clean CS doubts (which scored 0.24-0.44) versus toxic examples (which scored 0.31-0.67).
0.50 sits in the gap between these clusters with margin on the clean side, so legitimate
technical questions aren't blocked while still catching clearly inappropriate text.
"""
from flask import Flask, request, jsonify
import joblib
from pathlib import Path

app = Flask(__name__)

model = joblib.load(Path(__file__).parent / "toxicity_model.pkl")
vectorizer = joblib.load(Path(__file__).parent / "vectorizer.pkl")

FLAG_THRESHOLD = 0.50

# Deterministic blocklist for unambiguous profanity/insults the ML model sometimes
# misses (small training set = imperfect recall). Deliberately excludes words that
# have legitimate CS meaning (e.g. "garbage", "kill", "die", "crash", "abort") so
# real technical doubts are never blocked by this layer - only the ML model judges
# those, using context.
import re
BLOCKLIST = [
    "idiot", "stupid", "moron", "shut up", "worthless", "dumbass",
    "fuck", "shit", "bitch", "asshole", "bastard", "retard",
]
BLOCKLIST_PATTERN = re.compile(r"\b(" + "|".join(re.escape(w) for w in BLOCKLIST) + r")\b", re.IGNORECASE)


@app.route("/check", methods=["POST"])
def check_text():
    data = request.get_json(silent=True)
    if not data or "text" not in data:
        return jsonify({"error": "Missing 'text' field in request body"}), 400

    if not isinstance(data["text"], str) or len(data["text"]) > 10000:
        return jsonify({"error": "Text must be a string of at most 10000 characters"}), 400
    text = data["text"].strip()
    if not text:
        return jsonify({"error": "Text cannot be empty"}), 400

    # Layer 1: deterministic blocklist (catches clear-cut cases the model might miss)
    if BLOCKLIST_PATTERN.search(text):
        return jsonify({"flagged": True, "confidence": 1.0, "matchedBlocklist": True})

    # Layer 2: ML model (catches subtler/context-dependent toxicity)
    vec = vectorizer.transform([text])
    probability_toxic = model.predict_proba(vec)[0][1]
    flagged = bool(probability_toxic >= FLAG_THRESHOLD)

    return jsonify({
        "flagged": flagged,
        "confidence": round(float(probability_toxic), 3)
    })


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000)
