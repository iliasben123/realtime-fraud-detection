"""Tests du producer : conversion CSV -> événement, lecture en flux, générateur d'échantillon."""
from __future__ import annotations

import csv
import uuid
from pathlib import Path

import pytest

from producer import generate_sample_data as gen
from producer.transaction_producer import PAYSIM_COLUMNS, read_transactions, to_event

HEADER = "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
ROW = "1,TRANSFER,181.00,C1305486145,181.00,0.00,C553264065,0.00,0.00,1,0\n"


def test_to_event_normalises_types_and_names():
    row = dict(zip(HEADER.strip().split(","), ROW.strip().split(",")))
    event = to_event(row)

    assert uuid.UUID(event["transaction_id"])            # UUID valide
    assert event["event_time"].endswith("+00:00")        # ISO-8601 UTC
    assert event["step"] == 1
    assert event["type"] == "TRANSFER"
    assert event["amount"] == 181.0
    assert event["name_orig"] == "C1305486145"
    assert event["old_balance_orig"] == 181.0
    assert event["new_balance_orig"] == 0.0
    assert event["is_fraud"] is True
    assert event["is_flagged_fraud"] is False
    assert set(event) == {
        "transaction_id", "event_time", "step", "type", "amount", "name_orig", "old_balance_orig",
        "new_balance_orig", "name_dest", "old_balance_dest", "new_balance_dest", "is_fraud", "is_flagged_fraud",
    }


def test_read_transactions_streams_rows_and_honours_limit(tmp_path: Path):
    csv_path = tmp_path / "sample.csv"
    csv_path.write_text(HEADER + ROW * 5, encoding="utf-8")

    assert len(list(read_transactions(csv_path))) == 5
    assert len(list(read_transactions(csv_path, limit=2))) == 2
    ids = {e["transaction_id"] for e in read_transactions(csv_path)}
    assert len(ids) == 5                                  # un identifiant unique par événement


def test_read_transactions_rejects_missing_columns(tmp_path: Path):
    csv_path = tmp_path / "bad.csv"
    csv_path.write_text("step,type,amount\n1,PAYMENT,10\n", encoding="utf-8")

    with pytest.raises(ValueError, match="Colonnes manquantes"):
        list(read_transactions(csv_path))


def test_read_transactions_missing_file():
    with pytest.raises(FileNotFoundError):
        list(read_transactions(Path("/nonexistent/paysim.csv")))


def test_generated_sample_matches_paysim_format():
    import random
    rng = random.Random(1)
    hot = ["C1", "C2"]
    rows = [gen.generate_row(step=1, rng=rng, hot_accounts=hot) for _ in range(2000)]

    assert all(len(r) == len(gen.HEADER) for r in rows)
    assert set(gen.HEADER) == PAYSIM_COLUMNS
    types = {r[1] for r in rows}
    assert types <= {"PAYMENT", "TRANSFER", "CASH_OUT", "CASH_IN", "DEBIT"}
    # les comptes chauds concentrent une partie du trafic
    hot_share = sum(r[3] in hot for r in rows) / len(rows)
    assert 0.005 < hot_share < 0.05
    # une fraude vide intégralement le compte
    frauds = [r for r in rows if r[9] == 1]
    assert all(r[1] in ("TRANSFER", "CASH_OUT") and r[5] == "0.00" for r in frauds)

    # chaque ligne est convertible en événement
    for r in rows[:50]:
        to_event(dict(zip(gen.HEADER, map(str, r))))


def test_generated_csv_is_readable_by_producer(tmp_path: Path, monkeypatch):
    out = tmp_path / "paysim.csv"
    monkeypatch.setattr("sys.argv", ["gen", "--rows", "300", "--out", str(out), "--seed", "7"])
    gen.main()

    with out.open(encoding="utf-8") as f:
        assert csv.DictReader(f).fieldnames == gen.HEADER
    assert len(list(read_transactions(out))) == 300
