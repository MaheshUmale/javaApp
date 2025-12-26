package com.trading.hf;

import java.util.concurrent.ConcurrentHashMap;

public class PositionManager {

    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();

    public void addPosition(String instrumentKey, int quantity, String side, double entryPrice, long entryTimestamp) {
        positions.put(instrumentKey, new Position(instrumentKey, quantity, side, entryPrice, entryTimestamp));
    }

    public void removePosition(String instrumentKey) {
        positions.remove(instrumentKey);
    }

    public Position getPosition(String instrumentKey) {
        return positions.get(instrumentKey);
    }

    public ConcurrentHashMap<String, Position> getAllPositions() {
        return positions;
    }
}
