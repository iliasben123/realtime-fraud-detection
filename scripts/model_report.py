"""
Génère un rapport Markdown à partir des métadonnées d'un modèle du registre
(models/fraud_gbt/<version>/metadata.json) : métriques, comparaison avec les règles,
courbe précision / rappel par seuil et importance des features.

Usage :
  python scripts/model_report.py                 # version pointée par models/fraud_gbt/latest
  python scripts/model_report.py v20261017_032522
  python scripts/model_report.py --dir models/fraud_gbt --out docs/model_report.md
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_metadata(model_dir: Path, version: str | None) -> tuple[str, dict]:
    if version is None:
        version = (model_dir / "latest").read_text(encoding="utf-8").strip()
    meta = json.loads((model_dir / version / "metadata.json").read_text(encoding="utf-8"))
    return version, meta


def pct(x: float) -> str:
    return f"{100 * x:.1f} %"


def num(n: int) -> str:
    """Séparateur de milliers à la française (espace)."""
    return f"{n:,}".replace(",", " ")


def render(version: str, m: dict) -> str:
    metrics, best, rules = m["metrics"], m["metrics"]["at_threshold"], m["metrics"]["rules_baseline"]
    rows = m["rows"]
    lines = [
        f"### Modèle `{version}`",
        "",
        f"- Source : `{m['source']}`, {num(rows['train'] + rows['test'])} lignes "
        f"({num(rows['fraud_total'])} fraudes), jeu de test {m['params']['test_fraction']:.0%} "
        f"soit {num(rows['test'])} lignes",
        f"- Algorithme : {m['algorithm']} (maxIter={m['params']['maxIter']}, maxDepth={m['params']['maxDepth']}), "
        f"entraîné le {m['trained_at'][:10]}",
        f"- AUC-ROC **{metrics['auc_roc']:.4f}**, AUC-PR **{metrics['auc_pr']:.4f}**",
        "",
        "| Détecteur | Précision | Rappel | F1 | Vrais positifs | Faux positifs | Fraudes manquées |",
        "|---|---|---|---|---|---|---|",
        f"| Règles métier seules | {pct(rules['precision'])} | {pct(rules['recall'])} | {rules['f1']:.3f} "
        f"| {num(rules['tp'])} | {num(rules['fp'])} | {num(rules['fn'])} |",
        f"| Modèle GBT (seuil {best['threshold']:.2f}) | {pct(best['precision'])} | {pct(best['recall'])} | {best['f1']:.3f} "
        f"| {num(best['tp'])} | {num(best['fp'])} | {num(best['fn'])} |",
        "",
        "Compromis précision / rappel selon le seuil de probabilité (jeu de test) :",
        "",
        "| Seuil | Précision | Rappel | F1 | Faux positifs |",
        "|---|---|---|---|---|",
    ]
    for p in m["pr_curve"]:
        if p["threshold"] in (0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9):
            mark = " **←**" if p["threshold"] == best["threshold"] else ""
            lines.append(f"| {p['threshold']:.2f}{mark} | {pct(p['precision'])} | {pct(p['recall'])} "
                         f"| {p['f1']:.3f} | {p['fp']} |")
    lines += ["", "Importance des features (gain total, normalisé) :", "", "| Feature | Importance |", "|---|---|"]
    for name, imp in list(m["feature_importance"].items())[:10]:
        bar = "█" * int(round(imp * 40))
        lines.append(f"| `{name}` | {imp:.3f} {bar} |")
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Rapport Markdown d'un modèle du registre")
    p.add_argument("version", nargs="?", default=None)
    p.add_argument("--dir", default=str(ROOT / "models" / "fraud_gbt"))
    p.add_argument("--out", default=None, help="fichier de sortie (défaut : stdout)")
    args = p.parse_args(argv)

    version, meta = load_metadata(Path(args.dir), args.version)
    report = render(version, meta)
    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
        print(f"Rapport écrit dans {args.out}")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        print(report)
    return 0


if __name__ == "__main__":
    sys.exit(main())
