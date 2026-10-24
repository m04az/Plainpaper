"""Document segmentation model (research R2): MobileNetV2-style encoder, U-Net-style decoder.

Input  : 1x3x256x256 float RGB in 0..1.
Outputs: ``mask`` 1x1x128x128 logits (whole document, including occluded parts) and
         ``presence`` 1x1 logit (a document is in view).
Only Conv/BatchNorm/ReLU6/Add/Concat/Resize/GlobalAveragePool/Gemm, so OpenCV's dnn imports it.
"""

from __future__ import annotations

import torch
from torch import nn
from torch.nn import functional as F

INPUT_SIZE = 256
MASK_SIZE = 128


def conv_bn(cin: int, cout: int, k: int = 3, s: int = 1, groups: int = 1, act: bool = True) -> nn.Sequential:
    layers: list[nn.Module] = [
        nn.Conv2d(cin, cout, k, s, k // 2, groups=groups, bias=False),
        nn.BatchNorm2d(cout),
    ]
    if act:
        layers.append(nn.ReLU6(inplace=True))
    return nn.Sequential(*layers)


class InvertedResidual(nn.Module):
    def __init__(self, cin: int, cout: int, stride: int, expand: int) -> None:
        super().__init__()
        hidden = cin * expand
        layers: list[nn.Module] = []
        if expand != 1:
            layers.append(conv_bn(cin, hidden, k=1))
        layers += [conv_bn(hidden, hidden, k=3, s=stride, groups=hidden), conv_bn(hidden, cout, k=1, act=False)]
        self.block = nn.Sequential(*layers)
        self.residual = stride == 1 and cin == cout

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        y = self.block(x)
        return x + y if self.residual else y


def stage(cin: int, cout: int, stride: int, repeats: int, expand: int) -> nn.Sequential:
    blocks = [InvertedResidual(cin, cout, stride, expand)]
    blocks += [InvertedResidual(cout, cout, 1, expand) for _ in range(repeats - 1)]
    return nn.Sequential(*blocks)


class Up(nn.Module):
    """x2 bilinear upsample, concatenate the skip, two 3x3 convs (the second depthwise-separable)."""

    def __init__(self, cin: int, cskip: int, cout: int) -> None:
        super().__init__()
        self.fuse = conv_bn(cin + cskip, cout, k=1)
        self.refine = nn.Sequential(conv_bn(cout, cout, k=3, groups=cout), conv_bn(cout, cout, k=1))

    def forward(self, x: torch.Tensor, skip: torch.Tensor) -> torch.Tensor:
        x = F.interpolate(x, scale_factor=2.0, mode="bilinear", align_corners=False)
        return self.refine(self.fuse(torch.cat([x, skip], dim=1)))


class DocumentNet(nn.Module):
    def __init__(self) -> None:
        super().__init__()
        self.stem = conv_bn(3, 16, s=2)  # 128
        self.s1 = stage(16, 16, 1, 1, 1)  # 128
        self.s2 = stage(16, 24, 2, 2, 4)  # 64
        self.s3 = stage(24, 32, 2, 3, 4)  # 32
        self.s4 = stage(32, 64, 2, 3, 4)  # 16
        self.s5 = stage(64, 96, 1, 2, 4)  # 16
        self.s6 = stage(96, 128, 2, 2, 4)  # 8 (context for page-filling documents)
        self.up16 = Up(128, 96, 64)
        self.up32 = Up(64, 32, 48)
        self.up64 = Up(48, 24, 32)
        self.up128 = Up(32, 16, 24)
        self.mask_head = nn.Conv2d(24, 1, 1)
        self.presence_head = nn.Linear(128, 1)

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        f128 = self.s1(self.stem(x))
        f64 = self.s2(f128)
        f32 = self.s3(f64)
        f16 = self.s5(self.s4(f32))
        f8 = self.s6(f16)
        y = self.up16(f8, f16)
        y = self.up32(y, f32)
        y = self.up64(y, f64)
        y = self.up128(y, f128)
        mask = self.mask_head(y)
        presence = self.presence_head(torch.flatten(F.adaptive_avg_pool2d(f8, 1), 1))
        return mask, presence


def parameter_count(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters())
