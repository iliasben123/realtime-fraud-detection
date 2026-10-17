"""
Génère un CSV synthétique au format PaySim pour tester le pipeline sans
télécharger le dataset Kaggle (~470 Mo).

Usage (depuis le dossier ingestion/) :
  python -m producer.generate_sample_data                 # 50 000 lignes -> data/raw/paysim.csv
  python -m producer.generate_sample_data --rows 5000 --out ../data/raw/sample.csv
"""
from __future__ import annotations

import argparse
import csv
import random
from pathlib import Path

from .config import PROJECT_ROOT

HEADER = ["step", "type", "amount", "nameOrig", "oldbalanceOrg", "newbalanceOrig",
          "nameDest", "oldbalanceDest", "newbalanceDest", "isFraud", "isFlaggedFraud"]

# Répartition proche du vrai dataset PaySim
TYPE_WEIGHTS = {"CASH_OUT": 0.35, "PAYMENT": 0.34, "CASH_IN": 0.22, "TRANSFER": 0.08, "DEBIT": 0.01}
FRAUD_RATE = 0.0013       # ~0.13 % de fraudes dans PaySim
HOT_ACCOUNTS = 5          # comptes "chauds" très actifs -> déclenchent les règles de vélocité (fenêtres glissantes)
HOT_ACCOUNT_SHARE = 0.02  # part des transactions émises par ces comptes


def account(prefix: str, rng: random.Random) -> str:
    return f"{prefix}{rng.randint(10_000_000, 1_999_999_999)}"


def generate_row(step: int, rng: random.Random, hot_accounts: list[str] | None = None) -> list:
    is_fraud = rng.random() < FRAUD_RATE
    if is_fraud:
        # Pattern de fraude PaySim : TRANSFER ou CASH_OUT qui vide intégralement le compte
        tx_type = rng.choice(["TRANSFER", "CASH_OUT"])
        old_orig = round(rng.uniform(10_000, 10_000_000), 2)
        amount = old_orig
        new_orig = 0.0
    else:
        tx_type = rng.choices(list(TYPE_WEIGHTS), weights=list(TYPE_WEIGHTS.values()))[0]
        amount = round(rng.lognormvariate(10, 1.5), 2)   # distribution asymétrique réaliste
        old_orig = round(rng.uniform(0, 500_000), 2)
        if tx_type == "CASH_IN":
            new_orig = round(old_orig + amount, 2)
        else:
            new_orig = round(max(old_orig - amount, 0), 2)

    # Destinataire : marchand (M...) pour PAYMENT, client (C...) sinon
    name_dest = account("M", rng) if tx_type == "PAYMENT" else account("C", rng)
    if tx_type == "PAYMENT":
        old_dest = new_dest = 0.0               # PaySim ne trace pas les soldes marchands
    else:
        old_dest = round(rng.uniform(0, 2_000_000), 2)
        new_dest = round(old_dest + amount, 2)

    is_flagged = int(tx_type == "TRANSFER" and amount > 200_000)

    # Compte émetteur : quelques comptes très actifs concentrent une partie du trafic
    if hot_accounts and rng.random() < HOT_ACCOUNT_SHARE:
        name_orig = rng.choice(hot_accounts)
    else:
        name_orig = account("C", rng)

    return [step, tx_type, f"{amount:.2f}", name_orig, f"{old_orig:.2f}", f"{new_orig:.2f}",
            name_dest, f"{old_dest:.2f}", f"{new_dest:.2f}", int(is_fraud), is_flagged]


def main() -> None:
    p = argparse.ArgumentParser(description="Génère un CSV synthétique au format PaySim")
    p.add_argument("--rows", type=int, default=50_000)
    p.add_argument("--out", type=Path, default=PROJECT_ROOT / "data/raw/paysim.csv")
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()

    rng = random.Random(args.seed)
    hot_accounts = [account("C", rng) for _ in range(HOT_ACCOUNTS)]
    args.out.parent.mkdir(parents=True, exist_ok=True)
    rows_per_step = max(args.rows // 743, 1)    # PaySim = 743 steps (30 jours en heures)

    with args.out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(HEADER)
        for i in range(args.rows):
            w.writerow(generate_row(step=i // rows_per_step + 1, rng=rng, hot_accounts=hot_accounts))

    print(f"{args.rows} lignes écrites dans {args.out} (~{int(args.rows * FRAUD_RATE)} fraudes attendues)")
    print(f"Comptes chauds ({HOT_ACCOUNT_SHARE:.0%} du trafic) : {', '.join(hot_accounts)}")


if __name__ == "__main__":
    main()
