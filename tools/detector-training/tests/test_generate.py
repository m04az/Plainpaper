import numpy as np

from detector.generate import NEGATIVE_RATE, sample_for
from detector.model import INPUT_SIZE, MASK_SIZE


def test_deterministic():
    a, b = sample_for(7, 3), sample_for(7, 3)
    assert np.array_equal(a.image, b.image)
    assert np.array_equal(a.mask, b.mask)
    assert a.presence == b.presence


def test_shapes_and_ranges():
    for i in range(20):
        s = sample_for(1, i)
        assert s.image.shape == (INPUT_SIZE, INPUT_SIZE, 3) and s.image.dtype == np.uint8
        assert s.mask.shape == (MASK_SIZE, MASK_SIZE)
        assert set(np.unique(s.mask)).issubset({0.0, 1.0})
        if s.presence == 0:
            assert s.mask.sum() == 0 and s.quad is None
        else:
            assert 0.01 <= s.area <= 0.99
            assert s.mask.mean() > 0


def test_mask_covers_occluded_document():
    # The label is the document quad, independent of hands and cables drawn over it.
    for i in range(40):
        s = sample_for(2, i)
        if s.quad is None:
            continue
        inside = s.mask.mean()
        assert abs(inside - s.area) < 0.05


def test_negative_rate_roughly_matches():
    negatives = sum(sample_for(3, i).presence == 0 for i in range(200))
    assert NEGATIVE_RATE * 200 * 0.4 < negatives < NEGATIVE_RATE * 200 * 2.0
