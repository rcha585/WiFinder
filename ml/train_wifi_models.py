#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Train Wi-Fi floor (classifier) and X/Y (regressors) from dataset.csv, then export Java models.

Usage:
  cd ml
  python train_wifi_models.py

Requirements:
  pip install pandas numpy scikit-learn m2cgen
"""

import os, json, sys
from pathlib import Path
from typing import List, Tuple

import numpy as np
import pandas as pd

from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.metrics import (
    accuracy_score, confusion_matrix, mean_absolute_error, classification_report
)
from sklearn.model_selection import GroupKFold, cross_val_score

# ---------------------------- Config ----------------------------

# dataset 文件位置（优先 ml/dataset.csv，其次当前目录）
DATA_CSV_CANDIDATES = [Path("ml/dataset.csv"), Path("dataset.csv")]
JAVA_OUT_DIR = Path("export_java")         # Java 模型导出目录
PKG = "com.example.compsci399testproject.machinelearning.models"
RANDOM_STATE = 42

# RF 超参（保守稳健，适合 m2cgen 导出）
CLF_FLOOR = dict(n_estimators=200, max_depth=None, n_jobs=-1, random_state=RANDOM_STATE)
REG_XY = dict(n_estimators=300, max_depth=None, n_jobs=-1, random_state=RANDOM_STATE)

# RSSI 预处理（-100 表示缺失）
RSSI_FILL = -100.0
RSSI_MIN, RSSI_MAX = -100.0, -30.0

# ------------------------- Helpers -----------------------------

def find_dataset(path_candidates: List[Path]) -> Path:
    for p in path_candidates:
        if p.exists():
            return p
    raise SystemExit(f"[ERR] dataset.csv not found. Tried: {', '.join(map(str, path_candidates))}")

def load_dataset(csv_path: Path) -> pd.DataFrame:
    df = pd.read_csv(csv_path)
    required = ["floor", "x", "y"]
    for c in required:
        if c not in df.columns:
            raise SystemExit(f"[ERR] Missing column '{c}' in {csv_path}")
    return df

def extract_features(df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, List[str]]:
    base_cols = ["floor", "x", "y"]
    feat_cols = [c for c in df.columns if c not in base_cols]
    if not feat_cols:
        raise SystemExit("[ERR] No BSSID columns found. Make sure dataset has columns after floor/x/y.")
    X = (df[feat_cols]
         .astype(float)
         .fillna(RSSI_FILL)
         .clip(RSSI_MIN, RSSI_MAX)
         .values)
    y_floor = df["floor"].astype(int).values
    y_x = df["x"].astype(float).values
    y_y = df["y"].astype(float).values
    groups = df[base_cols].astype(str).agg("|".join, axis=1).values
    return X, y_floor, y_x, y_y, feat_cols, groups

def kfold_safe_splits(groups, min_splits=5):
    uniq = np.unique(groups)
    n_splits = min(min_splits, len(uniq))
    return max(n_splits, 2)  # 至少 2 折

# ------------------------- Training ----------------------------

def train_and_eval(X, y_floor, y_x, y_y, groups):
    # 全局标签：确保每折的混淆矩阵维度一致
    labels_all = np.unique(y_floor)
    n_labels = len(labels_all)

    gkf = GroupKFold(n_splits=max(2, min(5, len(np.unique(groups)))))

    # Floor CV accuracy
    clf_proto = RandomForestClassifier(**CLF_FLOOR)
    accs = cross_val_score(clf_proto, X, y_floor, cv=gkf, groups=groups, scoring="accuracy")

    # Regression CV MAE（手动按折）
    maes_x, maes_y = [], []
    cms = None  # 累加混淆矩阵（固定 labels_all）

    for tr, te in gkf.split(X, y_floor, groups):
        # Floor
        clf = RandomForestClassifier(**CLF_FLOOR).fit(X[tr], y_floor[tr])
        pred_f = clf.predict(X[te])

        if n_labels >= 2:
            cm = confusion_matrix(y_floor[te], pred_f, labels=labels_all)
            cms = cm if cms is None else (cms + cm)

        # X/Y
        rx = RandomForestRegressor(**REG_XY).fit(X[tr], y_x[tr])
        ry = RandomForestRegressor(**REG_XY).fit(X[tr], y_y[tr])
        px = rx.predict(X[te]); py = ry.predict(X[te])
        maes_x.append(mean_absolute_error(y_x[te], px))
        maes_y.append(mean_absolute_error(y_y[te], py))

    metrics = {
        "cv_folds": int(max(2, min(5, len(np.unique(groups))))),
        "floor_labels": labels_all.tolist(),
        "floor_accuracy_mean": float(np.mean(accs)),
        "floor_accuracy_std": float(np.std(accs)),
        "x_mae_mean": float(np.mean(maes_x)),
        "x_mae_std": float(np.std(maes_x)),
        "y_mae_mean": float(np.mean(maes_y)),
        "y_mae_std": float(np.std(maes_y)),
        "confusion_matrix": (cms.tolist() if cms is not None else None),
    }

    # Fit on ALL data
    clf_all = RandomForestClassifier(**CLF_FLOOR).fit(X, y_floor)
    rx_all  = RandomForestRegressor(**REG_XY).fit(X, y_x)
    ry_all  = RandomForestRegressor(**REG_XY).fit(X, y_y)

    return clf_all, rx_all, ry_all, metrics

# ------------------------- Export ------------------------------

def export_models_java(clf, rx, ry, feat_cols: List[str], out_dir: Path, pkg: str):
    import m2cgen as m2c
    out_dir.mkdir(parents=True, exist_ok=True)

    def write_java(java_src: str, cls_name: str):
        src = "package " + pkg + ";\n\n" + java_src.replace("class Model", f"public class {cls_name}")
        (out_dir / f"{cls_name}.java").write_text(src, encoding="utf-8")
        print(f"[OK] Exported {cls_name}.java")

    write_java(m2c.export_to_java(clf), "FloorRandomForest")
    write_java(m2c.export_to_java(rx),  "XRandomForest")
    write_java(m2c.export_to_java(ry),  "YRandomForest")

    # 保存特征顺序（App 侧 BssidVectorizer 必须严格一致）
    with open(out_dir / "bssid_whitelist_order.txt", "w", encoding="utf-8") as f:
        for c in feat_cols:
            f.write(f"{c}\n")
    print(f"[OK] Saved feature order -> {out_dir/'bssid_whitelist_order.txt'}")

# ------------------------- Main -------------------------------

def main():
    csv_path = find_dataset(DATA_CSV_CANDIDATES)
    print(f"[INFO] Using dataset: {csv_path}")

    df = load_dataset(csv_path)
    print(f"[INFO] Rows={len(df)}, Columns={len(df.columns)}")

    X, yf, yx, yy, feat_cols, groups = extract_features(df)
    print(f"[INFO] Feature dim={X.shape[1]} (BSSID columns)")

    clf_all, rx_all, ry_all, metrics = train_and_eval(X, yf, yx, yy, groups)

    # 导出
    export_models_java(clf_all, rx_all, ry_all, feat_cols, JAVA_OUT_DIR, PKG)

    # 保存指标
    (JAVA_OUT_DIR / "metrics.json").write_text(
        json.dumps(metrics, indent=2), encoding="utf-8"
    )
    print("[METRICS]")
    for k, v in metrics.items():
        if k != "confusion_matrix":
            print(f"  {k}: {v:.3f}" if isinstance(v, float) else f"  {k}: {v}")
    if metrics.get("confusion_matrix") is not None:
        print("  confusion_matrix: saved to metrics.json")

    print("\nNext steps:")
    print(f"  1) Copy {JAVA_OUT_DIR}/FloorRandomForest.java, XRandomForest.java, YRandomForest.java")
    print("     into app/src/main/java/com/example/compsci399testproject/machinelearning/models/")
    print("  2) Ensure BssidVectorizer uses EXACTLY the same feature order as bssid_whitelist_order.txt")
    print("  3) Rebuild & run. In app, keep Smoothing/Hysteresis OFF for collection; ON for improved A/B.")

if __name__ == "__main__":
    main()
