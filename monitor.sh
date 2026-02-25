#!/bin/bash
# VMS Data Collector Service - Monitoring Script

echo "======================================================"
echo "  VMS Data Collector Service - Real-time Monitor"
echo "======================================================"
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Service URL
SERVICE_URL="http://localhost:8080"

# Function to check if service is running
check_service() {
    if curl -s "${SERVICE_URL}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Service is RUNNING${NC}"
        return 0
    else
        echo -e "${RED}✗ Service is DOWN${NC}"
        return 1
    fi
}

# Function to display health
show_health() {
    echo -e "\n${BLUE}━━━ Health Status ━━━${NC}"
    health=$(curl -s "${SERVICE_URL}/actuator/health" | jq -r '.status // "UNKNOWN"')
    if [ "$health" == "UP" ]; then
        echo -e "Status: ${GREEN}${health}${NC}"
    else
        echo -e "Status: ${RED}${health}${NC}"
    fi
}

# Function to display metrics
show_metrics() {
    echo -e "\n${BLUE}━━━ Key Metrics ━━━${NC}"
    
    # JVM Memory
    echo -e "\n${YELLOW}JVM Memory:${NC}"
    jvm_used=$(curl -s "${SERVICE_URL}/actuator/metrics/jvm.memory.used" | jq -r '.measurements[0].value')
    jvm_max=$(curl -s "${SERVICE_URL}/actuator/metrics/jvm.memory.max" | jq -r '.measurements[0].value')
    if [ "$jvm_used" != "null" ] && [ "$jvm_max" != "null" ]; then
        jvm_used_mb=$(echo "scale=2; $jvm_used / 1024 / 1024" | bc)
        jvm_max_mb=$(echo "scale=2; $jvm_max / 1024 / 1024" | bc)
        echo "  Used: ${jvm_used_mb} MB / Max: ${jvm_max_mb} MB"
    fi
    
    # System CPU
    echo -e "\n${YELLOW}System:${NC}"
    cpu=$(curl -s "${SERVICE_URL}/actuator/metrics/system.cpu.usage" | jq -r '.measurements[0].value')
    if [ "$cpu" != "null" ]; then
        cpu_percent=$(echo "scale=2; $cpu * 100" | bc)
        echo "  CPU Usage: ${cpu_percent}%"
    fi
    
    # Process CPU
    process_cpu=$(curl -s "${SERVICE_URL}/actuator/metrics/process.cpu.usage" | jq -r '.measurements[0].value')
    if [ "$process_cpu" != "null" ]; then
        process_cpu_percent=$(echo "scale=2; $process_cpu * 100" | bc)
        echo "  Process CPU: ${process_cpu_percent}%"
    fi
    
    # Uptime
    uptime_seconds=$(curl -s "${SERVICE_URL}/actuator/metrics/process.uptime" | jq -r '.measurements[0].value')
    if [ "$uptime_seconds" != "null" ]; then
        uptime_minutes=$(echo "scale=2; $uptime_seconds / 60" | bc)
        echo "  Uptime: ${uptime_minutes} minutes"
    fi
}

# Function to display custom collector metrics
show_collector_metrics() {
    echo -e "\n${BLUE}━━━ Data Collector Metrics ━━━${NC}"
    
    # Get all collector metrics
    metrics=$(curl -s "${SERVICE_URL}/actuator/metrics" | jq -r '.names[]' | grep "collector\.")
    
    if [ -z "$metrics" ]; then
        echo "  No collector metrics available yet"
    else
        echo "$metrics" | while read -r metric; do
            value=$(curl -s "${SERVICE_URL}/actuator/metrics/${metric}" | jq -r '.measurements[0].value // "N/A"')
            echo "  ${metric}: ${value}"
        done
    fi
}

# Function to display HTTP metrics
show_http_metrics() {
    echo -e "\n${BLUE}━━━ HTTP Server Metrics ━━━${NC}"
    
    # HTTP requests
    http_count=$(curl -s "${SERVICE_URL}/actuator/metrics/http.server.requests" | jq -r '.measurements[] | select(.statistic=="COUNT") | .value // "0"')
    http_max=$(curl -s "${SERVICE_URL}/actuator/metrics/http.server.requests" | jq -r '.measurements[] | select(.statistic=="MAX") | .value // "0"')
    
    if [ "$http_count" != "null" ] && [ "$http_count" != "0" ]; then
        echo "  Total Requests: ${http_count}"
        echo "  Max Response Time: ${http_max}s"
    else
        echo "  No HTTP requests recorded yet"
    fi
}

# Function to watch logs
watch_logs() {
    echo -e "\n${BLUE}━━━ Live Application Logs ━━━${NC}"
    echo "Press Ctrl+C to stop..."
    echo ""
    
    # This will show the terminal output where the service is running
    echo "Check the terminal where you ran: ./nx serve data-collector-service"
}

# Main monitoring loop
main() {
    if check_service; then
        while true; do
            clear
            echo "======================================================"
            echo "  VMS Data Collector Service - Real-time Monitor"
            echo "  $(date '+%Y-%m-%d %H:%M:%S')"
            echo "======================================================"
            
            show_health
            show_metrics
            show_collector_metrics
            show_http_metrics
            
            echo ""
            echo -e "${YELLOW}Press Ctrl+C to exit${NC}"
            echo "Refreshing in 5 seconds..."
            
            sleep 5
        done
    else
        echo ""
        echo "Please start the service first:"
        echo "  ./nx serve data-collector-service"
    fi
}

# Parse arguments
case "${1:-}" in
    "logs")
        watch_logs
        ;;
    "health")
        check_service
        show_health
        ;;
    *)
        main
        ;;
esac
