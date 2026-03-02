#!/bin/bash

# Test WebSocket by publishing messages to Redis Pub/Sub

echo "Starting WebSocket test message publisher..."
echo "Press Ctrl+C to stop"
echo ""

counter=1

while true; do
    # Publish vessel position
    MMSI=$((123456000 + RANDOM % 100))
    LAT=$(awk -v min=1.0 -v max=2.0 'BEGIN{srand(); print min+rand()*(max-min)}')
    LON=$(awk -v min=103.0 -v max=104.0 'BEGIN{srand(); print min+rand()*(max-min)}')
    SPEED=$(awk -v min=5.0 -v max=15.0 'BEGIN{srand(); print min+rand()*(max-min)}')
    TIMESTAMP=$(date +%s)000
    
    docker exec vms-redis redis-cli PUBLISH vessel-positions-stream "{
        \"mmsi\":${MMSI},
        \"vesselName\":\"TEST SHIP ${counter}\",
        \"vesselType\":\"Cargo\",
        \"latitude\":${LAT},
        \"longitude\":${LON},
        \"speed\":${SPEED},
        \"course\":180.0,
        \"heading\":185.0,
        \"navigationalStatus\":\"Under way using engine\",
        \"timestamp\":${TIMESTAMP},
        \"country\":\"Singapore\",
        \"destination\":\"SINGAPORE\"
    }" > /dev/null 2>&1
    
    echo "[$(date '+%H:%M:%S')] Published vessel position #${counter} (MMSI: ${MMSI})"
    
    # Publish weather data every 5 messages
    if [ $((counter % 5)) -eq 0 ]; then
        TEMP=$(awk -v min=25.0 -v max=32.0 'BEGIN{srand(); print min+rand()*(max-min)}')
        WIND=$(awk -v min=2.0 -v max=8.0 'BEGIN{srand(); print min+rand()*(max-min)}')
        
        docker exec vms-redis redis-cli PUBLISH weather-data-stream "{
            \"gridId\":\"grid_1_103\",
            \"latitude\":1.0,
            \"longitude\":103.0,
            \"temperature\":${TEMP},
            \"windSpeed\":${WIND},
            \"windDirection\":180.0,
            \"waveHeight\":1.5,
            \"visibility\":10.0,
            \"pressure\":1013.25,
            \"humidity\":75.0,
            \"timestamp\":${TIMESTAMP}
        }" > /dev/null 2>&1
        
        echo "[$(date '+%H:%M:%S')] Published weather data"
    fi
    
    # Publish alert every 10 messages
    if [ $((counter % 10)) -eq 0 ]; then
        docker exec vms-redis redis-cli PUBLISH vessel-alerts-stream "{
            \"alertId\":\"ALT-${counter}\",
            \"mmsi\":${MMSI},
            \"vesselName\":\"TEST SHIP ${counter}\",
            \"alertType\":\"SPEED_ANOMALY\",
            \"severity\":\"HIGH\",
            \"description\":\"Vessel speed exceeds normal range\",
            \"latitude\":${LAT},
            \"longitude\":${LON},
            \"timestamp\":${TIMESTAMP}
        }" > /dev/null 2>&1
        
        echo "[$(date '+%H:%M:%S')] Published vessel alert"
    fi
    
    # Publish port operation every 15 messages
    if [ $((counter % 15)) -eq 0 ]; then
        docker exec vms-redis redis-cli PUBLISH port-data-stream "{
            \"operationId\":\"PORT123-OP${counter}\",
            \"portId\":\"PORT123\",
            \"portName\":\"Singapore Port\",
            \"mmsi\":${MMSI},
            \"vesselName\":\"TEST SHIP ${counter}\",
            \"operationType\":\"Docking\",
            \"status\":\"In Progress\",
            \"arrivalTime\":${TIMESTAMP},
            \"berthNumber\":\"A12\",
            \"timestamp\":${TIMESTAMP}
        }" > /dev/null 2>&1
        
        echo "[$(date '+%H:%M:%S')] Published port operation"
    fi
    
    counter=$((counter + 1))
    sleep 2
done
