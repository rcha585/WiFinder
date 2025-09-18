# train_wifi_models.py
# -------------------------------------------
# Train floor (classifier), x and y (regressors) from your Google Sheet "dataset" CSV,
# then export Java code to replace FloorRandomForest.java, XRandomForest.java, YRandomForest.java.
#
# Usage (locally):
#   1) Export your "dataset" tab to CSV, save as dataset.csv next to this script.
#   2) Install deps: pip install pandas scikit-learn m2cgen numpy
#   3) python train_wifi_models.py
#
# Notes:
# - Feature order is exactly the CSV header order from the first BSSID column onward.
# - Missing values should already be -100 in your dataset build; if not, they will be filled to -100.
# - We do GroupKFold validation by location key=(floor,x,y) to reduce data leakage.
# -------------------------------------------

import os, sys, json
import numpy as np
import pandas as pd

from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.model_selection import GroupKFold, cross_val_score
from sklearn.metrics import mean_absolute_error, accuracy_score
import m2cgen as m2c

DATA_CSV = "dataset.csv"  # export your Google Sheet "dataset" tab to this file

if not os.path.exists(DATA_CSV):
    raise SystemExit(f"CSV not found: {DATA_CSV}. Export your 'dataset' tab and save as {DATA_CSV}.")

df = pd.read_csv(DATA_CSV)

# Expected columns: floor, x, y, then many BSSID columns
base_cols = ["floor", "x", "y"]
for c in base_cols:
    if c not in df.columns:
        raise SystemExit(f"Missing required column: {c}")

bssid_cols = [c for c in df.columns if c not in base_cols]
if not bssid_cols:
    raise SystemExit("No BSSID columns found. Check your dataset export.")

# Prepare data
X = df[bssid_cols].copy().fillna(-100).astype(float).values
y_floor = df["floor"].astype(int).values
y_x = df["x"].astype(float).values
y_y = df["y"].astype(float).values

# Groups: each unique location (floor,x,y) as a group id
groups = df[base_cols].astype(str).agg("|".join, axis=1)

# Models
clf_floor = RandomForestClassifier(n_estimators=200, max_depth=None, random_state=42, n_jobs=-1)
reg_x = RandomForestRegressor(n_estimators=300, max_depth=None, random_state=42, n_jobs=-1)
reg_y = RandomForestRegressor(n_estimators=300, max_depth=None, random_state=42, n_jobs=-1)

# Cross-validation (optional but recommended)
gkf = GroupKFold(n_splits=min(5, len(np.unique(groups))))
acc = cross_val_score(clf_floor, X, y_floor, cv=gkf, groups=groups, scoring="accuracy")
print(f"[CV] Floor accuracy: mean={acc.mean():.3f}, std={acc.std():.3f}")

# For regression, compute MAE manually per fold
maes_x, maes_y = [], []
for train_idx, test_idx in gkf.split(X, y_floor, groups):
    reg_x.fit(X[train_idx], y_x[train_idx])
    reg_y.fit(X[train_idx], y_y[train_idx])
    px = reg_x.predict(X[test_idx])
    py = reg_y.predict(X[test_idx])
    maes_x.append(mean_absolute_error(y_x[test_idx], px))
    maes_y.append(mean_absolute_error(y_y[test_idx], py))
print(f"[CV] X MAE: mean={np.mean(maes_x):.2f}, std={np.std(maes_x):.2f}")
print(f"[CV] Y MAE: mean={np.mean(maes_y):.2f}, std={np.std(maes_y):.2f}")

# Fit on ALL data
clf_floor.fit(X, y_floor)
reg_x.fit(X, y_x)
reg_y.fit(X, y_y)

# Persist the feature list (order matters!) to macAddresses.csv-like file
# (If you want to regenerate your whitelist automatically)
with open("bssid_whitelist_order.txt", "w", encoding="utf-8") as f:
    for c in bssid_cols:
        f.write(f"{c}\n")
print("Saved feature order to bssid_whitelist_order.txt")

# Export Java code using m2cgen
os.makedirs("export_java", exist_ok=True)

java_floor = m2c.export_to_java(clf_floor)
with open(os.path.join("export_java", "FloorRandomForest.java"), "w", encoding="utf-8") as f:
    f.write("package com.example.compsci399testproject.machinelearning.models;\n\n")
    f.write(java_floor.replace("class Model", "public class FloorRandomForest"))

java_x = m2c.export_to_java(reg_x)
with open(os.path.join("export_java", "XRandomForest.java"), "w", encoding="utf-8") as f:
    f.write("package com.example.compsci399testproject.machinelearning.models;\n\n")
    f.write(java_x.replace("class Model", "public class XRandomForest"))

java_y = m2c.export_to_java(reg_y)
with open(os.path.join("export_java", "YRandomForest.java"), "w", encoding="utf-8") as f:
    f.write("package com.example.compsci399testproject.machinelearning.models;\n\n")
    f.write(java_y.replace("class Model", "public class YRandomForest"))

print("Exported Java models to ./export_java")
print("Next steps:")
print(" 1) Replace the three files in app/src/main/java/.../machinelearning/models/ with the exported ones.")
print(" 2) Ensure BssidVectorizer uses the SAME feature order (see bssid_whitelist_order.txt).")
print(" 3) Rebuild and run the app.")
