package com.dbvein.analysis;

public record CanvasNodeRequest(
        Long tableId,
        double positionX,
        double positionY,
        Double width,
        Double height,
        boolean collapsed
) {}
