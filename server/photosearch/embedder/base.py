"""Embedder abstraction — ตรวจจับใบหน้าทั้งหมดในรูปและสร้างเวกเตอร์

ใช้โมเดล+preprocessing เดียวกันทั้งรูปกิจกรรมและเซลฟี (บังคับผ่าน interface เดียว)
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

import numpy as np


@dataclass
class Face:
    # เวกเตอร์ที่ normalize แล้ว (L2=1) เพื่อให้ inner-product = cosine similarity
    embedding: np.ndarray
    bbox: list[float]  # [x1,y1,x2,y2] บนภาพวิเคราะห์
    det_score: float


class Embedder(Protocol):
    dim: int
    model_id: str
    model_version: str
    preprocessing_version: str

    def detect_and_embed(self, image_bytes: bytes) -> list[Face]:
        """คืนทุกใบหน้าที่พบในรูป (ภาพกลุ่มมีได้หลายใบหน้า)"""
        ...


def l2_normalize(v: np.ndarray) -> np.ndarray:
    n = np.linalg.norm(v)
    if n == 0:
        return v
    return v / n
