"""
Registre de modèles minimaliste sur système de fichiers :

  <model_dir>/
    latest                      -> nom de la version courante (fichier texte)
    <version>/model/            -> PipelineModel MLlib sérialisé
    <version>/metadata.json     -> métriques, seuil, features, horodatage

Le job streaming charge la version pointée par `latest` au démarrage.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from pyspark.ml import PipelineModel

log = logging.getLogger("ml-registry")

LATEST_FILE = "latest"
METADATA_FILE = "metadata.json"
MODEL_SUBDIR = "model"


@dataclass(frozen=True)
class ModelInfo:
    version: str
    model_path: str
    metadata: dict

    @property
    def threshold(self) -> float:
        return float(self.metadata.get("threshold", 0.5))


def new_version() -> str:
    return datetime.now(timezone.utc).strftime("v%Y%m%d_%H%M%S")


def version_dir(model_dir: str | Path, version: str) -> Path:
    return Path(model_dir) / version


def register(model: PipelineModel, model_dir: str | Path, version: str, metadata: dict) -> ModelInfo:
    """Sauvegarde le modèle et ses métadonnées puis fait pointer `latest` dessus."""
    vdir = version_dir(model_dir, version)
    vdir.mkdir(parents=True, exist_ok=True)
    model_path = vdir / MODEL_SUBDIR
    model.write().overwrite().save(str(model_path))
    (vdir / METADATA_FILE).write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    (Path(model_dir) / LATEST_FILE).write_text(version + "\n", encoding="utf-8")
    log.info("Modèle %s enregistré dans %s", version, vdir)
    return ModelInfo(version=version, model_path=str(model_path), metadata=metadata)


def resolve(model_dir: str | Path, version: str | None = None) -> ModelInfo | None:
    """Retourne la version demandée (ou `latest`), None si aucun modèle n'est disponible."""
    root = Path(model_dir)
    if version is None:
        latest = root / LATEST_FILE
        if not latest.exists():
            return None
        version = latest.read_text(encoding="utf-8").strip()
    vdir = root / version
    meta_file = vdir / METADATA_FILE
    if not (vdir / MODEL_SUBDIR).exists() or not meta_file.exists():
        log.warning("Version %s incomplète dans %s", version, root)
        return None
    metadata = json.loads(meta_file.read_text(encoding="utf-8"))
    return ModelInfo(version=version, model_path=str(vdir / MODEL_SUBDIR), metadata=metadata)


def load(info: ModelInfo) -> PipelineModel:
    return PipelineModel.load(info.model_path)
