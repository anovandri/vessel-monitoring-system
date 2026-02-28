package com.kreasipositif.vms.collector.aisstream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a geographic bounding box for filtering AIS data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoundingBox {
    private String name;
    private double minLon;
    private double minLat;
    private double maxLon;
    private double maxLat;

    /**
     * Create from coordinate array [[minLon, minLat], [maxLon, maxLat]]
     */
    public static BoundingBox fromCoordinates(String name, double[][] coordinates) {
        if (coordinates == null || coordinates.length != 2) {
            throw new IllegalArgumentException("Coordinates must be array of 2 points");
        }
        return new BoundingBox(
                name,
                coordinates[0][0], // minLon
                coordinates[0][1], // minLat
                coordinates[1][0], // maxLon
                coordinates[1][1]  // maxLat
        );
    }

    @Override
    public String toString() {
        return String.format("%s [%.2f,%.2f] to [%.2f,%.2f]", 
                name, minLon, minLat, maxLon, maxLat);
    }
}
